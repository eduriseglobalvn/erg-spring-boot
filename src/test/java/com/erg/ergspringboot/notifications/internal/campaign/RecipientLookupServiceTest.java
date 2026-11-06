package com.erg.ergspringboot.notifications.internal.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.notifications.RecipientLookupResponses.RecipientCard;
import com.erg.ergspringboot.notifications.RecipientLookupResponses.ResolvePeopleResult;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.lms.LmsAudienceReadPort;
import com.erg.ergspringboot.lms.LmsStudentLookupPort;
import com.erg.ergspringboot.lms.LmsStudentLookupPort.StudentCard;
import com.erg.ergspringboot.users.UserAccountProjection;
import com.erg.ergspringboot.users.UserDirectoryPort;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

class RecipientLookupServiceTest {

    private static final String TENANT = "t1";
    private static final List<String> ADMIN = List.of("admin");
    private static final List<String> TEACHER = List.of("teacher");

    /** Students of class 7A / 7B. Phone is stored with spaces on purpose; its key is the stripped form. */
    private final List<StudentCard> roll = List.of(
            new StudentCard("u-a1", "Nguyễn Văn A", "HS0712", "a1@erg.edu.vn", "0911 111 111", "7A"),
            new StudentCard("u-a2", "Nguyễn Văn A", "HS0900", "a2@erg.edu.vn", null, "7B"),
            new StudentCard("u-minh", "Trần Đức Minh", "HS0100", "minh@erg.edu.vn", "0988.777.666", "7A"),
            new StudentCard("u-gone", "Phạm Đã Khoá", "HS0200", "gone@erg.edu.vn", null, "7A"),
            new StudentCard("u-b", "Lê Bảo", "HS0300", "b@erg.edu.vn", null, "9C"));
    private final List<String> classesOfRoll = List.of("7A", "7B", "7A", "7A", "9C");

    private final List<UserAccountProjection> teachers = List.of(
            new UserAccountProjection("t-hoa", TENANT, "Đỗ Thị Hoa", "hoa@erg.edu.vn", "ACTIVE"),
            new UserAccountProjection("t-off", TENANT, "Giáo Viên Nghỉ", "off@erg.edu.vn", "SUSPENDED"));
    private final List<UserAccountProjection> staff = List.of(
            new UserAccountProjection("st-lan", TENANT, "Vũ Lan", "lan@erg.edu.vn", "ACTIVE"));

    private final LmsAudienceReadPort lms = mock(LmsAudienceReadPort.class);
    private final UserDirectoryPort users = mock(UserDirectoryPort.class);
    private int largestUserLookup;
    private final FakeStudents students = new FakeStudents();
    private final RecipientLookupService service =
            new RecipientLookupService(students, lms, users, new AudienceScopeGuard(lms));

    @BeforeEach
    void wireDirectory() {
        when(lms.classIdsTaughtBy(TENANT, "gv1")).thenReturn(List.of("7A", "7B"));
        when(users.findCalendarTeachersByTenant(eq(TENANT), any(Pageable.class))).thenReturn(teachers);
        when(users.findActiveStaffUserIds(eq(TENANT), any(Pageable.class))).thenReturn(List.of("st-lan"));
        when(users.findByTenantIdAndIds(eq(TENANT), anyCollection())).thenAnswer(call -> {
            Set<String> ids = Set.copyOf((Collection<String>) call.getArgument(1));
            largestUserLookup = Math.max(largestUserLookup, ids.size());
            List<UserAccountProjection> all = new ArrayList<>(teachers);
            all.addAll(staff);
            roll.forEach(s -> all.add(new UserAccountProjection(s.userId(), TENANT, s.fullName(), s.email(),
                    s.userId().equals("u-gone") ? "SUSPENDED" : "ACTIVE")));
            students.extra.forEach(s -> all.add(new UserAccountProjection(s.userId(), TENANT, s.fullName(), null,
                    s.userId().startsWith("sus-") ? "SUSPENDED" : "ACTIVE")));
            return all.stream().filter(user -> ids.contains(user.id())).toList();
        });
    }

    private ResolvePeopleResult resolveAsAdmin(String... lines) {
        return service.resolveLines(TENANT, "ad1", ADMIN, List.of(lines));
    }

    private static List<String> ids(ResolvePeopleResult result) {
        return result.matched().stream().map(RecipientCard::userId).toList();
    }

    @Test
    void studentCodeMatchesAndCardShowsRoleClassAndCode() {
        ResolvePeopleResult result = resolveAsAdmin("HS0712");

        assertThat(result.matched()).containsExactly(
                new RecipientCard("u-a1", "Nguyễn Văn A", "STUDENT", "Học sinh · 7A · HS0712"));
        assertThat(result.notFound()).isEmpty();
        assertThat(result.ambiguous()).isEmpty();
    }

