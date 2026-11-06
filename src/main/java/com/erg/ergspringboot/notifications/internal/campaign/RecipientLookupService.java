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
import java.util.stream.Stream;
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

    private record Person(String userId, String name, String role, String meta,
                          String code, String email, String phone, boolean student) {
        RecipientCard card() {
            return new RecipientCard(userId, name, role, meta);
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
        Scope scope = scopeOf(tenantId, actorId, actorRoles);
        String text = query == null ? "" : query.trim();
        if (text.isEmpty()) {
            return List.of();
        }
        int max = Math.max(1, Math.min(limit, MAX_SEARCH_LIMIT));
        String needle = fold(text);
        Map<String, Person> found = new LinkedHashMap<>();
        keyedStudents(tenantId, scope, List.of(text)).forEach(p -> found.putIfAbsent(p.userId(), p));
        students.listStudents(tenantId, scope.classIds(), DIRECTORY_CAP).stream().map(RecipientLookupService::person)
                .filter(p -> fold(p.name()).contains(needle)).forEach(p -> found.putIfAbsent(p.userId(), p));
        if (scope.staffVisible()) {
            staffDirectory(tenantId).stream()
                    .filter(p -> fold(p.name()).contains(needle) || text.equalsIgnoreCase(p.email()))
                    .forEach(p -> found.putIfAbsent(p.userId(), p));
        }
        List<Person> sorted = found.values().stream()
                .sorted(Comparator.comparing((Person p) -> fold(p.name())).thenComparing(Person::userId)).toList();
        List<RecipientCard> cards = new ArrayList<>();
        for (int from = 0; from < sorted.size() && cards.size() < max; from += max) {
            withActiveLogin(tenantId, sorted.subList(from, Math.min(sorted.size(), from + max))).stream()
                    .limit(max - cards.size()).map(Person::card).forEach(cards::add);
        }
        return cards;
    }

    public ResolvePeopleResult resolveLines(String tenantId, String actorId, List<String> actorRoles, List<String> lines) {
        if (lines == null || lines.size() > MAX_LINES) {
            throw new BadRequestException("Tối đa 5.000 dòng mỗi lần");
        }
        Scope scope = scopeOf(tenantId, actorId, actorRoles);
        List<String> clean = lines.stream().filter(l -> l != null && !l.isBlank()).map(String::trim).toList();

        Map<String, List<Person>> byLine = new HashMap<>();
        List<Person> keyed = keyedStudents(tenantId, scope, clean);
        if (scope.staffVisible() && clean.stream().anyMatch(l -> l.indexOf('@') >= 0)) {
            keyed = Stream.concat(keyed.stream(), staffDirectory(tenantId).stream()).toList();
        }
        for (String line : clean) {
            byLine.put(line, matching(keyed, line));
        }
        List<String> unmatched = clean.stream().distinct().filter(l -> byLine.get(l).isEmpty()).toList();
        if (!unmatched.isEmpty()) {
            matchByName(tenantId, scope, unmatched, byLine);
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

    private Scope scopeOf(String tenantId, String actorId, List<String> actorRoles) {
        if (guard.isAdmin(actorRoles)) {
            return new Scope(null, true);
        }
        if (!guard.isTeacher(actorRoles)) {
            throw new AccessDeniedException("Bạn không có quyền tìm người nhận");
        }
        return new Scope(List.copyOf(lms.classIdsTaughtBy(tenantId, actorId)), false);
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
            if (phone.matches("0\\d{8,10}")) {
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

    private void matchByName(String tenantId, Scope scope, List<String> unmatched, Map<String, List<Person>> byLine) {
        Map<String, List<Person>> index = new HashMap<>();
        students.listStudents(tenantId, scope.classIds(), DIRECTORY_CAP).stream().map(RecipientLookupService::person)
                .forEach(p -> index.computeIfAbsent(fold(p.name()), k -> new ArrayList<>()).add(p));
        Set<String> staffIds = new LinkedHashSet<>();
        if (scope.staffVisible()) {
            staffDirectory(tenantId).forEach(p -> {
                index.computeIfAbsent(fold(p.name()), k -> new ArrayList<>()).add(p);
                staffIds.add(p.userId());
            });
        }
        Map<String, Person> candidates = new LinkedHashMap<>();
        unmatched.forEach(l -> index.getOrDefault(fold(l), List.of()).forEach(p -> candidates.put(p.userId(), p)));
        Set<String> active = new LinkedHashSet<>(staffIds);
        withActiveLogin(tenantId, candidates.values().stream().filter(p -> !staffIds.contains(p.userId())).toList())
                .forEach(p -> active.add(p.userId()));
        unmatched.forEach(l -> byLine.put(l, index.getOrDefault(fold(l), List.of()).stream()
                .filter(p -> active.contains(p.userId())).toList()));
    }

    private static List<Person> matching(List<Person> keyed, String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        String phone = normalisePhone(line);
        Map<String, Person> hit = new LinkedHashMap<>();
        for (Person p : keyed) {
            boolean match = line.indexOf('@') >= 0
                    ? lower.equals(lower(p.email()))
                    : (p.student() && line.equalsIgnoreCase(p.code()))
                        || (p.phone() != null && phone.equals(normalisePhone(p.phone())));
            if (match) {
                hit.putIfAbsent(p.userId(), p);
            }
        }
        return List.copyOf(hit.values());
    }

    /** Teachers and staff are visible to admins only; both are already filtered to active accounts. */
    private List<Person> staffDirectory(String tenantId) {
        Map<String, Person> byId = new LinkedHashMap<>();
        users.findCalendarTeachersByTenant(tenantId, STAFF_PAGE).stream().filter(RecipientLookupService::isActive)
                .forEach(u -> byId.putIfAbsent(u.id(), person(u, "TEACHER", "Giáo viên")));
        List<String> staffIds = users.findActiveStaffUserIds(tenantId, STAFF_PAGE);
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
                s.studentCode(), s.email(), s.phone(), true);
    }

    private static Person person(UserAccountProjection u, String role, String label) {
        return new Person(u.id(), u.displayName(), role, join(label, u.email()), null, u.email(), null, false);
    }

    private static String join(String... parts) {
        return Stream.of(parts).filter(p -> p != null && !p.isBlank()).collect(java.util.stream.Collectors.joining(SEP));
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    /** Strips spaces, dots, dashes and brackets; a +84 prefix becomes the national leading 0. */
    static String normalisePhone(String raw) {
        String digits = raw.replaceAll("[\\s.\\-()]", "");
        return digits.startsWith("+84") ? "0" + digits.substring(3) : digits;
    }

    /** Case, accent and extra-space insensitive form of a name (đ/Đ become d). */
    static String fold(String raw) {
        if (raw == null) {
            return "";
        }
        String plain = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .replace('đ', 'd').replace('Đ', 'D');
        return plain.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
