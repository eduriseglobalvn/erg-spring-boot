package com.erg.ergspringboot.repository.lms;

import com.erg.ergspringboot.model.lms.StudentGroup;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface StudentGroupRepository extends JpaRepository<StudentGroup, String> {

    List<StudentGroup> findByTenantIdOrderByCreatedAtDesc(String tenantId);
    List<StudentGroup> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    List<StudentGroup> findByTenantIdAndClassIdOrderByCreatedAtDesc(String tenantId, String classId);
    List<StudentGroup> findByTenantIdAndClassIdOrderByCreatedAtDesc(String tenantId, String classId, Pageable pageable);

    List<StudentGroup> findByTenantIdAndClassIdInOrderByCreatedAtDesc(String tenantId, java.util.Collection<String> classIds);
    List<StudentGroup> findByTenantIdAndClassIdInOrderByCreatedAtDesc(String tenantId, java.util.Collection<String> classIds, Pageable pageable);

    List<StudentGroup> findByTenantIdAndOwnerUserIdOrderByCreatedAtDesc(String tenantId, String ownerUserId);
    List<StudentGroup> findByTenantIdAndOwnerUserIdOrderByCreatedAtDesc(String tenantId, String ownerUserId, Pageable pageable);

    @Query("""
            select group from StudentGroup group
            where group.tenantId = :tenantId
              and (group.ownerUserId = :ownerUserId or group.classId in :classIds)
            order by group.createdAt desc
            """)
    List<StudentGroup> findByTenantIdAndOwnerUserIdOrClassIdInOrderByCreatedAtDesc(
            @Param("tenantId") String tenantId,
            @Param("ownerUserId") String ownerUserId,
            @Param("classIds") java.util.Collection<String> classIds
    );

    @Query("""
            select group from StudentGroup group
            where group.tenantId = :tenantId
              and (group.ownerUserId = :ownerUserId or group.classId in :classIds)
            order by group.createdAt desc
            """)
    List<StudentGroup> findByTenantIdAndOwnerUserIdOrClassIdInOrderByCreatedAtDesc(
            @Param("tenantId") String tenantId,
            @Param("ownerUserId") String ownerUserId,
            @Param("classIds") java.util.Collection<String> classIds,
            Pageable pageable
    );

    Optional<StudentGroup> findByIdAndTenantId(String id, String tenantId);
}
