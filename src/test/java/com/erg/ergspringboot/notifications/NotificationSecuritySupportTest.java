package com.erg.ergspringboot.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** STU-04: learners read their own inbox; LMS assignment notices are stored under the "lms" portal. */
class NotificationSecuritySupportTest {

    private final NotificationSecuritySupport support = new NotificationSecuritySupport();

    private final ErgPrincipal learner = ErgPrincipal.authenticated("usr_stu", "sess", "erg",
            List.of("lms.learner"),
            List.of("lms.assignment.read_self", "lms.assignment.submit", "lms.quiz.play", "elearning.assignment.read_self"),
            List.of(), List.of("elearning"), "student", "self", Map.of());

    private final ErgPrincipal teacher = ErgPrincipal.authenticated("usr_tch", "sess", "erg",
            List.of("lms.teacher"), List.of("lms.assignment.read"), List.of(), List.of("lms"), "staff", "class", Map.of());

    @Test
    void learnerReadsTheDefaultLmsInboxAndTheStudentInbox() {
        assertThat(support.verifiedPortal(learner, null)).isEqualTo("lms");
        assertThat(support.verifiedPortal(learner, "lms")).isEqualTo("lms");
        assertThat(support.verifiedPortal(learner, "elearning")).isEqualTo("elearning");
    }

    @Test
    void learnerCannotReadTheAdminOrCrmInbox() {
        assertThatThrownBy(() -> support.verifiedPortal(learner, "lcms")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> support.verifiedPortal(learner, "crm")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void teacherKeepsTheLmsInboxOnly() {
        assertThat(support.verifiedPortal(teacher, null)).isEqualTo("lms");
        assertThatThrownBy(() -> support.verifiedPortal(teacher, "elearning")).isInstanceOf(ForbiddenException.class);
    }
}
