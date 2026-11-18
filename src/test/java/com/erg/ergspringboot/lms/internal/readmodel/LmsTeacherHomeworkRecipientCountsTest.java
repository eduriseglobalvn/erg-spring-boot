package com.erg.ergspringboot.lms.internal.readmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.lms.LmsAssignmentQueryPort;
import com.erg.ergspringboot.lms.persistence.projection.AssignmentCountProjection;
import java.util.List;
import org.junit.jupiter.api.Test;

class LmsTeacherHomeworkRecipientCountsTest {
    @Test
    void savedRecipientRowsWinOverClassRoster() {
        LmsAssignmentQueryPort port = mock(LmsAssignmentQueryPort.class);
        List<String> ids = List.of("a1", "a2");
        when(port.countClassRosterRecipients("t", ids)).thenReturn(List.of(
                new AssignmentCountProjection("a1", 30L), new AssignmentCountProjection("a2", 28L)));
        when(port.countSavedRecipients("t", ids)).thenReturn(List.of(new AssignmentCountProjection("a2", 5L)));

        var counts = new LmsTeacherHomeworkReadUseCase(port, null, null, null, null).recipientCounts("t", ids);

        assertThat(counts).containsEntry("a1", 30).containsEntry("a2", 5);
    }
}
