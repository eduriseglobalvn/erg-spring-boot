package com.erg.ergspringboot.lms.internal.readmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.lms.LmsStatePolicy;
import com.erg.ergspringboot.lms.persistence.AssignmentProgressCountRepository;
import com.erg.ergspringboot.lms.persistence.AssignmentRecipientProgressRepository;
import com.erg.ergspringboot.lms.persistence.AssignmentRecipientRepository;
import com.erg.ergspringboot.lms.persistence.AssignmentRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/** H4: the recipient lists are driven from the saved rows, or (BUG-D-003) from the class roster when the assignment has none. */
class JpaLmsAssignmentQueryAdapterTest {
    private final AssignmentRecipientRepository rows = mock(AssignmentRecipientRepository.class);
    private final AssignmentRecipientProgressRepository progress = mock(AssignmentRecipientProgressRepository.class);
    private final JpaLmsAssignmentQueryAdapter adapter = new JpaLmsAssignmentQueryAdapter(
            mock(AssignmentRepository.class), mock(AssignmentProgressCountRepository.class), rows, progress);
    private final PageRequest page = PageRequest.of(0, 20);

    @Test
    void anAssignmentWithSavedRecipientRowsListsThoseRows() {
        when(rows.existsByTenantIdAndAssignmentId("t", "a")).thenReturn(true);
        when(progress.recipientsFromRows("t", "a", page)).thenReturn(new PageImpl<>(List.of()));

        adapter.findAssignmentRecipients("t", "a", page);

        verify(progress).recipientsFromRows("t", "a", page);
        verify(progress, never()).recipientsFromRoster("t", "a", page);
    }

    @Test
    void aLegacyAssignmentWithoutRecipientRowsFallsBackToTheClassRoster() {
        when(rows.existsByTenantIdAndAssignmentId("t", "a")).thenReturn(false);
        when(progress.recipientsFromRoster("t", "a", page)).thenReturn(new PageImpl<>(List.of()));
        when(progress.missingLateFromRoster("t", "a", page, LmsStatePolicy.completedAttemptStates())).thenReturn(new PageImpl<>(List.of()));

        adapter.findAssignmentRecipients("t", "a", page);
        adapter.findAssignmentMissingLateStudents("t", "a", page);

        verify(progress).recipientsFromRoster("t", "a", page);
        verify(progress).missingLateFromRoster("t", "a", page, LmsStatePolicy.completedAttemptStates());
        verify(progress, never()).recipientsFromRows("t", "a", page);
        verify(progress, never()).missingLateFromRows("t", "a", page, LmsStatePolicy.completedAttemptStates());
    }

    @Test
    void theFlagsOfARecipientsPageAreQueriedByThePageStudentsAndSkippedWhenEmpty() {
        assertThat(adapter.findMissingLateOf("t", "a", List.of())).isEmpty();
        adapter.findMissingLateOf("t", "a", List.of("s1"));

        verify(progress).missingLateOf("t", "a", List.of("s1"), LmsStatePolicy.completedAttemptStates());
    }
}
