package com.erg.ergspringboot.lms.internal.readmodel;

import com.erg.ergspringboot.common.response.PageResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.*;
import com.erg.ergspringboot.repository.lms.*;
import com.erg.ergspringboot.repository.lms.projection.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

/** Assignment progress projection boundary. */
@Service
public class LmsAssignmentProgressQueryUseCase {
    private final AttemptRepository attempts;
    private final AssignmentRepository assignments;
    private final AssignmentResourceRepository resources;
    public LmsAssignmentProgressQueryUseCase(AttemptRepository attempts, AssignmentRepository assignments,
            AssignmentResourceRepository resources) { this.attempts = attempts; this.assignments = assignments; this.resources = resources; }
    public AssignmentProgressProjection summary(String tenantId, String assignmentId) {
        return attempts.summarizeAssignmentProgress(tenantId, assignmentId);
    }

    @Cacheable(cacheNames = "lmsAssignmentProgress", key = "{#tenantId, #assignmentId, #attemptStatus, #page, #size}", unless = "#result == null")
    public AssignmentProgressWorkspaceResponse workspace(String tenantId, String assignmentId,
            String attemptStatus, Integer page, Integer size) {
        String tenant = require(tenantId, "tenantId"); String id = require(assignmentId, "assignmentId");
        AssignmentDetailProjection assignment = assignments.findAssignmentDetail(tenant, id)
                .orElseThrow(() -> new NoSuchElementException("Assignment progress workspace not found."));
        Pageable attemptPage = PageRequest.of(page == null || page < 0 ? 0 : page,
                size == null || size < 1 ? 20 : Math.min(size, 50), Sort.by("updatedAt").descending());
        Pageable recipientPage = PageRequest.of(0, 50);
        AssignmentProgressProjection summary = attempts.summarizeAssignmentProgress(tenant, id);
        List<AssignmentResourceProjection> assignmentResources = resources.findResourceProjections(tenant, id);
        Page<AttemptSummaryProjection> attemptItems = attempts.findAssignmentAttemptSummaries(tenant, id,
                attemptStatus == null || attemptStatus.isBlank() ? null : attemptStatus.trim().toLowerCase(), attemptPage);
        Page<RecipientProgressProjection> recipients = assignments.findAssignmentRecipients(tenant, id, recipientPage);
        Page<MissingLateStudentProjection> missing = assignments.findAssignmentMissingLateStudents(tenant, id, recipientPage);
        List<String> recipientIds = recipients.getContent().stream().map(RecipientProgressProjection::studentId).toList();
        return new AssignmentProgressWorkspaceResponse(tenant, detail(assignment, assignmentResources), summary(summary),
                attemptPage(attemptItems), recipientPage(recipients, latest(tenant, id, recipientIds),
                        best(tenant, id, recipientIds), missingMap(missing)), missingPage(missing));
    }

