package com.erg.ergspringboot.content.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.authorization.AuthorizationOperations;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.content.ContentLifecycleTransition;
import com.erg.ergspringboot.model.learningcontent.ContentItem;
import com.erg.ergspringboot.platform.security.ErgPolicyService;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.content.ContentLifecycleTransitionRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumLevelRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumSubjectRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumTopicRepository;
import com.erg.ergspringboot.repository.learningcontent.ContentItemRepository;
import com.erg.ergspringboot.repository.lms.QuestionRepository;
import com.erg.ergspringboot.repository.lms.QuizRepository;
import com.erg.ergspringboot.repository.quiz.authoring.QuizDraftRepository;
import com.erg.ergspringboot.repository.quiz.version.QuizVersionRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ContentLifecycleOperationsTest {

    private final ContentLifecycleTransitionRepository transitions = mock(ContentLifecycleTransitionRepository.class);
    private final CurriculumSubjectRepository subjects = mock(CurriculumSubjectRepository.class);
    private final CurriculumLevelRepository levels = mock(CurriculumLevelRepository.class);
    private final CurriculumTopicRepository topics = mock(CurriculumTopicRepository.class);
    private final ContentItemRepository contentItems = mock(ContentItemRepository.class);
    private final QuestionRepository questions = mock(QuestionRepository.class);
    private final QuizRepository quizzes = mock(QuizRepository.class);
    private final QuizDraftRepository quizDrafts = mock(QuizDraftRepository.class);
    private final QuizVersionRepository quizVersions = mock(QuizVersionRepository.class);
    private final ContentLifecycleOperations operations = new ContentLifecycleOperations(
            transitions, subjects, levels, topics, contentItems, questions, quizzes,
            quizDrafts, quizVersions, new AuthorizationOperations(new ErgPolicyService()));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsMissingAuthenticatedPrincipalBeforeReadingTenantData() {
        assertThatThrownBy(() -> operations.transition(
                access("tenant-a", "user-1"), LifecycleResourceKind.CONTENT_ITEM, "item-1",
                request("IN_REVIEW", "key-1", "submit")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("authenticated principal");

        verifyNoInteractions(contentItems);
    }

    @Test
    void rejectsCallerSuppliedTenantOrActorMismatch() {
        authenticate("content.lifecycle.submit");

        assertThatThrownBy(() -> operations.transition(
                access("tenant-b", "user-1"), LifecycleResourceKind.CONTENT_ITEM, "item-1",
                request("IN_REVIEW", "key-1", "submit")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("does not match");
        verifyNoInteractions(contentItems);
    }

    @Test
    void tenantIsTakenFromVerifiedAccessContextAndForeignResourceIsNotLoaded() {
        authenticate("content.lifecycle.submit");
        when(contentItems.findByIdAndTenantId("item-1", "tenant-a")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> operations.transition(
                access("tenant-a", "user-1"), LifecycleResourceKind.CONTENT_ITEM, "item-1",
                request("IN_REVIEW", "key-1", "submit")))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(contentItems).findByIdAndTenantId("item-1", "tenant-a");
    }

    @Test
    void staleOptimisticVersionIsRejectedBeforeMutation() {
        authenticate("content.lifecycle.submit");
        ContentItem item = item("item-1", "tenant-a", 3L);
        when(contentItems.findByIdAndTenantId("item-1", "tenant-a")).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> operations.transition(
                access("tenant-a", "user-1"), LifecycleResourceKind.CONTENT_ITEM, "item-1",
                new ContentLifecycleRequests.TransitionRequest("IN_REVIEW", 2L, "submit", "key-1", null, null)))
                .isInstanceOf(LifecycleTransitionException.class)
                .hasMessageContaining("reload");

        verify(contentItems).findByIdAndTenantId("item-1", "tenant-a");
        verifyNoInteractions(quizDrafts, quizVersions);
    }

    @Test
    void duplicateIdempotencyReplaysOnlyWithinTheSameResourceKind() {
        authenticate("content.lifecycle.submit");
        ContentLifecycleTransition replay = transition("CONTENT_ITEM", "item-1", "DRAFT", "IN_REVIEW", "user-1");
        when(transitions.findByTenantIdAndResourceKindAndResourceIdAndIdempotencyKey(
                "tenant-a", "CONTENT_ITEM", "item-1", "key-1")).thenReturn(Optional.of(replay));

        var response = operations.transition(
                access("tenant-a", "user-1"), LifecycleResourceKind.CONTENT_ITEM, "item-1",
                request("IN_REVIEW", "key-1", "submit"));

        assertThat(response.replayed()).isTrue();
        assertThat(response.state()).isEqualTo(LifecycleState.IN_REVIEW);
        verifyNoInteractions(contentItems);
    }

    @Test
    void reviewSubmitterCannotApproveWithoutElevatedPermission() {
        authenticate("content.lifecycle.approve");
        ContentItem item = item("item-1", "tenant-a", 0L);
        item.setLifecycleState(LifecycleState.IN_REVIEW.name());
        item.setStatus("in_review");
        when(contentItems.findByIdAndTenantId("item-1", "tenant-a")).thenReturn(Optional.of(item));
        ContentLifecycleTransition review = transition(
                "CONTENT_ITEM", "item-1", "DRAFT", "IN_REVIEW", "user-1");
        when(transitions.findTopByTenantIdAndResourceKindAndResourceIdAndToStateOrderByCreatedAtDesc(
                "tenant-a", "CONTENT_ITEM", "item-1", "IN_REVIEW")).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> operations.transition(
                access("tenant-a", "user-1"), LifecycleResourceKind.CONTENT_ITEM, "item-1",
                request("APPROVED", "key-2", "approve")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("submitter cannot approve");
    }

    @Test
    void validTransitionUpdatesCanonicalStateAndWritesTrail() {
        authenticate("content.lifecycle.submit");
        ContentItem item = item("item-1", "tenant-a", 0L);
        when(contentItems.findByIdAndTenantId("item-1", "tenant-a")).thenReturn(Optional.of(item));
        when(contentItems.saveAndFlush(any(ContentItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transitions.save(any(ContentLifecycleTransition.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = operations.transition(
                access("tenant-a", "user-1"), LifecycleResourceKind.CONTENT_ITEM, "item-1",
                request("IN_REVIEW", "key-1", "submit"));

        assertThat(response.state()).isEqualTo(LifecycleState.IN_REVIEW);
        assertThat(item.getLifecycleState()).isEqualTo(LifecycleState.IN_REVIEW.name());
        assertThat(item.getStatus()).isEqualTo("in_review");
        verify(contentItems).saveAndFlush(eq(item));
        verify(transitions).save(any(ContentLifecycleTransition.class));
    }

    private void authenticate(String... permissions) {
        ErgPrincipal principal = ErgPrincipal.authenticated(
                "user-1", "session-1", "tenant-a", List.of("admin"), List.of(permissions),
                List.of(), List.of("admin"), "employee", "admin", Map.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private static UserAccessContext access(String tenant, String user) {
        return new UserAccessContext(user, tenant, List.of(), List.of(), null, null);
    }

    private static ContentLifecycleRequests.TransitionRequest request(
            String target, String key, String reason) {
        return new ContentLifecycleRequests.TransitionRequest(target, null, reason, key, null, null);
    }

    private static ContentItem item(String id, String tenant, long version) {
        ContentItem item = new ContentItem();
        item.setId(id);
        item.setTenantId(tenant);
        item.setLifecycleState(LifecycleState.DRAFT.name());
        item.setStatus("draft");
        item.setRecordVersion(version);
        return item;
    }

    private static ContentLifecycleTransition transition(
            String kind, String id, String from, String to, String actor) {
        ContentLifecycleTransition value = new ContentLifecycleTransition();
        value.setTenantId("tenant-a");
        value.setResourceKind(kind);
        value.setResourceId(id);
        value.setFromState(from);
        value.setToState(to);
        value.setActorId(actor);
        value.setReason("submit");
        value.setIdempotencyKey("key-1");
        value.setResultingVersion(1L);
        return value;
    }
}
