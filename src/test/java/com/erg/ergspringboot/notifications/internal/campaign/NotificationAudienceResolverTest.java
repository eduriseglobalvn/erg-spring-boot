package com.erg.ergspringboot.notifications.internal.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.lms.LmsAudienceReadPort;
import com.erg.ergspringboot.notifications.CampaignAudience;
import com.erg.ergspringboot.users.UserAccountProjection;
import com.erg.ergspringboot.users.UserDirectoryPort;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationAudienceResolverTest {

    private static final String TENANT = "t1";

    private final LmsAudienceReadPort lms = mock(LmsAudienceReadPort.class);
    private final UserDirectoryPort users = mock(UserDirectoryPort.class);
    private final NotificationAudienceResolver resolver = new NotificationAudienceResolver(lms, users);

    private List<String> class7a;

    @BeforeEach
    void roster() {
        class7a = IntStream.rangeClosed(1, 31).mapToObj(i -> "s" + i).toList();
        when(lms.studentUserIdsInClasses(eq(TENANT), eq(List.of("7A")), anyInt())).thenReturn(class7a);
    }

    private static CampaignAudience audience(List<String> classIds, List<String> userIds) {
        return new CampaignAudience(false, null, null, classIds, null, null, userIds);
    }

    private static UserAccountProjection active(String id) {
        return new UserAccountProjection(id, TENANT, id, id + "@x.vn", "ACTIVE");
    }

    @Test
    void mergesClassAndPickedWithoutDuplicatesKeepingClassOrderFirst() {
        // s5, s9 already in 7A; x1 is outside the class.
        when(users.findByTenantIdAndIds(eq(TENANT), any(Collection.class))).thenReturn(List.of(active("x1")));

        ResolvedAudience result = resolver.resolve(TENANT, audience(List.of("7A"), List.of("x1", "s5", "s9")));

        assertThat(result.breakdown()).isEqualTo(new AudienceBreakdown(32, 0, 31, 0, 3, 2));
        List<String> expected = new ArrayList<>(class7a);
        expected.add("x1");
        assertThat(result.userIds()).containsExactlyElementsOf(expected);
    }

    @Test
    void pickedUnknownUserIsDropped() {
        when(users.findByTenantIdAndIds(eq(TENANT), any(Collection.class))).thenReturn(List.of());

        ResolvedAudience result = resolver.resolve(TENANT, audience(List.of("7A"), List.of("ghost")));

        assertThat(result.breakdown().total()).isEqualTo(31);
        assertThat(result.breakdown().picked()).isZero();
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
    void rolesAndSchoolsResolveTeachersStudentsAndStaff() {
        when(lms.allStudentUserIds(eq(TENANT), anyInt())).thenReturn(List.of("s1", "s2"));
        when(users.findCalendarTeachersByTenant(eq(TENANT), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(active("t1u"), active("s2")));
        when(users.findActiveStaffUserIds(eq(TENANT), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of("f1"));

        ResolvedAudience result = resolver.resolve(TENANT,
                new CampaignAudience(false, null, null, null, null, List.of("TEACHER", "STUDENT", "STAFF"), null));

        // a user is counted once, in the first group that reached it (students come first)
        assertThat(result.breakdown()).isEqualTo(new AudienceBreakdown(4, 1, 2, 1, 0, 0));
        assertThat(result.userIds()).containsExactly("s1", "s2", "t1u", "f1");
    }

    @Test
    void emptyAudienceResolvesToNobody() {
        ResolvedAudience result = resolver.resolve(TENANT, audience(List.of(), List.of()));

        assertThat(result.userIds()).isEmpty();
        assertThat(result.breakdown()).isEqualTo(new AudienceBreakdown(0, 0, 0, 0, 0, 0));
        org.mockito.Mockito.verifyNoInteractions(users);
    }
}
