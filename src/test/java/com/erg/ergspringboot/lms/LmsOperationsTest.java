package com.erg.ergspringboot.lms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.AttemptResponse;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.StartAttemptRequest;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.lms.LmsRuntimeService;
import org.junit.jupiter.api.Test;

class LmsOperationsTest {
    @Test
    void delegatesIdempotentAttemptStartThroughModuleBoundary() {
        LmsRuntimeService legacy = mock(LmsRuntimeService.class);
        StartAttemptRequest request = mock(StartAttemptRequest.class);
        UserAccessContext access = mock(UserAccessContext.class);
        AttemptResponse expected = mock(AttemptResponse.class);
        when(legacy.startAttempt("student-1", request, "idem-1", access)).thenReturn(expected);

        assertThat(new LmsOperations(legacy, mock(com.erg.ergspringboot.service.lms.LmsAssignmentCommandService.class))
                .startAttempt("student-1", request, "idem-1", access))
                .isSameAs(expected);
    }
}
