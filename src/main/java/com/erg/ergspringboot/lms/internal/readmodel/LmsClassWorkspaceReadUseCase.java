package com.erg.ergspringboot.lms.internal.readmodel;

import com.erg.ergspringboot.common.response.PageResponse;
import com.erg.ergspringboot.common.response.lms.LmsClassWorkspaceResponses.*;
import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.AssignmentResourceResponse;
import com.erg.ergspringboot.repository.lms.*;
import com.erg.ergspringboot.repository.lms.projection.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

/** Class workspace read projection with bounded paging and optional score/risk summaries. */
@Service
public class LmsClassWorkspaceReadUseCase {
    private final ClassroomRepository classrooms; private final StudentRepository students;
    private final AssignmentRepository assignments; private final AssignmentResourceRepository resources;
    private final AttemptRepository attempts;
    public LmsClassWorkspaceReadUseCase(ClassroomRepository classrooms, StudentRepository students,
            AssignmentRepository assignments, AssignmentResourceRepository resources, AttemptRepository attempts) {
        this.classrooms=classrooms; this.students=students; this.assignments=assignments; this.resources=resources; this.attempts=attempts;
    }
    public Page<ClassWorkspaceProjection> schoolClasses(String tenant,String school,String status,Pageable page){return classrooms.findSchoolClassWorkspaces(tenant,school,normalize(status),page);}
    public ClassWorkspaceResponse query(String tenantId,String classId,String schoolId,String studentStatus,Integer page,Integer size,String assignmentStatus,Integer assignmentPage,Integer assignmentSize,boolean includeScore,boolean includeRisk){
        String tenant=require(tenantId,"tenantId"), id=require(classId,"classId"), school=blank(schoolId);
        ClassWorkspaceProjection info=classrooms.findClassWorkspace(tenant,id,school).orElseThrow(()->new NoSuchElementException("Class workspace not found."));
        Pageable studentPage=PageRequest.of(safe(page,20),limit(size,50),Sort.by("fullName").ascending().and(Sort.by("studentCode").ascending()));
        Pageable assignmentPageable=PageRequest.of(safe(assignmentPage,20),limit(assignmentSize,50));
        Page<StudentRosterProjection> roster=students.findClassRoster(tenant,id,normalize(studentStatus),studentPage);
        Page<AssignmentCardProjection> cards=assignments.findClassAssignmentCards(tenant,id,normalize(assignmentStatus),assignmentPageable);
        ClassScoreSummaryResponse scores=includeScore?score(attempts.summarizeClassWorkspaceScores(tenant,id)):null;
        ClassRiskSummaryResponse risks=includeRisk?risk(students.summarizeClassWorkspaceRisks(tenant,id,50.0D)):null;
        return new ClassWorkspaceResponse(tenant,classInfo(info),scores,risks,studentPage(roster),assignmentPage(cards,tenant));
    }
    private ClassInfoResponse classInfo(ClassWorkspaceProjection p){return new ClassInfoResponse(p.id(),p.schoolId(),p.name(),p.grade(),p.academicYear(),p.status(),p.homeroomTeacherId(),p.studentCount()==null?0L:p.studentCount(),p.assignmentCount()==null?0L:p.assignmentCount(),p.updatedAt());}
    private PageResponse<StudentRosterItemResponse> studentPage(Page<StudentRosterProjection> p){List<StudentRosterItemResponse> items=p.getContent().stream().map(x->new StudentRosterItemResponse(x.id(),x.schoolId(),x.academicClassId(),x.studentCode(),x.fullName(),x.username(),x.authUserId(),x.email(),x.status(),x.updatedAt())).toList();return page(items,p);}
    private PageResponse<AssignmentSummaryItemResponse> assignmentPage(Page<AssignmentCardProjection> p,String tenant){Map<String,List<AssignmentResourceProjection>> by=resourceMap(tenant,p.getContent());List<AssignmentSummaryItemResponse> items=p.getContent().stream().map(x->new AssignmentSummaryItemResponse(x.id(),x.academicClassId(),x.quizId(),x.subjectId(),x.dueAt(),x.status(),x.assignedBy(),x.recipientMode(),resourceDtos(by.getOrDefault(x.id(),List.of())),x.createdAt(),x.updatedAt())).toList();return page(items,p);}
    private List<AssignmentResourceResponse> resourceDtos(List<AssignmentResourceProjection> rs){return rs.stream().sorted(Comparator.comparingInt(x->x.orderIndex()==null?0:x.orderIndex())).map(x->new AssignmentResourceResponse(x.quizId(),x.quizVersionId(),x.quizVersionLabel(),x.orderIndex()==null?0:x.orderIndex())).toList();}
    private Map<String,List<AssignmentResourceProjection>> resourceMap(String tenant,List<AssignmentCardProjection> cards){List<String> ids=cards.stream().map(AssignmentCardProjection::id).filter(x->x!=null&&!x.isBlank()).distinct().toList();if(ids.isEmpty())return Map.of();return resources.findResourceProjectionsByAssignmentIds(tenant,ids).stream().collect(Collectors.groupingBy(AssignmentResourceProjection::assignmentId));}
    private ClassScoreSummaryResponse score(ClassScoreSummaryProjection p){return p==null?new ClassScoreSummaryResponse(0,0,0,0):new ClassScoreSummaryResponse(bound(p.completedAttemptCount()),bound(p.attemptedStudentCount()),percent(p.averagePercent()),percent(p.bestPercent()));}
    private ClassRiskSummaryResponse risk(ClassRiskSummaryProjection p){return p==null?new ClassRiskSummaryResponse(0,0,0):new ClassRiskSummaryResponse(bound(p.missingOverdueStudentCount()),bound(p.lowScoreStudentCount()),bound(p.inactiveStudentCount()));}
    private int percent(Double v){return Math.max(0,Math.min(100,v==null?0:(int)Math.round(v)));} private int bound(Long v){return v==null?0:(int)Math.min(Integer.MAX_VALUE,Math.max(0,v));}
    private <T> PageResponse<T> page(List<T> items,Page<?> p){return new PageResponse<>(items,p.getNumber(),p.getSize(),p.getTotalElements(),p.getTotalPages(),p.hasNext(),p.hasPrevious());}
    private int safe(Integer page,int fallback){return page==null||page<0?0:page;} private int limit(Integer size,int max){return size==null||size<1?20:Math.min(size,max);}
    private String normalize(String v){return v==null||v.isBlank()?null:v.trim().toLowerCase();} private String blank(String v){return v==null||v.isBlank()?null:v.trim();} private String require(String v,String f){String x=blank(v);if(x==null)throw new IllegalArgumentException(f+" is required.");return x;}
}
