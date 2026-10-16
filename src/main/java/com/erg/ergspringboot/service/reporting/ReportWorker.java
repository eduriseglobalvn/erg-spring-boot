package com.erg.ergspringboot.service.reporting;

import com.erg.ergspringboot.common.response.quiz.QuizReportResponses.AssignmentReportResponse;
import com.erg.ergspringboot.model.reporting.ReportJob;
import com.erg.ergspringboot.platform.security.TenantContext;
import com.erg.ergspringboot.repository.reporting.ReportJobRepository;
import com.erg.ergspringboot.service.quiz.report.QuizReportService;
import com.erg.ergspringboot.reporting.AssignmentProgressFilter;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Bounded worker for heavy report exports. It never runs in the HTTP transaction. */
@Service
@ConditionalOnProperty(prefix = "erg.reporting.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReportWorker {
    private static final int BATCH_SIZE = 2;
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 100;
    private static final int MAX_RESULT_ROWS = PAGE_SIZE * MAX_PAGES;
    private static final int MAX_ATTEMPTS = 5;

    private final ReportJobRepository repository;
    private final QuizReportService quizReportService;
    private final TransactionTemplate transaction;

    public ReportWorker(
            ReportJobRepository repository,
            QuizReportService quizReportService,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.quizReportService = quizReportService;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${erg.reporting.worker.fixed-delay-ms:5000}")
    public void scheduledRun() { runDueBatch(); }

    public int runDueBatch() {
        TenantContext previous = TenantContext.current();
        try {
            TenantContext.set(TenantContext.maintenance());
            List<ReportJob> jobs = transaction.execute(status -> {
                OffsetDateTime now = OffsetDateTime.now();
                List<ReportJob> due = repository.findDueForUpdate(now, BATCH_SIZE);
                OffsetDateTime lease = now.plus(Duration.ofMinutes(10));
                due.forEach(job -> {
                    job.setStatus("processing");
                    job.setLeaseUntil(lease);
                    job.setStartedAt(job.getStartedAt() == null ? now : job.getStartedAt());
                    job.setAttempts(job.getAttempts() + 1);
                });
                if (!due.isEmpty()) repository.saveAll(due);
                return due;
            });
            if (jobs == null) return 0;
            jobs.forEach(this::process);
            return jobs.size();
        } finally {
            if (previous == null) TenantContext.clear(); else TenantContext.set(previous);
        }
    }

    private void process(ReportJob job) {
        TenantContext previous = TenantContext.current();
        try {
            TenantContext.set(TenantContext.of(job.getTenantId(), job.getRequestedBy(), "report-worker"));
            if (!ReportJobService.ASSIGNMENT_PROGRESS.equals(job.getReportType())) {
                throw new IllegalArgumentException("unsupported report type: " + job.getReportType());
            }
            List<Map<String, Object>> students = new ArrayList<>();
            AssignmentReportResponse first = null;
            int page = 0;
            AssignmentProgressFilter filter = filter(job);
            while (page < MAX_PAGES) {
                AssignmentReportResponse report = defaultFilter(filter)
                        ? quizReportService.assignmentReport(job.getTenantId(), job.getAggregateId(), page, PAGE_SIZE)
                        : quizReportService.assignmentReport(job.getTenantId(), job.getAggregateId(), new AssignmentProgressFilter(
                                filter.classId(), filter.status(), filter.submittedFrom(), filter.submittedTo(),
                                filter.timezone(), page, PAGE_SIZE));
                if (first == null) first = report;
                if (report.totalRecipients() > MAX_RESULT_ROWS) {
                    throw new IllegalArgumentException("report exceeds 10,000 result rows; narrow the scope");
                }
                students.addAll(report.students().stream().map(this::toStudentMap).toList());
                updateProgress(job.getId(), Math.min(99, (page + 1) * 100 / Math.max(1, report.totalPages())));
                if (!report.hasNext()) break;
                if (page + 1 >= MAX_PAGES) {
                    throw new IllegalArgumentException("report exceeds 10,000 result rows; narrow the scope");
                }
                page++;
            }
            if (first == null) throw new IllegalStateException("report returned no page");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("assignmentId", first.assignmentId());
            result.put("totalRecipients", first.totalRecipients());
            result.put("submittedCount", first.submittedCount());
            result.put("inProgressCount", first.inProgressCount());
            result.put("notStartedCount", first.notStartedCount());
            result.put("needsReviewCount", first.needsReviewCount());
            result.put("averagePercent", first.averagePercent());
            result.put("students", students);
            if ("csv".equalsIgnoreCase(job.getFormat())) {
                result.put("contentType", "text/csv");
                result.put("content", csv(students));
                result.remove("students");
            }
            transition(job.getId(), "completed", 100, result, null);
        } catch (RuntimeException failure) {
            transition(job.getId(), job.getAttempts() >= MAX_ATTEMPTS ? "failed" : "queued", 0, null, failure.getMessage());
        } finally {
            if (previous == null) TenantContext.clear(); else TenantContext.set(previous);
        }
    }

    private void updateProgress(String id, int progress) {
        transaction.executeWithoutResult(status -> repository.findById(id).ifPresent(job -> {
            job.setProgress(progress);
            repository.save(job);
        }));
    }

    private Map<String, Object> toStudentMap(
            com.erg.ergspringboot.common.response.quiz.QuizReportResponses.StudentProgressRow row
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("studentUserId", row.studentUserId());
        result.put("studentName", row.studentName());
        result.put("status", row.status());
        result.put("answeredCount", row.answeredCount());
        result.put("totalQuestions", row.totalQuestions());
        result.put("percentComplete", row.percentComplete());
        result.put("score", row.score());
        result.put("passed", row.passed());
        result.put("submittedAt", row.submittedAt());
        return result;
    }

    private AssignmentProgressFilter filter(ReportJob job) {
        Map<String, Object> request = job.getRequestJson() == null ? Map.of() : job.getRequestJson();
        return new AssignmentProgressFilter(
                text(request.get("classId")),
                text(request.get("status")),
                offset(request.get("submittedFrom")),
                offset(request.get("submittedTo")),
                zone(text(request.get("timezone"))), 0, PAGE_SIZE);
    }

    private OffsetDateTime offset(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        return OffsetDateTime.parse(value.toString());
    }

    private ZoneId zone(String value) {
        if (value == null || value.isBlank()) return ZoneId.of("UTC");
        try { return ZoneId.of(value); }
        catch (RuntimeException ignored) { return ZoneId.of("UTC"); }
    }

    private String text(Object value) { return value == null ? null : value.toString(); }

    private boolean defaultFilter(AssignmentProgressFilter filter) {
        return filter.classId() == null && filter.status() == null
                && filter.submittedFrom() == null && filter.submittedTo() == null;
    }

    private String csv(List<Map<String, Object>> students) {
        StringBuilder csv = new StringBuilder("studentUserId,studentName,status,answeredCount,totalQuestions,percentComplete,score,passed,submittedAt\n");
        for (Map<String, Object> student : students) {
            csv.append(csvCell(student.get("studentUserId"))).append(',')
                    .append(csvCell(student.get("studentName"))).append(',')
                    .append(csvCell(student.get("status"))).append(',')
                    .append(csvCell(student.get("answeredCount"))).append(',')
                    .append(csvCell(student.get("totalQuestions"))).append(',')
                    .append(csvCell(student.get("percentComplete"))).append(',')
                    .append(csvCell(student.get("score"))).append(',')
                    .append(csvCell(student.get("passed"))).append(',')
                    .append(csvCell(student.get("submittedAt"))).append('\n');
        }
        return csv.toString();
    }

    private String csvCell(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private void transition(String id, String state, int progress, Map<String, Object> result, String error) {
        transaction.executeWithoutResult(status -> repository.findById(id).ifPresent(job -> {
            job.setStatus(state);
            job.setProgress(progress);
            job.setResultJson(result);
            job.setLastError(error == null ? null : error.substring(0, Math.min(1000, error.length())));
            job.setLeaseUntil(null);
            if ("completed".equals(state) || "failed".equals(state)) job.setCompletedAt(OffsetDateTime.now());
            else job.setAvailableAt(OffsetDateTime.now().plusSeconds(Math.min(300, 1L << Math.min(8, job.getAttempts()))));
            repository.save(job);
        }));
    }
}
