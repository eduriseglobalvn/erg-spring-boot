package com.erg.ergspringboot.notifications.internal.campaign;

import com.erg.ergspringboot.notifications.RecipientLookupResponses.AmbiguousLine;
import com.erg.ergspringboot.notifications.RecipientLookupResponses.RecipientCard;
import com.erg.ergspringboot.notifications.RecipientLookupResponses.ResolvePeopleResult;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.lms.LmsAudienceReadPort;
import com.erg.ergspringboot.lms.LmsStudentLookupPort;
import com.erg.ergspringboot.lms.LmsStudentLookupPort.StudentCard;
import com.erg.ergspringboot.users.UserAccountProjection;
import com.erg.ergspringboot.users.UserDirectoryPort;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Finds people to address: a type-ahead search and the resolution of a pasted list (one code, email, phone or
 * name per line). Admins see students, teachers and staff; teachers only the students of the classes they teach.
 * Students must still be on the roll with an active login, staff and teachers must be active.
 */
@Service
public class RecipientLookupService {

    static final int MAX_LINES = 5_000;
    static final int MAX_SEARCH_LIMIT = 20;
    // shortcut: names are matched in memory (no unaccent in the DB); lists past this many students are cut, move to an unaccent column then.
    static final int DIRECTORY_CAP = 20_000;
    private static final int ACTIVE_CHUNK = 1_000;
    private static final Pageable STAFF_PAGE = PageRequest.ofSize(MAX_LINES + 1);
    private static final String SEP = " · ";
    private static final Logger log = LoggerFactory.getLogger(RecipientLookupService.class);
    private static final Pattern PHONE_STRIP = Pattern.compile("[\\s.\\-()]");
    private static final Pattern PHONE_SHAPE = Pattern.compile("0\\d{8,10}");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private record Person(String userId, String name, String role, String meta,
                          String code, String email, String phone, boolean student, String folded) {
        RecipientCard card() {
            return new RecipientCard(userId, name, role, meta);
        }
    }

