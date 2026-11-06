package com.erg.ergspringboot.notifications.internal.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.lms.LmsAudienceReadPort;
import com.erg.ergspringboot.notifications.CampaignAudience;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class AudienceScopeGuardTest {

    private static final String TENANT = "t1";
    private static final String TEACHER = "gv1";
    private static final Set<String> TEACHER_ROLES = Set.of("teacher");

    private final LmsAudienceReadPort lms = mock(LmsAudienceReadPort.class);
    private final AudienceScopeGuard guard = new AudienceScopeGuard(lms);

    @BeforeEach
    void teachesSevenAAndSevenB() {
        when(lms.classIdsTaughtBy(TENANT, TEACHER)).thenReturn(List.of("7A", "7B"));
        // only s1 is a student of 7A/7B; whatever else is asked about is "outside"
        when(lms.studentUserIdsInClassesAmong(eq(TENANT), eq(List.of("7A", "7B")), any(Collection.class)))
                .thenAnswer(call -> ((Collection<String>) call.getArgument(2)).stream()
                        .filter("s1"::equals).toList());
    }

    private static CampaignAudience classes(String... ids) {
        return new CampaignAudience(false, null, null, List.of(ids), null, null, null);
    }

    private void teacherSends(CampaignAudience audience) {
        guard.assertAllowed(TENANT, TEACHER, TEACHER_ROLES, audience);
    }

    @Test
    void seededTeacherRoleIsATeacherThroughItsVerifiedPermissions() {
        CampaignActor seeded = new CampaignActor(TENANT, TEACHER, Set.of("lms_teacher_standard"),
                List.of("lms.assignment.read", "notification.campaign.send"), List.of());

        assertThat(guard.isTeacher(seeded)).isTrue();
        assertThat(guard.isAdmin(seeded)).isFalse();
        assertThatCode(() -> guard.assertAllowed(seeded, classes("7A"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.assertAllowed(seeded, classes("8A"))).isInstanceOf(AccessDeniedException.class);
        // the role name alone (no permissions) is still nobody
        assertThatThrownBy(() -> guard.assertAllowed(TENANT, TEACHER, Set.of("lms_teacher_standard"), classes("7A")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adminIsNeverDerivedFromPermissions() {
        CampaignActor contentEditor = new CampaignActor(TENANT, "ed1", Set.of("content_editor"),
                List.of("content.read", "rbac.role.read"), List.of());

        assertThat(guard.isAdmin(contentEditor)).isFalse();
        assertThatThrownBy(() -> guard.assertAllowed(contentEditor,
                new CampaignAudience(true, null, null, null, null, null, null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deniedTeacherWorkspaceFailsClosed() {
        CampaignActor denied = new CampaignActor(TENANT, TEACHER, Set.of("lms_teacher_standard"),
                List.of("lms.assignment.read"), List.of("workspace.*"));

        assertThat(guard.isTeacher(denied)).isFalse();
        assertThat(guard.isTeacher(new CampaignActor(TENANT, TEACHER, null, null, null))).isFalse();
    }

    @Test
    void teacherMayTargetOwnClasses() {
        assertThatCode(() -> teacherSends(classes("7A"))).doesNotThrowAnyException();
        assertThatCode(() -> teacherSends(classes("7A", "7B"))).doesNotThrowAnyException();
    }

    @Test
    void teacherMayNotTargetOtherClasses() {
        assertThatThrownBy(() -> teacherSends(classes("8A"))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> teacherSends(classes("7A", "8A"))).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void teacherMayNotUseWiderScopes() {
        assertThatThrownBy(() -> teacherSends(new CampaignAudience(true, null, null, null, null, null, null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> teacherSends(new CampaignAudience(false, List.of("sch"), null, null, null, null, null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> teacherSends(new CampaignAudience(false, null, List.of("7"), null, null, null, null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> teacherSends(new CampaignAudience(false, null, null, null, null, List.of("TEACHER"), null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void teacherMayPickOnlyOwnStudents() {
        assertThatCode(() -> teacherSends(new CampaignAudience(false, null, null, null, null, null, List.of("s1"))))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> teacherSends(
                new CampaignAudience(false, null, null, null, null, null, List.of("s1", "outsider"))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void teacherMayTargetGroupOnlyWhenAllMembersAreOwnStudents() {
        when(lms.studentUserIdsInGroups(eq(TENANT), eq(List.of("g-ok")), anyInt())).thenReturn(List.of("s1"));
        when(lms.studentUserIdsInGroups(eq(TENANT), eq(List.of("g-mixed")), anyInt())).thenReturn(List.of("s1", "z"));

        assertThatCode(() -> teacherSends(new CampaignAudience(false, null, null, null, List.of("g-ok"), null, null)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> teacherSends(
                new CampaignAudience(false, null, null, null, List.of("g-mixed"), null, null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adminMayTargetAnything() {
        CampaignAudience everything = new CampaignAudience(
                true, List.of("sch"), List.of("7"), List.of("8A"), List.of("g"), List.of("STAFF"), List.of("anyone"));

        assertThatCode(() -> guard.assertAllowed(TENANT, "ad1", Set.of("admin"), everything))
                .doesNotThrowAnyException();
    }

    @Test
    void pickedListOverFiveThousandIsRejectedBeforeAnyQuery() {
        List<String> many = java.util.stream.IntStream.rangeClosed(1, 5001).mapToObj(i -> "u" + i).toList();

        assertThatThrownBy(() -> teacherSends(new CampaignAudience(false, null, null, null, null, null, many)))
                .isInstanceOf(AudienceTooLargeException.class);
        org.mockito.Mockito.verify(lms, org.mockito.Mockito.never()).studentUserIdsInClassesAmong(any(), any(), any());
    }

    @Test
    void missingRolesFailClosedAsForbidden() {
        assertThatThrownBy(() -> guard.assertAllowed(TENANT, TEACHER, null, classes("7A")))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> guard.assertAllowed(TENANT, TEACHER, java.util.Collections.singleton(null), classes("7A")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void usersWithoutTeacherOrAdminRoleMaySendNothing() {
        assertThatThrownBy(() -> guard.assertAllowed(TENANT, "hs1", Set.of("student"), classes("7A")))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> guard.assertAllowed(TENANT, "x", Set.of(), classes("7A")))
                .isInstanceOf(AccessDeniedException.class);
    }
}
