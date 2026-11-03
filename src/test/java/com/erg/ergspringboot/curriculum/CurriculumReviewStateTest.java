package com.erg.ergspringboot.curriculum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.curriculum.internal.CurriculumReviewAccess;
import com.erg.ergspringboot.curriculum.internal.query.CurriculumTreeQueryUseCase;
import com.erg.ergspringboot.curriculum.internal.validation.CurriculumChainValidationUseCase;
import com.erg.ergspringboot.model.curriculum.CurriculumSubject;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.curriculum.CurriculumLevelRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumSubjectRepository;
import com.erg.ergspringboot.repository.curriculum.CurriculumTopicRepository;
import com.erg.ergspringboot.service.curriculum.CurriculumException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** B4: nodes moved to in_review/approved by the lifecycle stay visible and usable for curriculum managers. */
class CurriculumReviewStateTest {
    private final CurriculumSubjectRepository subjects = mock(CurriculumSubjectRepository.class);
    private final CurriculumLevelRepository levels = mock(CurriculumLevelRepository.class);
    private final CurriculumTopicRepository topics = mock(CurriculumTopicRepository.class);
    private final CurriculumReviewAccess reviewAccess = mock(CurriculumReviewAccess.class);
    private final UserAccessContext access = new UserAccessContext("u-1", "erg", List.of("*"), List.of("*"), null, null);

    @Test
    void managersCanFilterTheTreeByReviewStates() {
        when(reviewAccess.canSeeReviewStates()).thenReturn(true);
        CurriculumSubject subject = new CurriculumSubject();
        subject.setId("sub-1");
        subject.setName("Math");
        subject.setStatus("in_review");
        when(subjects.findByTenantIdAndStatusIgnoreCaseOrderBySortOrderAscNameAsc(eq("erg"), eq("in_review"), any()))
                .thenReturn(List.of(subject));

        var tree = new CurriculumTreeQueryUseCase(subjects, levels, topics, reviewAccess).tree("in_review", access);

        assertThat(tree.subjects()).hasSize(1);
    }

    @Test
    void nonManagersStillGetInvalidStatusForReviewStates() {
        when(reviewAccess.canSeeReviewStates()).thenReturn(false);

        assertThatThrownBy(() -> new CurriculumTreeQueryUseCase(subjects, levels, topics, reviewAccess).tree("approved", access))
                .isInstanceOf(CurriculumException.class);
    }

    @Test
    void reviewStateParentsAreUsableOnlyForManagers() {
        when(reviewAccess.isUsableParent(any())).thenCallRealMethod();
        when(reviewAccess.canSeeReviewStates()).thenReturn(true);
        assertThat(reviewAccess.isUsableParent("approved")).isTrue();
        assertThat(reviewAccess.isUsableParent("draft")).isFalse();
        assertThat(CurriculumReviewAccess.denyAll().isUsableParent("in_review")).isFalse();
        assertThat(CurriculumReviewAccess.denyAll().isUsableParent("active")).isTrue();
        assertThat(new CurriculumChainValidationUseCase(subjects, levels, topics, reviewAccess)).isNotNull();
    }
}
