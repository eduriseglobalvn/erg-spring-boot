package com.erg.ergspringboot.controller.curriculum;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.erg.ergspringboot.curriculum.CurriculumException;
import com.erg.ergspringboot.curriculum.CurriculumOperations;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class CurriculumTreeStatusGateTest {
    @Test
    void statusIsAuthorizedBeforeTheCachedTreeLookupSoAWarmCacheCannotServeReviewNodes() {
        CurriculumOperations operations = mock(CurriculumOperations.class);
        doThrow(CurriculumException.badRequest("CURRICULUM_STATUS_INVALID", "no")).when(operations)
                .requireReadableTreeStatus("in_review");
        CurriculumController controller = new CurriculumController(operations, null, null, null, null);

        assertThatThrownBy(() -> controller.tree("in_review", mock(HttpServletRequest.class)))
                .isInstanceOf(CurriculumException.class);
        verify(operations, never()).listTree(any(), any());
    }
}
