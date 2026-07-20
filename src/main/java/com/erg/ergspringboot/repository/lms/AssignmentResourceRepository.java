package com.erg.ergspringboot.repository.lms;

import com.erg.ergspringboot.model.lms.AssignmentResource;
import com.erg.ergspringboot.model.lms.AssignmentResourceId;
import com.erg.ergspringboot.repository.lms.projection.AssignmentResourceProjection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssignmentResourceRepository extends JpaRepository<AssignmentResource, AssignmentResourceId> {

    List<AssignmentResource> findByAssignmentIdOrderByOrderIndexAsc(String assignmentId);

    @Query("""
            select new com.erg.ergspringboot.repository.lms.projection.AssignmentResourceProjection(
                resource.assignmentId,
                resource.quizId,
                resource.quizVersionId,
                resource.quizVersionLabel,
                resource.orderIndex
            )
            from AssignmentResource resource
            where resource.tenantId = :tenantId
              and resource.assignmentId = :assignmentId
            order by resource.orderIndex asc, resource.quizId asc
            """)
    List<AssignmentResourceProjection> findResourceProjections(
            @Param("tenantId") String tenantId,
            @Param("assignmentId") String assignmentId
    );

    @Query("""
            select new com.erg.ergspringboot.repository.lms.projection.AssignmentResourceProjection(
                resource.assignmentId,
                resource.quizId,
                resource.quizVersionId,
                resource.quizVersionLabel,
                resource.orderIndex
            )
            from AssignmentResource resource
            where resource.tenantId = :tenantId
              and resource.assignmentId in :assignmentIds
            order by resource.assignmentId asc, resource.orderIndex asc, resource.quizId asc
            """)
    List<AssignmentResourceProjection> findResourceProjectionsByAssignmentIds(
            @Param("tenantId") String tenantId,
            @Param("assignmentIds") List<String> assignmentIds
    );

    List<AssignmentResource> findByTenantIdAndQuizId(String tenantId, String quizId);

    void deleteByAssignmentId(String assignmentId);
}