    /** Per-request lookup tables so every pasted line costs O(1). Keys are never empty. */
    private record Index(Map<String, List<Person>> byCode, Map<String, List<Person>> byEmail,
                         Map<String, List<Person>> byPhone) {
        static Index of(List<Person> people) {
            Index index = new Index(new HashMap<>(), new HashMap<>(), new HashMap<>());
            for (Person p : people) {
                if (p.student() && p.code() != null && !p.code().isBlank()) {
                    index.byCode.computeIfAbsent(p.code().trim().toUpperCase(Locale.ROOT), k -> new ArrayList<>()).add(p);
                }
                if (p.email() != null && !p.email().isBlank()) {
                    index.byEmail.computeIfAbsent(p.email().trim().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(p);
                }
                if (p.phone() != null) {
                    String phone = normalisePhone(p.phone());
                    if (PHONE_SHAPE.matcher(phone).matches()) {
                        index.byPhone.computeIfAbsent(phone, k -> new ArrayList<>()).add(p);
                    }
                }
            }
            return index;
        }

        List<Person> find(String line) {
            Map<String, Person> hit = new LinkedHashMap<>();
            if (line.indexOf('@') >= 0) {
                byEmail.getOrDefault(line.toLowerCase(Locale.ROOT), List.of()).forEach(p -> hit.putIfAbsent(p.userId(), p));
            } else {
                byCode.getOrDefault(line.toUpperCase(Locale.ROOT), List.of()).forEach(p -> hit.putIfAbsent(p.userId(), p));
                String phone = normalisePhone(line);
                if (PHONE_SHAPE.matcher(phone).matches()) {
                    byPhone.getOrDefault(phone, List.of()).forEach(p -> hit.putIfAbsent(p.userId(), p));
                }
            }
            return List.copyOf(hit.values());
        }
    }

    /** Computes a value at most once per request. */
    private static final class Once<T> implements Supplier<T> {
        private final Supplier<T> source;
        private T value;
        private boolean done;

        Once(Supplier<T> source) {
            this.source = source;
        }

        @Override
        public T get() {
            if (!done) {
                value = source.get();
                done = true;
            }
            return value;
        }
    }

    private record Scope(Collection<String> classIds, boolean staffVisible) {}

    private final LmsStudentLookupPort students;
    private final LmsAudienceReadPort lms;
    private final UserDirectoryPort users;
    private final AudienceScopeGuard guard;

    public RecipientLookupService(
            LmsStudentLookupPort students, LmsAudienceReadPort lms, UserDirectoryPort users, AudienceScopeGuard guard) {
        this.students = students;
        this.lms = lms;
        this.users = users;
        this.guard = guard;
    }

    public List<RecipientCard> search(String tenantId, String actorId, List<String> actorRoles, String query, int limit) {
        return search(new CampaignActor(tenantId, actorId, actorRoles), query, limit);
    }

    public List<RecipientCard> search(CampaignActor actor, String query, int limit) {
        String tenantId = actor.tenantId();
        Scope scope = scopeOf(actor);
        String text = query == null ? "" : query.trim();
        if (text.isEmpty()) {
            return List.of();
        }
        int max = Math.max(1, Math.min(limit, MAX_SEARCH_LIMIT));
        String needle = fold(text);
        if (needle.isEmpty()) {
            return List.of();
        }
        Map<String, Person> found = new LinkedHashMap<>();
        keyedStudents(tenantId, scope, List.of(text)).forEach(p -> found.putIfAbsent(p.userId(), p));
        directory(tenantId, scope).stream()
                .filter(p -> p.folded().contains(needle)).forEach(p -> found.putIfAbsent(p.userId(), p));
        if (scope.staffVisible()) {
            staffDirectory(tenantId).stream()
                    .filter(p -> p.folded().contains(needle) || text.equalsIgnoreCase(p.email()))
                    .forEach(p -> found.putIfAbsent(p.userId(), p));
        }
        List<Person> sorted = found.values().stream()
                .sorted(Comparator.comparing(Person::folded).thenComparing(Person::userId)).toList();
        List<RecipientCard> cards = new ArrayList<>();
        for (int from = 0; from < sorted.size() && cards.size() < max; from += max) {
            withActiveLogin(tenantId, sorted.subList(from, Math.min(sorted.size(), from + max))).stream()
                    .limit(max - cards.size()).map(Person::card).forEach(cards::add);
        }
        return cards;
    }

    public ResolvePeopleResult resolveLines(String tenantId, String actorId, List<String> actorRoles, List<String> lines) {
        return resolveLines(new CampaignActor(tenantId, actorId, actorRoles), lines);
    }

    public ResolvePeopleResult resolveLines(CampaignActor actor, List<String> lines) {
        String tenantId = actor.tenantId();
        if (lines == null || lines.size() > MAX_LINES) {
            throw new BadRequestException("Tối đa 5.000 dòng mỗi lần");
        }
        Scope scope = scopeOf(actor);
        List<String> clean = lines.stream().filter(l -> l != null && !l.isBlank()).map(String::trim).toList();

        Supplier<List<Person>> staff = new Once<>(() -> staffDirectory(tenantId));
        Map<String, List<Person>> byLine = new HashMap<>();
        List<Person> keyed = keyedStudents(tenantId, scope, clean);
        if (scope.staffVisible() && clean.stream().anyMatch(l -> l.indexOf('@') >= 0)) {
            keyed = Stream.concat(keyed.stream(), staff.get().stream()).toList();
        }
        Index index = Index.of(keyed);
        for (String line : clean) {
            byLine.computeIfAbsent(line, index::find);
        }
        List<String> unmatched = clean.stream().distinct().filter(l -> byLine.get(l).isEmpty()).toList();
        if (!unmatched.isEmpty()) {
            matchByName(tenantId, scope, unmatched, byLine, staff);
        }

        Map<String, RecipientCard> matched = new LinkedHashMap<>();
        List<String> notFound = new ArrayList<>();
        List<AmbiguousLine> ambiguous = new ArrayList<>();
        for (String line : clean) {
            List<Person> candidates = byLine.get(line);
            if (candidates.isEmpty()) {
                notFound.add(line);
            } else if (candidates.size() > 1) {
                ambiguous.add(new AmbiguousLine(line, candidates.size()));
            } else {
                matched.putIfAbsent(candidates.get(0).userId(), candidates.get(0).card());
            }
        }
        return new ResolvePeopleResult(List.copyOf(matched.values()), notFound, ambiguous);
    }

    private Scope scopeOf(CampaignActor actor) {
        if (guard.isAdmin(actor)) {
            return new Scope(null, true);
        }
        if (!guard.isTeacher(actor)) {
            throw new AccessDeniedException("Bạn không có quyền tìm người nhận");
        }
        return new Scope(List.copyOf(lms.classIdsTaughtBy(actor.tenantId(), actor.userId())), false);
    }

    /** One batched query for every code, email and phone among {@code texts}; students not yet login-checked. */
    private List<Person> keyedStudents(String tenantId, Scope scope, List<String> texts) {
        Set<String> codes = new LinkedHashSet<>();
        Set<String> emails = new LinkedHashSet<>();
        Set<String> phones = new LinkedHashSet<>();
        for (String text : texts) {
            if (text.indexOf('@') >= 0) {
                emails.add(text.toLowerCase(Locale.ROOT));
                continue;
            }
            if (text.chars().noneMatch(Character::isWhitespace)) {
                codes.add(text.toUpperCase(Locale.ROOT));
            }
            String phone = normalisePhone(text);
            if (PHONE_SHAPE.matcher(phone).matches()) {
                phones.add(phone);
                phones.add("+84" + phone.substring(1));
            }
        }
        if (scope.classIds() != null && scope.classIds().isEmpty()) {
            return List.of();
        }
        return withActiveLogin(tenantId, students.findByKeys(tenantId, scope.classIds(), codes, emails, phones).stream()
                .map(RecipientLookupService::person).toList());
    }

    private void matchByName(String tenantId, Scope scope, List<String> unmatched,
            Map<String, List<Person>> byLine, Supplier<List<Person>> staff) {
        Map<String, List<Person>> index = new HashMap<>();
        directory(tenantId, scope).stream().filter(p -> !p.folded().isEmpty())
                .forEach(p -> index.computeIfAbsent(p.folded(), k -> new ArrayList<>()).add(p));
        Set<String> staffIds = new LinkedHashSet<>();
        if (scope.staffVisible()) {
            staff.get().stream().filter(p -> !p.folded().isEmpty()).forEach(p -> {
                index.computeIfAbsent(p.folded(), k -> new ArrayList<>()).add(p);
                staffIds.add(p.userId());
            });
        }
        Map<String, List<Person>> byName = new HashMap<>();
        Map<String, Person> candidates = new LinkedHashMap<>();
        for (String line : unmatched) {
            List<Person> found = index.getOrDefault(fold(line), List.of());
            byName.put(line, found);
            found.forEach(p -> candidates.put(p.userId(), p));
        }
        Set<String> active = new LinkedHashSet<>(staffIds);
        withActiveLogin(tenantId, candidates.values().stream().filter(p -> !staffIds.contains(p.userId())).toList())
                .forEach(p -> active.add(p.userId()));
        byName.forEach((line, found) ->
                byLine.put(line, found.stream().filter(p -> active.contains(p.userId())).toList()));
    }

    private List<Person> directory(String tenantId, Scope scope) {
        List<StudentCard> cards = students.listStudents(tenantId, scope.classIds(), DIRECTORY_CAP);
        if (cards.size() >= DIRECTORY_CAP) {
            log.warn("Student directory hit its cap of {} for tenant {}; name search is truncated", DIRECTORY_CAP, tenantId);
        }
        return cards.stream().map(RecipientLookupService::person).toList();
    }

    /** Teachers and staff are visible to admins only; both are already filtered to active accounts. */
    private List<Person> staffDirectory(String tenantId) {
        Map<String, Person> byId = new LinkedHashMap<>();
        users.findCalendarTeachersByTenant(tenantId, STAFF_PAGE).stream().filter(RecipientLookupService::isActive)
                .forEach(u -> byId.putIfAbsent(u.id(), person(u, "TEACHER", "Giáo viên")));
        if (byId.size() >= STAFF_PAGE.getPageSize()) {
            log.warn("Teacher directory hit its cap of {} for tenant {}", STAFF_PAGE.getPageSize(), tenantId);
        }
        List<String> staffIds = users.findActiveStaffUserIds(tenantId, STAFF_PAGE);
        if (staffIds.size() >= STAFF_PAGE.getPageSize()) {
            log.warn("Staff directory hit its cap of {} for tenant {}", STAFF_PAGE.getPageSize(), tenantId);
        }
        if (!staffIds.isEmpty()) {
            users.findByTenantIdAndIds(tenantId, staffIds).stream().filter(RecipientLookupService::isActive)
                    .forEach(u -> byId.putIfAbsent(u.id(), person(u, "STAFF", "Nhân viên")));
        }
        return List.copyOf(byId.values());
    }

    /** Drops students whose login is missing or not active; staff/teacher persons pass through untouched. */
    private List<Person> withActiveLogin(String tenantId, List<Person> people) {
        List<Person> studentsOnly = people.stream().filter(Person::student).toList();
        Set<String> active = new LinkedHashSet<>();
        for (int from = 0; from < studentsOnly.size(); from += ACTIVE_CHUNK) {
            List<String> ids = studentsOnly.subList(from, Math.min(studentsOnly.size(), from + ACTIVE_CHUNK)).stream()
                    .map(Person::userId).toList();
            users.findByTenantIdAndIds(tenantId, ids).stream().filter(RecipientLookupService::isActive)
                    .forEach(u -> active.add(u.id()));
        }
        return people.stream().filter(p -> !p.student() || active.contains(p.userId())).toList();
    }

    private static boolean isActive(UserAccountProjection user) {
        return user.status() == null || "ACTIVE".equalsIgnoreCase(user.status());
    }

    private static Person person(StudentCard s) {
        return new Person(s.userId(), s.fullName(), "STUDENT", join("Học sinh", s.className(), s.studentCode()),
                s.studentCode(), s.email(), s.phone(), true, fold(s.fullName()));
    }

    private static Person person(UserAccountProjection u, String role, String label) {
        return new Person(u.id(), u.displayName(), role, join(label, u.email()), null, u.email(), null, false, fold(u.displayName()));
    }

    private static String join(String... parts) {
        return Stream.of(parts).filter(p -> p != null && !p.isBlank()).collect(Collectors.joining(SEP));
    }

    /** Strips spaces, dots, dashes and brackets; a +84 prefix becomes the national leading 0. */
    static String normalisePhone(String raw) {
        String digits = PHONE_STRIP.matcher(raw).replaceAll("");
        return digits.startsWith("+84") ? "0" + digits.substring(3) : digits;
    }

    /** Case, accent and extra-space insensitive form of a name (đ/Đ become d). */
    static String fold(String raw) {
        if (raw == null) {
            return "";
        }
        String plain = MARKS.matcher(Normalizer.normalize(raw, Normalizer.Form.NFD)).replaceAll("")
                .replace('đ', 'd').replace('Đ', 'D');
        return SPACES.matcher(plain.toLowerCase(Locale.ROOT).trim()).replaceAll(" ");
    }
}
