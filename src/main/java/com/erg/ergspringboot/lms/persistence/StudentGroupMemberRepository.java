package com.erg.ergspringboot.lms.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

public interface StudentGroupMemberRepository extends JpaRepository<StudentGroupMember, StudentGroupMemberId> {

    List<StudentGroupMember> findByGroupIdAndTenantId(String groupId, String tenantId);

    List<StudentGroupMember> findByTenantIdAndGroupIdIn(String tenantId, Collection<String> groupIds);

    List<StudentGroupMember> findByTenantIdAndGroupIdIn(String tenantId, Collection<String> groupIds, Pageable pageable);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("delete from StudentGroupMember m where m.groupId = :groupId and m.tenantId = :tenantId")
    void deleteByGroupIdAndTenantId(@org.springframework.data.repository.query.Param("groupId") String groupId,
            @org.springframework.data.repository.query.Param("tenantId") String tenantId);
}
