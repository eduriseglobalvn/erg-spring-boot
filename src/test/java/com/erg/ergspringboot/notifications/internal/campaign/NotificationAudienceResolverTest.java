package com.erg.ergspringboot.notifications.internal.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.lms.LmsAudienceReadPort;
import com.erg.ergspringboot.notifications.CampaignAudience;
import com.erg.ergspringboot.users.UserAccountProjection;
import com.erg.ergspringboot.users.UserDirectoryPort;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class NotificationAudienceResolverTest {

    private static final String TENANT = "t1";

    private final LmsAudienceReadPort lms = mock(LmsAudienceReadPort.class);
    private final UserDirectoryPort users = mock(UserDirectoryPort.class);
    private final AudienceAccountQueries accounts = mock(AudienceAccountQueries.class);
    private final NotificationAudienceResolver resolver = new NotificationAudienceResolver(lms, users, accounts, 5_000);

    /** User ids the directory does not know at all / knows as suspended. */
    private final Set<String> unknownUsers = new HashSet<>();
    private final Set<String> suspendedUsers = new HashSet<>();

    private List<String> class7a;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void roster() {
        class7a = IntStream.rangeClosed(1, 31).mapToObj(i -> "s" + i).toList();
        when(lms.studentUserIdsInClasses(eq(TENANT), eq(List.of("7A")), anyInt())).thenReturn(class7a);
        when(accounts.activeUserIds(eq(TENANT), anyCollection())).thenAnswer(call ->
                ((Collection<String>) call.getArgument(1)).stream()
                        .filter(id -> !unknownUsers.contains(id) && !suspendedUsers.contains(id))
                        .collect(java.util.stream.Collectors.toSet()));
    }

    private static CampaignAudience audience(List<String> classIds, List<String> userIds) {
        return new CampaignAudience(false, null, null, classIds, null, null, userIds);
    }

    private static UserAccountProjection account(String id, String status) {
        return new UserAccountProjection(id, TENANT, id, id + "@x.vn", status);
    }

    @Test
    void mergesClassAndPickedWithoutDuplicatesKeepingClassOrderFirst() {
        // s5, s9 already in 7A; x1 is outside the class.
        ResolvedAudience result = resolver.resolve(TENANT, audience(List.of("7A"), List.of("x1", "s5", "s9")));

        assertThat(result.breakdown()).isEqualTo(new AudienceBreakdown(32, 0, 31, 0, 3, 2));
        List<String> expected = new ArrayList<>(class7a);
        expected.add("x1");
        assertThat(result.userIds()).containsExactlyElementsOf(expected);
    }

    @Test
    void pickedUnknownOrSuspendedUserIsDropped() {
        unknownUsers.add("ghost");
        suspendedUsers.add("gone");

        ResolvedAudience result = resolver.resolve(TENANT, audience(List.of("7A"), List.of("ghost", "gone")));

        assertThat(result.breakdown().total()).isEqualTo(31);
        assertThat(result.breakdown().picked()).isZero();
    }

    @Test
    void studentWhoseLoginIsSuspendedOrMissingIsNotReached() {
        suspendedUsers.add("s2");
        unknownUsers.add("s3");

        ResolvedAudience result = resolver.resolve(TENANT, audience(List.of("7A"), List.of()));

        assertThat(result.userIds()).hasSize(29).doesNotContain("s2", "s3");
        assertThat(result.breakdown().students()).isEqualTo(29);
    }

    @Test
    void rejectsMoreThanFiveThousandRecipients() {
        List<String> many = IntStream.rangeClosed(1, 5001).mapToObj(i -> "u" + i).toList();
        when(lms.studentUserIdsInClasses(eq(TENANT), eq(List.of("BIG")), anyInt())).thenReturn(many);

        assertThatThrownBy(() -> resolver.resolve(TENANT, audience(List.of("BIG"), List.of())))
                .isInstanceOf(AudienceTooLargeException.class)
                .hasMessage("Tối đa 5.000 người nhận mỗi lần gửi");
    }

    @Test
    void exactlyFiveThousandIsAllowed() {
        List<String> many = IntStream.rangeClosed(1, 5000).mapToObj(i -> "u" + i).toList();
        when(lms.studentUserIdsInClasses(eq(TENANT), eq(List.of("BIG")), anyInt())).thenReturn(many);

        assertThat(resolver.resolve(TENANT, audience(List.of("BIG"), List.of())).breakdown().total())
                .isEqualTo(5000);
    }

    @Test
    void theCapIsConfigurableSoATwentyThousandStudentTenantCanBeReached() {
        List<String> everyone = IntStream.rangeClosed(1, 20_000).mapToObj(i -> "u" + i).toList();
        when(accounts.countActiveStudents(TENANT, null)).thenReturn(20_000L);
        when(accounts.activeStudentUserIds(eq(TENANT), anyInt())).thenReturn(everyone);
        CampaignAudience students = new CampaignAudience(false, null, null, null, null, List.of("STUDENT"), null);

        NotificationAudienceResolver wide = new NotificationAudienceResolver(lms, users, accounts, 50_000);
        assertThat(wide.resolve(TENANT, students).breakdown().total()).isEqualTo(20_000);

        NotificationAudienceResolver narrow = new NotificationAudienceResolver(lms, users, accounts, 10_000);
        assertThatThrownBy(() -> narrow.resolve(TENANT, students))
                .isInstanceOf(AudienceTooLargeException.class)
                .hasMessage("Tối đa 10.000 người nhận mỗi lần gửi");
    }

    @Test
    void wholeStudentBodyOverTheCapFailsOnTheSqlCountBeforeAnyIdIsLoaded() {
        when(accounts.countActiveStudents(TENANT, null)).thenReturn(20_000L);

        assertThatThrownBy(() -> resolver.resolve(TENANT, new CampaignAudience(false, null, null, null, null, List.of("STUDENT"), null)))
                .isInstanceOf(AudienceTooLargeException.class);
        verify(accounts, never()).activeStudentUserIds(any(), anyInt());
        verifyNoInteractions(lms);
    }

    @Test
    void classAudienceOverTheCapFailsOnTheSqlCountBeforeTheRosterIsRead() {
        when(accounts.countActiveStudents(eq(TENANT), anyCollection())).thenReturn(5_001L);

        assertThatThrownBy(() -> resolver.resolve(TENANT, audience(List.of("BIG"), List.of())))
                .isInstanceOf(AudienceTooLargeException.class);
        verify(lms, never()).studentUserIdsInClasses(any(), any(), anyInt());
    }
    @Test
    void pickedListOverFiveThousandIsRejectedBeforeAnyLookup() {
        List<String> picked = IntStream.rangeClosed(1, 5001).mapToObj(i -> "p" + i).toList();

        assertThatThrownBy(() -> resolver.resolve(TENANT, audience(List.of(), picked)))
                .isInstanceOf(AudienceTooLargeException.class);
        verifyNoInteractions(lms);
        verifyNoInteractions(accounts);
    }

    @Test
    void rolesResolveTeachersStudentsAndStaffCountingEachPersonOnce() {
        when(accounts.activeStudentUserIds(eq(TENANT), anyInt())).thenReturn(List.of("s1", "s2"));
        when(users.findCalendarTeachersByTenant(eq(TENANT), any(Pageable.class)))
                .thenReturn(List.of(account("t1u", "ACTIVE"), account("s2", "ACTIVE")));
        when(users.findActiveStaffUserIds(eq(TENANT), any(Pageable.class))).thenReturn(List.of("f1"));

        ResolvedAudience result = resolver.resolve(TENANT,
                new CampaignAudience(false, null, null, null, null, List.of("TEACHER", "STUDENT", "STAFF"), null));

        // a user is counted once, in the first group that reached them (students come first)
        assertThat(result.breakdown()).isEqualTo(new AudienceBreakdown(4, 1, 2, 1, 0, 0));
        assertThat(result.userIds()).containsExactly("s1", "s2", "t1u", "f1");
    }

    @Test
    void wholeTenantReachesEveryoneKind() {
        when(accounts.activeStudentUserIds(eq(TENANT), anyInt())).thenReturn(List.of("s1"));
        when(users.findCalendarTeachersByTenant(eq(TENANT), any(Pageable.class)))
                .thenReturn(List.of(account("t1u", "ACTIVE")));
        when(users.findActiveStaffUserIds(eq(TENANT), any(Pageable.class))).thenReturn(List.of("f1"));

        ResolvedAudience result = resolver.resolve(TENANT, new CampaignAudience(true, null, null, null, null, null, null));

        assertThat(result.breakdown()).isEqualTo(new AudienceBreakdown(3, 1, 1, 1, 0, 0));
    }

    @Test
    void schoolScopeReachesItsClassesStudentsAndTeachers() {
        when(lms.classIdsOf(eq(TENANT), eq(List.of("sch")), eq(List.of()))).thenReturn(List.of("7A"));
        when(users.findCalendarTeachersByTenantAndSchoolIds(eq(TENANT), eq(List.of("sch")), any(Pageable.class)))
                .thenReturn(List.of(account("t1u", "ACTIVE")));

        ResolvedAudience result = resolver.resolve(TENANT,
                new CampaignAudience(false, List.of("sch"), null, null, null, null, null));

        assertThat(result.breakdown()).isEqualTo(new AudienceBreakdown(32, 1, 31, 0, 0, 0));
    }

    @Test
    void groupMembersAreReachedAndMergedWithClassMembers() {
        when(lms.studentUserIdsInGroups(eq(TENANT), eq(List.of("g1")), anyInt())).thenReturn(List.of("s1", "g-only"));

        ResolvedAudience result = resolver.resolve(TENANT,
                new CampaignAudience(false, null, null, List.of("7A"), List.of("g1"), null, null));

        assertThat(result.breakdown()).isEqualTo(new AudienceBreakdown(32, 0, 32, 0, 0, 0));
        assertThat(result.userIds()).endsWith("g-only");
    }

    @Test
    void emptyAudienceResolvesToNobody() {
        ResolvedAudience result = resolver.resolve(TENANT, audience(List.of(), List.of()));

        assertThat(result.userIds()).isEmpty();
        assertThat(result.breakdown()).isEqualTo(new AudienceBreakdown(0, 0, 0, 0, 0, 0));
        verifyNoInteractions(users);
    }
}