    @Test
    void studentCodeIsCaseInsensitive() {
        assertThat(ids(resolveAsAdmin("hs0712"))).containsExactly("u-a1");
    }

    @Test
    void emailMatchesIgnoringCase() {
        assertThat(ids(resolveAsAdmin("Minh@ERG.edu.vn"))).containsExactly("u-minh");
    }

    @Test
    void unknownPhoneIsReportedVerbatimAfterNormalising() {
        ResolvePeopleResult result = resolveAsAdmin("  0912 000 111  ");

        assertThat(result.matched()).isEmpty();
        assertThat(result.notFound()).containsExactly("0912 000 111");
    }

    @Test
    void phoneMatchesWhateverSeparatorsAreStoredOrTyped() {
        assertThat(ids(resolveAsAdmin("0911.111-111", "+84988777666"))).containsExactly("u-a1", "u-minh");
    }

    @Test
    void nameSharedByTwoPeopleIsAmbiguous() {
        ResolvePeopleResult result = resolveAsAdmin("Nguyễn Văn A");

        assertThat(result.matched()).isEmpty();
        assertThat(result.ambiguous()).hasSize(1);
        assertThat(result.ambiguous().get(0).line()).isEqualTo("Nguyễn Văn A");
        assertThat(result.ambiguous().get(0).candidates()).isEqualTo(2);
    }

    @Test
    void nameMatchIgnoresAccentsAndCase() {
        assertThat(ids(resolveAsAdmin("tran duc minh", "ĐỖ THỊ HOA"))).containsExactly("u-minh", "t-hoa");
    }

    @Test
    void blankLinesAreSkippedAndEdgesTrimmed() {
        ResolvePeopleResult result = resolveAsAdmin("", "   ", "  HS0712  ", "\t");

        assertThat(ids(result)).containsExactly("u-a1");
        assertThat(result.notFound()).isEmpty();
    }

    @Test
    void samePersonOnTwoLinesAppearsOnce() {
        assertThat(ids(resolveAsAdmin("HS0712", "HS0712", "a1@erg.edu.vn", "0911111111"))).containsExactly("u-a1");
    }

    @Test
    void moreThanFiveThousandLinesIsRejected() {
        List<String> lines = IntStream.rangeClosed(1, 5_001).mapToObj(i -> "HS" + i).toList();

        assertThatThrownBy(() -> service.resolveLines(TENANT, "ad1", ADMIN, lines))
                .isInstanceOf(BadRequestException.class);
        assertThat(service.resolveLines(TENANT, "ad1", ADMIN, lines.subList(0, 5_000)).notFound()).hasSize(5_000);
    }

    @Test
    void lookupIsBatchedNotPerLine() {
        resolveAsAdmin("HS0712", "HS0100", "minh@erg.edu.vn", "0912 000 111", "HS0300");

        assertThat(students.keyQueries).isEqualTo(1);
    }

    @Test
    void studentsWithoutAnActiveLoginAreNotOffered() {
        ResolvePeopleResult result = resolveAsAdmin("HS0200", "Phạm Đã Khoá");

        assertThat(result.matched()).isEmpty();
        assertThat(result.notFound()).containsExactly("HS0200", "Phạm Đã Khoá");
    }

    @Test
    void adminAlsoFindsActiveTeachersAndStaffButNotSuspendedOnes() {
        ResolvePeopleResult result = resolveAsAdmin("hoa@erg.edu.vn", "Vũ Lan", "off@erg.edu.vn");

        assertThat(result.matched()).containsExactly(
                new RecipientCard("t-hoa", "Đỗ Thị Hoa", "TEACHER", "Giáo viên · hoa@erg.edu.vn"),
                new RecipientCard("st-lan", "Vũ Lan", "STAFF", "Nhân viên · lan@erg.edu.vn"));
        assertThat(result.notFound()).containsExactly("off@erg.edu.vn");
    }

    @Test
    void teacherOnlyReachesStudentsOfTaughtClasses() {
        ResolvePeopleResult result = service.resolveLines(
                TENANT, "gv1", TEACHER, List.of("HS0712", "HS0300", "hoa@erg.edu.vn", "Vũ Lan", "Lê Bảo"));

        assertThat(ids(result)).containsExactly("u-a1");
        assertThat(result.notFound()).containsExactly("HS0300", "hoa@erg.edu.vn", "Vũ Lan", "Lê Bảo");
    }

    @Test
    void teacherWithNoClassesReachesNobody() {
        when(lms.classIdsTaughtBy(TENANT, "gv2")).thenReturn(List.of());

        assertThat(service.resolveLines(TENANT, "gv2", TEACHER, List.of("HS0712")).notFound())
                .containsExactly("HS0712");
        assertThat(service.search(TENANT, "gv2", TEACHER, "Nguyễn", 20)).isEmpty();
    }

