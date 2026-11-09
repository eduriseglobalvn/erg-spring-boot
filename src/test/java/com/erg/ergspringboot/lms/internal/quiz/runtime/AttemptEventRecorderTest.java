package com.erg.ergspringboot.lms.internal.quiz.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.erg.ergspringboot.lcms.persistence.quiz.runtime.AttemptEvent;
import com.erg.ergspringboot.lcms.persistence.quiz.runtime.AttemptEventRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AttemptEventRecorderTest {

    @Test
    void clientEventIdLongerThanTheColumnIsStoredUnderAStableUuid() {
        AttemptEventRepository repository = mock(AttemptEventRepository.class);
        AttemptEventRecorder recorder = new AttemptEventRecorder(repository);
        String clientId = "evt_0b8f4c1e-6f0a-4f43-9d55-2f2d6a8c1b11";

        recorder.recordClientBatch("erg", "attempt-1", "quiz-1", null, List.of(Map.of("id", clientId, "type", "answer_graded")));

        ArgumentCaptor<AttemptEvent> saved = ArgumentCaptor.forClass(AttemptEvent.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getId()).hasSize(36).isEqualTo(AttemptEventRecorder.storableId("attempt-1", clientId));
        assertThat(AttemptEventRecorder.storableId("attempt-1", "short-id")).isEqualTo("short-id");
        assertThat(AttemptEventRecorder.storableId("attempt-2", clientId)).isNotEqualTo(saved.getValue().getId());
    }
}
