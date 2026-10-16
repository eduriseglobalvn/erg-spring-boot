package com.erg.ergspringboot.controller.elearning.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.AnnouncementReadResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionFeedResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionPostResponse;
import com.erg.ergspringboot.controller.elearning.graphql.StudentEngagementGraphQlController.CreateStudentDiscussionInput;
import com.erg.ergspringboot.controller.elearning.graphql.StudentEngagementGraphQlController.MarkStudentAnnouncementReadInput;
import com.erg.ergspringboot.controller.elearning.graphql.StudentEngagementGraphQlController.StudentDiscussionFeedInput;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.elearning.ElearningOperations;
import com.erg.ergspringboot.elearning.api.ElearningCommands.CreateDiscussionCommand;
import com.erg.ergspringboot.elearning.api.ElearningCommands.DiscussionFeedQuery;
import com.erg.ergspringboot.elearning.api.ElearningCommands.MarkAnnouncementReadCommand;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPolicyService;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class StudentEngagementGraphQlControllerTest {

    @Mock
    private ElearningOperations service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void discussionFeedDerivesTenantAndActorFromPrincipal() {
        authenticate(principal("student-user-1", "tenant-a"));
        StudentDiscussionFeedResponse expected = new StudentDiscussionFeedResponse(List.of(), 0, 20, 0, false);
        when(service.discussionFeed(any())).thenReturn(expected);
        StudentEngagementGraphQlController controller = controller();

        assertThat(controller.discussionFeed(new StudentDiscussionFeedInput("tenant-a", 0, 20))).isSameAs(expected);

        ArgumentCaptor<DiscussionFeedQuery> captor = ArgumentCaptor.forClass(DiscussionFeedQuery.class);
        verify(service).discussionFeed(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo("tenant-a");
        assertThat(captor.getValue().actorUserId()).isEqualTo("student-user-1");
    }

    @Test
    void createDiscussionIgnoresClientActorAndUsesAuthenticatedPrincipal() {
        authenticate(principal("student-user-1", "tenant-a"));
        StudentDiscussionPostResponse expected = StudentDiscussionPostResponse.empty("post-1");
        when(service.createDiscussion(any())).thenReturn(expected);
        StudentEngagementGraphQlController controller = controller();

        controller.createStudentDiscussion(new CreateStudentDiscussionInput(
                "tenant-a", "idem-1", "Cần hỗ trợ", "Nội dung", null, List.of()
        ));

        ArgumentCaptor<CreateDiscussionCommand> captor =
                ArgumentCaptor.forClass(CreateDiscussionCommand.class);
        verify(service).createDiscussion(captor.capture());
        assertThat(captor.getValue().actorUserId()).isEqualTo("student-user-1");
        assertThat(captor.getValue().tenantId()).isEqualTo("tenant-a");
    }

    @Test
    void announcementReadRejectsCrossTenantRequestBeforeServiceCall() {
        authenticate(principal("student-user-1", "tenant-a"));
        StudentEngagementGraphQlController controller = controller();

        assertThatThrownBy(() -> controller.markStudentAnnouncementRead(
                new MarkStudentAnnouncementReadInput("tenant-b", "announcement-1")
        )).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(service);
    }

    @Test
    void announcementReadReturnsServiceResult() {
        authenticate(principal("student-user-1", "tenant-a"));
        AnnouncementReadResponse expected = new AnnouncementReadResponse(
                "announcement-1", true, OffsetDateTime.parse("2026-07-30T12:00:00+07:00")
        );
        when(service.markAnnouncementRead(any())).thenReturn(expected);

        assertThat(controller().markStudentAnnouncementRead(
                new MarkStudentAnnouncementReadInput(null, "announcement-1")
        )).isSameAs(expected);
    }

    private StudentEngagementGraphQlController controller() {
        return new StudentEngagementGraphQlController(service, new GraphQlAccessSupport(new ErgPolicyService()));
    }

    private void authenticate(ErgPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );
    }

    private ErgPrincipal principal(String userId, String tenantId) {
        return new ErgPrincipal(
                userId,
                "session-1",
                tenantId,
                List.of("student"),
                List.of("elearning.dashboard.read", "elearning.discussion.write_self", "elearning.announcement.read_self"),
                List.of(),
                List.of("elearning"),
                "student",
                "self",
                Map.of()
        );
    }
}