    @Test
    void actorWhoIsNeitherAdminNorTeacherIsDenied() {
        assertThatThrownBy(() -> service.search(TENANT, "x", List.of("student"), "Minh", 5))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.resolveLines(TENANT, "x", null, List.of("HS0712")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void searchMatchesNamePartsIgnoringAccentsAndOrdersByName() {
        List<RecipientCard> found = service.search(TENANT, "ad1", ADMIN, "van", 20);

        assertThat(found).extracting(RecipientCard::userId).containsExactly("u-a1", "u-a2");
        assertThat(service.search(TENANT, "ad1", ADMIN, "  ĐỨC ", 20)).extracting(RecipientCard::userId)
                .containsExactly("u-minh");
    }

    @Test
    void searchIsExactOnCodeEmailAndPhone() {
        assertThat(service.search(TENANT, "ad1", ADMIN, "HS0100", 20)).extracting(RecipientCard::userId)
                .containsExactly("u-minh");
        assertThat(service.search(TENANT, "ad1", ADMIN, "minh@erg.edu.vn", 20)).extracting(RecipientCard::userId)
                .containsExactly("u-minh");
        assertThat(service.search(TENANT, "ad1", ADMIN, "0911 111 111", 20)).extracting(RecipientCard::userId)
                .containsExactly("u-a1");
        // exact, not prefix
        assertThat(service.search(TENANT, "ad1", ADMIN, "HS07", 20)).isEmpty();
    }

    @Test
    void searchBlankQueryIsEmptyAndLimitIsClamped() {
        assertThat(service.search(TENANT, "ad1", ADMIN, "   ", 20)).isEmpty();
        assertThat(service.search(TENANT, "ad1", ADMIN, null, 20)).isEmpty();
        assertThat(service.search(TENANT, "ad1", ADMIN, "van", 0)).hasSize(1);
        assertThat(service.search(TENANT, "ad1", ADMIN, "van", -5)).hasSize(1);

        List<StudentCard> many = IntStream.range(0, 40)
                .mapToObj(i -> new StudentCard("m" + i, "Học Sinh %02d".formatted(i), "M" + i, null, null, "7A"))
                .toList();
        students.extra = many;
        assertThat(service.search(TENANT, "ad1", ADMIN, "Học Sinh", 1_000)).hasSize(20);
    }

    @Test
    void searchForATeacherStaysInsideTaughtClassesAndNeverShowsStaff() {
        assertThat(service.search(TENANT, "gv1", TEACHER, "le", 20)).isEmpty(); // Lê Bảo is in 9C
        assertThat(service.search(TENANT, "gv1", TEACHER, "hoa", 20)).isEmpty();
        assertThat(service.search(TENANT, "gv1", TEACHER, "lan", 20)).isEmpty();
        assertThat(service.search(TENANT, "gv1", TEACHER, "minh", 20)).extracting(RecipientCard::userId)
                .containsExactly("u-minh");
    }

    @Test
    void searchDropsStudentsWhoseLoginIsSuspended() {
        assertThat(service.search(TENANT, "ad1", ADMIN, "Khoá", 20)).isEmpty();
        assertThat(service.search(TENANT, "ad1", ADMIN, "HS0200", 20)).isEmpty();
    }

    @Test
    void teacherNameOrPhoneSharedWithAStudentOutsideTheirClassesIsStillOneMatch() {
        students.extra = List.of(
                new StudentCard("u-t1", "Hoàng Trùng Tên", "T1", null, "0955 000 111", "7A"),
                new StudentCard("u-t2", "Hoàng Trùng Tên", "T2", null, "0955.000.111", "9C"),
                new StudentCard("u-out", "Chỉ Ngoài Lớp", "OUT1", null, "0966 000 222", "9C"));

        ResolvePeopleResult teacher = service.resolveLines(
                TENANT, "gv1", TEACHER, List.of("hoang trung ten", "0955000111", "OUT1", "0966000222", "Chỉ Ngoài Lớp"));
        assertThat(ids(teacher)).containsExactly("u-t1");
        assertThat(teacher.ambiguous()).isEmpty();
        assertThat(teacher.notFound()).containsExactly("OUT1", "0966000222", "Chỉ Ngoài Lớp");

        ResolvePeopleResult admin = resolveAsAdmin("hoang trung ten", "0955000111", "OUT1");
        assertThat(admin.ambiguous()).extracting(a -> a.candidates()).containsExactly(2, 2);
        assertThat(ids(admin)).containsExactly("u-out");
    }

    @Test
    void adminSearchFindsTeachersAndStaffByNameAndEmail() {
        assertThat(service.search(TENANT, "ad1", ADMIN, "đỗ thị", 20)).containsExactly(
                new RecipientCard("t-hoa", "Đỗ Thị Hoa", "TEACHER", "Giáo viên · hoa@erg.edu.vn"));
        assertThat(service.search(TENANT, "ad1", ADMIN, "HOA@erg.edu.vn", 20)).extracting(RecipientCard::userId)
                .containsExactly("t-hoa");
        assertThat(service.search(TENANT, "ad1", ADMIN, "vu lan", 20)).containsExactly(
                new RecipientCard("st-lan", "Vũ Lan", "STAFF", "Nhân viên · lan@erg.edu.vn"));
        assertThat(service.search(TENANT, "ad1", ADMIN, "lan@erg.edu.vn", 20)).extracting(RecipientCard::userId)
                .containsExactly("st-lan");
        assertThat(service.search(TENANT, "ad1", ADMIN, "nghi", 20)).isEmpty(); // suspended teacher
    }

    @Test
    void loginCheckIsChunkedAndStillFiltersCorrectlyAcrossThousandsOfStudents() {
        students.extra = IntStream.range(0, 2_500)
                .mapToObj(i -> new StudentCard((i % 5 == 0 ? "sus-" : "ok-") + i, "Khối Lớn " + i,
                        "BIG%04d".formatted(i), null, null, "7A"))
                .toList();
        List<String> lines = IntStream.range(0, 2_500).mapToObj(i -> "big%04d".formatted(i)).toList();

        ResolvePeopleResult result = service.resolveLines(TENANT, "ad1", ADMIN, lines);

        assertThat(result.matched()).hasSize(2_000);
        assertThat(result.notFound()).hasSize(500).contains("big0000", "big2495");
        assertThat(largestUserLookup).isBetween(1, 1_000);
    }

    @Test
    void blankPunctuationAndCombiningMarkLinesNeverMatchAnyone() {
        students.extra = List.of(
                new StudentCard("u-blank", "", "", "", "", "7A"),
                new StudentCard("u-null", null, null, null, null, "7A"));

        ResolvePeopleResult result = resolveAsAdmin("-", ".", "()", "  ", "", "́", "@");

        assertThat(result.matched()).isEmpty();
        assertThat(result.ambiguous()).isEmpty();
        assertThat(result.notFound()).containsExactly("-", ".", "()", "́", "@");
        assertThat(service.search(TENANT, "ad1", ADMIN, "́", 20)).isEmpty();
        assertThat(service.search(TENANT, "ad1", ADMIN, "()", 20)).isEmpty();
    }

    @Test
    void fiveThousandLinesAgainstFiveThousandStudentsResolveQuickly() {
        students.extra = IntStream.range(0, 5_000)
                .mapToObj(i -> new StudentCard("p" + i, "Học Sinh Mẫu " + i, "PERF%04d".formatted(i),
                        "p" + i + "@erg.edu.vn", "09%08d".formatted(i), "7A"))
                .toList();
        List<String> lines = IntStream.range(0, 5_000)
                .mapToObj(i -> switch (i % 3) {
                    case 0 -> "perf%04d".formatted(i);
                    case 1 -> "P" + i + "@ERG.edu.vn";
                    default -> "09 %08d".formatted(i);
                }).toList();

        long start = System.nanoTime();
        ResolvePeopleResult result = service.resolveLines(TENANT, "ad1", ADMIN, lines);
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertThat(result.matched()).hasSize(5_000);
        assertThat(millis).as("resolve time in ms").isLessThan(3_000);
    }

    /** In-memory stand-in for the lms read port with the same filtering contract. */
    private final class FakeStudents implements LmsStudentLookupPort {
        int keyQueries;
        List<StudentCard> extra = List.of();

        private List<StudentCard> scoped(Collection<String> classIds) {
            List<StudentCard> all = new ArrayList<>();
            for (int i = 0; i < roll.size(); i++) {
                if (classIds == null || classIds.contains(classesOfRoll.get(i))) {
                    all.add(roll.get(i));
                }
            }
            extra.stream().filter(s -> classIds == null || classIds.contains(s.className())).forEach(all::add);
            return all;
        }

        @Override
        public List<StudentCard> findByKeys(String tenantId, Collection<String> classIds,
                Collection<String> codes, Collection<String> emails, Collection<String> phones) {
            keyQueries++;
            return scoped(classIds).stream().filter(s ->
                    (s.studentCode() != null && codes.contains(s.studentCode().toUpperCase()))
                    || (s.email() != null && emails.contains(s.email().toLowerCase()))
                    || (s.phone() != null && phones.contains(s.phone().replaceAll("[ .\\-]", "")))).toList();
        }

        @Override
        public List<StudentCard> listStudents(String tenantId, Collection<String> classIds, int limit) {
            return scoped(classIds).stream().limit(limit).toList();
        }
    }
}
