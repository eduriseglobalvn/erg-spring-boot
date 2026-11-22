package com.erg.ergspringboot.calendar.internal.readmodel;

import com.erg.ergspringboot.calendar.CalendarCatalogSearchResponse;
import com.erg.ergspringboot.calendar.CalendarCatalogSearchResponse.Item;
import com.erg.ergspringboot.calendar.persistence.CalendarRoom;
import com.erg.ergspringboot.curriculum.persistence.CurriculumSubject;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.users.UserAccountProjection;
import com.erg.ergspringboot.users.UserDirectoryPort;
import jakarta.persistence.EntityManager;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Paged, text-filtered lookup of classes, teachers, rooms and subjects, for clients whose catalog was cut at 500 entries.
 * Callers pass school ids that the scope service already checked; every query filters by tenant.
 */
@Service
public class CalendarCatalogSearchUseCase {

    static final int MAX_PAGE_SIZE = 100;
    // shortcut: teachers have no SQL text search on the user port, so up to this many are read and filtered in memory
    private static final int TEACHER_SCAN_LIMIT = 5_000;

    private final EntityManager entityManager;
    private final UserDirectoryPort userDirectory;

    public CalendarCatalogSearchUseCase(EntityManager entityManager, UserDirectoryPort userDirectory) {
        this.entityManager = entityManager;
        this.userDirectory = userDirectory;
    }

    @Transactional(value = "postgresTransactionManager", readOnly = true)
    public CalendarCatalogSearchResponse search(
            String tenantId, String kind, List<String> schoolIds, String q, Integer page, Integer size) {
        String normalizedKind = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
        int pageIndex = page == null ? 0 : page;
        int pageSize = size == null ? 20 : size;
        if (pageIndex < 0 || pageIndex > 10_000 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BadRequestException("page must be between 0 and 10000 and size between 1 and " + MAX_PAGE_SIZE);
        }
        String text = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<String> schools = schoolIds == null ? List.of() : schoolIds;
        List<Item> found = switch (normalizedKind) {
            case "classes" -> schools.isEmpty() ? List.of() : classes(tenantId, schools, text, pageIndex, pageSize);
            case "rooms" -> schools.isEmpty() ? List.of() : rooms(tenantId, schools, text, pageIndex, pageSize);
            case "subjects" -> subjects(tenantId, text, pageIndex, pageSize);
            case "teachers" -> schools.isEmpty() ? List.of() : teachers(tenantId, schools, text, pageIndex, pageSize);
            default -> throw new BadRequestException("kind must be one of: classes, teachers, rooms, subjects");
        };
        boolean hasMore = found.size() > pageSize;
        return new CalendarCatalogSearchResponse(normalizedKind, pageIndex, pageSize, hasMore,
                hasMore ? found.subList(0, pageSize) : found);
    }

    private List<Item> classes(String tenantId, List<String> schools, String text, int page, int size) {
        return window(entityManager.createQuery("select c from Classroom c where c.tenantId = :tenantId"
                        + " and c.schoolId in :schoolIds and lower(c.name) like :q escape '\\'"
                        + " order by c.name asc, c.id asc", Classroom.class)
                .setParameter("tenantId", tenantId).setParameter("schoolIds", schools).setParameter("q", like(text)), page, size)
                .stream().map(c -> new Item(c.getId(), c.getName(), c.getSchoolId(), c.getGrade(), c.getAcademicYear())).toList();
    }

    private List<Item> rooms(String tenantId, List<String> schools, String text, int page, int size) {
        return window(entityManager.createQuery("select r from CalendarRoom r where r.tenantId = :tenantId"
                        + " and r.schoolId in :schoolIds and r.status = 'ACTIVE' and lower(r.name) like :q escape '\\'"
                        + " order by r.name asc, r.id asc", CalendarRoom.class)
                .setParameter("tenantId", tenantId).setParameter("schoolIds", schools).setParameter("q", like(text)), page, size)
                .stream().map(r -> new Item(r.getId(), r.getName(), r.getSchoolId(), null, null)).toList();
    }

    private List<Item> subjects(String tenantId, String text, int page, int size) {
        return window(entityManager.createQuery("select s from CurriculumSubject s where s.tenantId = :tenantId"
                        + " and upper(s.status) = 'ACTIVE' and lower(s.name) like :q escape '\\'"
                        + " order by s.sortOrder asc, s.name asc, s.id asc", CurriculumSubject.class)
                .setParameter("tenantId", tenantId).setParameter("q", like(text)), page, size)
                .stream().map(s -> new Item(s.getId(), s.getName(), null, null, null)).toList();
    }

    private List<Item> teachers(String tenantId, List<String> schools, String text, int page, int size) {
        List<UserAccountProjection> all = userDirectory.findCalendarTeachersByTenantAndSchoolIds(
                tenantId, schools, PageRequest.of(0, TEACHER_SCAN_LIMIT));
        return all.stream()
                .filter(u -> u != null && u.id() != null && u.displayName() != null
                        && u.displayName().toLowerCase(Locale.ROOT).contains(text))
                .sorted(Comparator.comparing(UserAccountProjection::displayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(UserAccountProjection::id))
                .skip((long) page * size).limit(size + 1L)
                .map(u -> new Item(u.id(), u.displayName(), null, null, null)).toList();
    }

    /** One extra row past the page tells the caller whether another page exists. */
    private static <T> List<T> window(jakarta.persistence.TypedQuery<T> query, int page, int size) {
        return query.setFirstResult(page * size).setMaxResults(size + 1).getResultList();
    }

    private static String like(String text) {
        return "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
