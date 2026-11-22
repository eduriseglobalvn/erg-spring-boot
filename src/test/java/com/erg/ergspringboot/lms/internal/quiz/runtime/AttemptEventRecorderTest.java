package com.erg.ergspringboot.lms.internal.quiz.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
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

        List<AttemptEvent> saved = savedBatch(repository);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getId()).hasSize(36).isEqualTo(AttemptEventRecorder.storableId("attempt-1", clientId));
        assertThat(AttemptEventRecorder.storableId("attempt-1", "short-id")).isEqualTo("short-id");
        assertThat(AttemptEventRecorder.storableId("attempt-2", clientId)).isNotEqualTo(saved.get(0).getId());
    }

    @Test
    void aReplayedBatchCostsOneLookupAndInsertsOnlyTheEventsNotStoredYet() {
        AttemptEventRepository repository = mock(AttemptEventRepository.class);
        AttemptEventRecorder recorder = new AttemptEventRecorder(repository);
        when(repository.findExistingIds(any())).thenReturn(List.of("evt-1"));

        recorder.recordClientBatch("erg", "attempt-1", "quiz-1", null, List.of(
                Map.of("id", "evt-1"), Map.of("id", "evt-2"), Map.of("id", "evt-2"), Map.of("id", "evt-3", "type", "x")));

        verify(repository, times(1)).findExistingIds(any());
        verify(repository, never()).existsById(any());
        verify(repository, never()).save(any());
        // evt-1 is stored already, the duplicate evt-2 counts once.
        assertThat(savedBatch(repository)).extracting(AttemptEvent::getId).containsExactly("evt-2", "evt-3");
    }

    @Test
    void anOversizedBatchKeepsTheNewestEventsAndNeverRejects() {
        AttemptEventRepository repository = mock(AttemptEventRepository.class);
        AttemptEventRecorder recorder = new AttemptEventRecorder(repository);
        List<Map<String, Object>> events = new java.util.ArrayList<>();
        for (int i = 0; i < AttemptEventRecorder.MAX_CLIENT_EVENTS + 20; i++) events.add(Map.of("id", "evt-" + i));

        recorder.recordClientBatch("erg", "attempt-1", "quiz-1", null, events);

        List<AttemptEvent> saved = savedBatch(repository);
        assertThat(saved).hasSize(AttemptEventRecorder.MAX_CLIENT_EVENTS);
        assertThat(saved.get(0).getId()).isEqualTo("evt-20");
        assertThat(saved.get(saved.size() - 1).getId()).isEqualTo("evt-" + (AttemptEventRecorder.MAX_CLIENT_EVENTS + 19));
    }

    @SuppressWarnings("unchecked")
    private static List<AttemptEvent> savedBatch(AttemptEventRepository repository) {
        ArgumentCaptor<Iterable<AttemptEvent>> batch = ArgumentCaptor.forClass(Iterable.class);
        verify(repository).saveAll(batch.capture());
        List<AttemptEvent> out = new java.util.ArrayList<>();
        batch.getValue().forEach(out::add);
        return out;
    }

    @Test
    void serverEventIsInsertedWithoutAReplayLookupWhileAKnownClientEventIsSkipped() {
        AttemptEventRepository repository = mock(AttemptEventRepository.class);
        AttemptEventRecorder recorder = new AttemptEventRecorder(repository);
        when(repository.existsById("evt-known")).thenReturn(true);

        recorder.record(null, "erg", "attempt-1", "quiz-1", null, "attempt_submitted", null, null, Map.of(), null);
        recorder.record("evt-known", "erg", "attempt-1", "quiz-1", null, "answer", null, null, Map.of(), null);

        ArgumentCaptor<AttemptEvent> saved = ArgumentCaptor.forClass(AttemptEvent.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo("attempt_submitted");
        assertThat(saved.getValue().isNew()).as("persisted, not merged after a SELECT").isTrue();
        verify(repository, never()).existsById(saved.getValue().getId());
    }
}
