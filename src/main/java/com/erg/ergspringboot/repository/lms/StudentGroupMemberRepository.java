package com.erg.ergspringboot.repository.lms;

import com.erg.ergspringboot.model.lms.StudentGroupMember;
import com.erg.ergspringboot.model.lms.StudentGroupMemberId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

public interface StudentGroupMemberRepository extends JpaRepository<StudentGroupMember, StudentGroupMemberId> {

    List<StudentGroupMember> findByGroupIdAndTenantId(String groupId, String tenantId);

    List<StudentGroupMember> findByTenantIdAndGroupIdIn(String tenantId, Collection<String> groupIds);

    List<StudentGroupMember> findByTenantIdAndGroupIdIn(String tenantId, Collection<String> groupIds, Pageable pageable);

    void deleteByGroupIdAndTenantId(String groupId, String tenantId);
}