    private AssignmentDetailResponse detail(AssignmentDetailProjection p, List<AssignmentResourceProjection> source) { return new AssignmentDetailResponse(p.id(), p.academicClassId(), p.quizId(), p.subjectId(), p.dueAt(), p.status(), p.assignedBy(), p.recipientMode(), source.stream().sorted(Comparator.comparingInt(r -> r.orderIndex() == null ? 0 : r.orderIndex())).map(r -> new AssignmentResourceResponse(r.quizId(), r.quizVersionId(), r.quizVersionLabel(), r.orderIndex() == null ? 0 : r.orderIndex())).toList(), p.createdAt(), p.updatedAt()); }
    private AssignmentProgressSummaryResponse summary(AssignmentProgressProjection p) { return p == null ? new AssignmentProgressSummaryResponse(0,0,0) : new AssignmentProgressSummaryResponse(p.submittedCount(), p.inProgressCount(), p.needsReviewCount()); }
    private PageResponse<AssignmentAttemptSummaryResponse> attemptPage(Page<AttemptSummaryProjection> p) { List<AssignmentAttemptSummaryResponse> items = p.getContent().stream().map(x -> new AssignmentAttemptSummaryResponse(x.id(), x.assignmentId(), x.quizId(), x.studentId(), x.status(), x.score(), x.maxScore(), percent(x.percent()), x.passed(), x.startedAt(), x.submittedAt(), x.updatedAt())).toList(); return page(items,p); }
    private PageResponse<RecipientProgressResponse> recipientPage(Page<RecipientProgressProjection> p, Map<String,AttemptScoreProjection> latest, Map<String,AttemptScoreProjection> best, Map<String,MissingLateStudentProjection> missing) { List<RecipientProgressResponse> items = p.getContent().stream().map(x -> { MissingLateStudentProjection m=missing.get(x.studentId()); return new RecipientProgressResponse(x.studentId(), x.schoolId(), x.academicClassId(), x.studentCode(), x.fullName(), x.username(), x.authUserId(), x.email(), x.status(), m!=null&&Boolean.TRUE.equals(m.missing()), m!=null&&Boolean.TRUE.equals(m.late()), score(latest.get(x.studentId())), score(best.get(x.studentId())), x.updatedAt()); }).toList(); return page(items,p); }
    private PageResponse<MissingLateStudentResponse> missingPage(Page<MissingLateStudentProjection> p) { List<MissingLateStudentResponse> items=p.getContent().stream().map(x -> new MissingLateStudentResponse(x.studentId(),x.schoolId(),x.academicClassId(),x.studentCode(),x.fullName(),x.username(),x.authUserId(),x.email(),x.status(),x.dueAt(),Boolean.TRUE.equals(x.missing()),Boolean.TRUE.equals(x.late()),x.updatedAt())).toList(); return page(items,p); }
    private AssignmentAttemptScoreResponse score(AttemptScoreProjection x) { return x==null?null:new AssignmentAttemptScoreResponse(x.id(),x.assignmentId(),x.quizId(),x.studentId(),x.status(),x.score(),x.maxScore(),percent(x.percent()),x.passed(),x.startedAt(),x.submittedAt(),x.updatedAt()); }
    private Map<String,AttemptScoreProjection> latest(String t,String a,List<String> ids){if(ids.isEmpty())return Map.of();return attempts.findLatestAttemptScoresForRecipients(t,a,ids).stream().collect(Collectors.toMap(AttemptScoreProjection::studentId,Function.identity(),this::latest));}
    private Map<String,AttemptScoreProjection> best(String t,String a,List<String> ids){if(ids.isEmpty())return Map.of();return attempts.findBestAttemptScoresForRecipients(t,a,ids).stream().collect(Collectors.toMap(AttemptScoreProjection::studentId,Function.identity(),this::best));}
    private Map<String,MissingLateStudentProjection> missingMap(Page<MissingLateStudentProjection> p){return p.getContent().stream().collect(Collectors.toMap(MissingLateStudentProjection::studentId,Function.identity(),(a,b)->a));}
    private AttemptScoreProjection latest(AttemptScoreProjection a,AttemptScoreProjection b){if(a.updatedAt()==null)return b;if(b.updatedAt()==null)return a;int c=a.updatedAt().compareTo(b.updatedAt());return c==0?(a.id().compareTo(b.id())>=0?a:b):(c>0?a:b);}
    private AttemptScoreProjection best(AttemptScoreProjection a,AttemptScoreProjection b){int c=Integer.compare(percent(a.percent()),percent(b.percent()));return c==0?latest(a,b):(c>0?a:b);}
    private int percent(Double value){return Math.max(0,Math.min(100,value==null?0:(int)Math.round(value)));}
    private <T> PageResponse<T> page(List<T> items,Page<?> p){return new PageResponse<>(items,p.getNumber(),p.getSize(),p.getTotalElements(),p.getTotalPages(),p.hasNext(),p.hasPrevious());}
    private String require(String value,String field){String v=value==null||value.isBlank()?null:value.trim();if(v==null)throw new IllegalArgumentException(field+" is required.");return v;}
}
