package com.erg.ergspringboot.reporting.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.model.reporting.ReportJob;
import com.erg.ergspringboot.repository.reporting.ReportJobRepository;
import com.erg.ergspringboot.reporting.ReportExportOptions;
import com.erg.ergspringboot.reporting.AssignmentProgressFilter;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReportJobUseCaseTest {
    @Test
    void enqueueCapturesFormatExpiryAndReplaysSameRequest() {
        ReportJobRepository repository = mock(ReportJobRepository.class);
        when(repository.findByTenantIdAndIdempotencyKey("tenant-a", "key-1"))
                .thenReturn(Optional.empty(), Optional.of(existing("key-1")));
        when(repository.save(any(ReportJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ReportJobUseCase useCase = new ReportJobUseCase(repository);
        ReportExportOptions options = new ReportExportOptions("csv",
                new AssignmentProgressFilter("class-1", "submitted", null, null, ZoneId.of("UTC"), 0, 100));

        var created = useCase.enqueueAssignmentProgress("tenant-a", "teacher-1", "assignment-1", "key-1", options);
        assertThat(created.format()).isEqualTo("csv");
        assertThat(created.expiresAt()).isNotNull();
        assertThat(created.downloadPath()).contains("/api/v1/reports/jobs/").endsWith("/download");

        ReportJob replay = existing("key-1");
        replay.setRequestJson(Map.of("assignmentId", "assignment-1", "format", "csv", "classId", "class-1",
                "status", "submitted", "timezone", "UTC", "page", 0, "size", 100));
        when(repository.findByTenantIdAndIdempotencyKey("tenant-a", "key-1")).thenReturn(Optional.of(replay));
        assertThat(useCase.enqueueAssignmentProgress("tenant-a", "teacher-1", "assignment-1", "key-1", options).id())
                .isEqualTo("job-key-1");
    }

    @Test
    void changedFormatForSameKeyFailsClosed() {
        ReportJobRepository repository = mock(ReportJobRepository.class);
        ReportJob existing = existing("key-1");
        existing.setRequestJson(Map.of("assignmentId", "assignment-1", "format", "json", "timezone", "UTC", "page", 0, "size", 100));
        when(repository.findByTenantIdAndIdempotencyKey("tenant-a", "key-1")).thenReturn(Optional.of(existing));
        ReportJobUseCase useCase = new ReportJobUseCase(repository);

        assertThatThrownBy(() -> useCase.enqueueAssignmentProgress("tenant-a", "teacher-1", "assignment-1", "key-1",
                new ReportExportOptions("csv", null))).isInstanceOf(ConflictException.class);
    }

    private ReportJob existing(String key) {
        ReportJob job = new ReportJob();
        job.setId("job-" + key);
        job.setTenantId("tenant-a");
        job.setAggregateId("assignment-1");
        job.setIdempotencyKey(key);
        job.setFormat("csv");
        job.setRequestJson(Map.of("assignmentId", "assignment-1"));
        return job;
    }
}
