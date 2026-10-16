package com.erg.ergspringboot.lms.internal.readmodel;

import com.erg.ergspringboot.common.response.PageResponse;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.*;
import com.erg.ergspringboot.lms.LmsAssignmentQueryPort;
import com.erg.ergspringboot.lms.LmsAttemptQueryPort;
import com.erg.ergspringboot.lms.LmsStatePolicy;
import com.erg.ergspringboot.repository.lms.AssignmentRepository;
import com.erg.ergspringboot.repository.lms.AssignmentResourceRepository;
import com.erg.ergspringboot.repository.lms.AttemptRepository;
import com.erg.ergspringboot.repository.lms.ClassroomRepository;
import com.erg.ergspringboot.repository.lms.StudentRepository;
import com.erg.ergspringboot.repository.lms.projection.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

/** Teacher homework dashboard projection; all JDBC reads stay on the transaction-bound thread. */
@Service
public class LmsTeacherHomeworkReadUseCase {
    private final LmsAssignmentQueryPort assignments; private final AssignmentResourceRepository resources;
    private final ClassroomRepository classrooms; private final LmsAttemptQueryPort attempts; private final StudentRepository students;
    @Autowired
    public LmsTeacherHomeworkReadUseCase(LmsAssignmentQueryPort assignments, AssignmentResourceRepository resources,
            ClassroomRepository classrooms, LmsAttemptQueryPort attempts, StudentRepository students){this.assignments=assignments;this.resources=resources;this.classrooms=classrooms;this.attempts=attempts;this.students=students;}
    /** Compatibility constructor retained for direct legacy callers. */
    @Deprecated
    public LmsTeacherHomeworkReadUseCase(AssignmentRepository assignments, AssignmentResourceRepository resources,
            ClassroomRepository classrooms, AttemptRepository attempts, StudentRepository students){
        this(new JpaLmsAssignmentQueryAdapter(assignments), resources, classrooms,
                new JpaLmsAttemptQueryAdapter(attempts), students);
    }
    @Cacheable(cacheNames="lmsHomeworkWorkspace",key="{#tenantId, #teacherId, #status, #page, #size}",unless="#result == null")
    public TeacherHomeworkWorkspaceResponse query(String tenantId,String teacherId,String status,Integer page,Integer size){
        String tenant=require(tenantId,"tenantId"),teacher=require(teacherId,"teacherId");Pageable pageable=PageRequest.of(page==null||page<0?0:page,size==null||size<1?20:Math.min(size,50));String normalized=LmsStatePolicy.assignmentFilter(status);
        Page<AssignmentCardProjection> cards=assignments.findTeacherAssignmentCards(tenant,teacher,normalized,pageable);
        TeacherHomeworkSummaryProjection summary=assignments.summarizeTeacherHomeworkWorkspace(tenant,teacher);
        List<ClassOptionProjection> options=classrooms.findTeacherClassOptions(tenant,teacher);
        Page<PendingReviewProjection> pending=attempts.findTeacherPendingReviews(tenant,teacher,pageable);
        Page<OverdueStudentProjection> overdue=students.findTeacherOverdueStudents(tenant,teacher,pageable);
        return new TeacherHomeworkWorkspaceResponse(tenant,teacher,summary(summary),options(options),cardPage(cards,tenant),pendingPage(pending),overduePage(overdue));
    }
    private TeacherHomeworkSummaryResponse summary(TeacherHomeworkSummaryProjection p){return p==null?new TeacherHomeworkSummaryResponse(0,0,0):new TeacherHomeworkSummaryResponse(bound(p.activeAssignmentCount()),bound(p.overdueAssignmentCount()),bound(p.pendingReviewAssignmentCount()));}
    private List<ClassOptionResponse> options(List<ClassOptionProjection> ps){return ps.stream().map(p->new ClassOptionResponse(p.id(),p.schoolId(),p.name(),p.grade(),p.academicYear(),p.status())).toList();}
    private PageResponse<HomeworkAssignmentCardResponse> cardPage(Page<AssignmentCardProjection> p,String tenant){Map<String,List<AssignmentResourceProjection>> by=resourceMap(tenant,p.getContent());List<HomeworkAssignmentCardResponse> items=p.getContent().stream().map(x->new HomeworkAssignmentCardResponse(x.id(),x.academicClassId(),x.quizId(),x.subjectId(),x.dueAt(),x.status(),x.assignedBy(),x.recipientMode(),resourceDtos(by.getOrDefault(x.id(),List.of())),x.createdAt(),x.updatedAt())).toList();return page(items,p);}
    private List<AssignmentResourceResponse> resourceDtos(List<AssignmentResourceProjection> rs){return rs.stream().sorted(Comparator.comparingInt(x->x.orderIndex()==null?0:x.orderIndex())).map(x->new AssignmentResourceResponse(x.quizId(),x.quizVersionId(),x.quizVersionLabel(),x.orderIndex()==null?0:x.orderIndex())).toList();}
    private Map<String,List<AssignmentResourceProjection>> resourceMap(String tenant,List<AssignmentCardProjection> cards){List<String> ids=cards.stream().map(AssignmentCardProjection::id).filter(x->x!=null&&!x.isBlank()).distinct().toList();if(ids.isEmpty())return Map.of();return resources.findResourceProjectionsByAssignmentIds(tenant,ids).stream().collect(Collectors.groupingBy(AssignmentResourceProjection::assignmentId));}
    private PageResponse<PendingReviewResponse> pendingPage(Page<PendingReviewProjection> p){List<PendingReviewResponse> items=p.getContent().stream().map(x->new PendingReviewResponse(x.attemptId(),x.assignmentId(),x.academicClassId(),x.quizId(),x.subjectId(),x.studentId(),x.studentCode(),x.fullName(),x.status(),x.score(),x.maxScore(),percent(x.percent()),x.submittedAt(),x.updatedAt())).toList();return page(items,p);}
    private PageResponse<OverdueStudentResponse> overduePage(Page<OverdueStudentProjection> p){List<OverdueStudentResponse> items=p.getContent().stream().map(x->new OverdueStudentResponse(x.assignmentId(),x.academicClassId(),x.quizId(),x.subjectId(),x.dueAt(),x.studentId(),x.studentCode(),x.fullName(),x.status(),x.updatedAt())).toList();return page(items,p);}
    private <T> PageResponse<T> page(List<T> items,Page<?> p){return new PageResponse<>(items,p.getNumber(),p.getSize(),p.getTotalElements(),p.getTotalPages(),p.hasNext(),p.hasPrevious());}
    private int percent(Double v){return Math.max(0,Math.min(100,v==null?0:(int)Math.round(v)));}private int bound(Long v){return v==null?0:(int)Math.min(Integer.MAX_VALUE,Math.max(0,v));}
    private String require(String v,String f){String x=v==null||v.isBlank()?null:v.trim();if(x==null)throw new IllegalArgumentException(f+" is required.");return x;}
}
