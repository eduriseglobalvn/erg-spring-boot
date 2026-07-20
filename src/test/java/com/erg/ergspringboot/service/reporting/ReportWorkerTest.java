package com.erg.ergspringboot.service.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.response.quiz.QuizReportResponses.AssignmentReportResponse;
import com.erg.ergspringboot.model.reporting.ReportJob;
import com.erg.ergspringboot.repository.reporting.ReportJobRepository;
import com.erg.ergspringboot.service.quiz.report.QuizReportService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

@ExtendWith(MockitoExtension.class)
class ReportWorkerTest {
    @Mock private ReportJobRepository repository;
    @Mock private QuizReportService quizReportService;

    @Test
    void assignmentReportIsGeneratedOutsideRequestAndMarkedCompleted() {
        ReportJob job = job();
        when(repository.findDueForUpdate(any(), any(Integer.class))).thenReturn(List.of(job));
        when(repository.findById("job-1")).thenReturn(Optional.of(job));
        when(quizReportService.assignmentReport("tenant-a", "assignment-1", 0, 100))
                .thenReturn(new AssignmentReportResponse(
                        "assignment-1", 0, 0, 0, 0, 0, 0.0, List.of(), 0, 100, 1, false));

        ReportWorker worker = new ReportWorker(repository, quizReportService, new ImmediateTransactionManager());
        assertThat(worker.runDueBatch()).isEqualTo(1);
        assertThat(job.getStatus()).isEqualTo("completed");
        assertThat(job.getProgress()).isEqualTo(100);
        assertThat(job.getResultJson()).containsEntry("assignmentId", "assignment-1");
    }

    @Test
    void oversizedAssignmentReportIsNotSilentlyMarkedCompletedWithTruncatedRows() {
        ReportJob job = job();
        when(repository.findDueForUpdate(any(), any(Integer.class))).thenReturn(List.of(job));
        when(repository.findById("job-1")).thenReturn(Optional.of(job));
        when(quizReportService.assignmentReport(any(), any(), any(Integer.class), any(Integer.class)))
                .thenAnswer(invocation -> new AssignmentReportResponse(
                        "assignment-1", 10_001, 0, 0, 10_001, 0, 0.0,
                        List.of(), invocation.getArgument(2), 100, 101, true));

        ReportWorker worker = new ReportWorker(repository, quizReportService, new ImmediateTransactionManager());
        worker.runDueBatch();

        assertThat(job.getStatus()).isNotEqualTo("completed");
        assertThat(job.getResultJson()).isNull();
        assertThat(job.getLastError()).contains("10,000");
    }

    private ReportJob job() {
        ReportJob job = new ReportJob();
        job.setId("job-1");
        job.setTenantId("tenant-a");
        job.setRequestedBy("teacher-1");
        job.setReportType(ReportJobService.ASSIGNMENT_PROGRESS);
        job.setAggregateId("assignment-1");
        job.setStatus("queued");
        job.setAvailableAt(OffsetDateTime.now());
        job.setCreatedAt(OffsetDateTime.now());
        job.setRequestJson(Map.of("assignmentId", "assignment-1"));
        return job;
    }

    private static final class ImmediateTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    }
}
