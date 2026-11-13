package com.erg.ergspringboot.lms.internal.core;

import com.erg.ergspringboot.common.request.quiz.AssignmentRequests.StudentGroupRequest;
import com.erg.ergspringboot.common.response.quiz.AssignmentResponses.StudentGroupResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.lms.persistence.StudentGroup;
import com.erg.ergspringboot.lms.persistence.StudentGroupMember;
import com.erg.ergspringboot.lms.persistence.StudentGroupMemberRepository;
import com.erg.ergspringboot.lms.persistence.StudentGroupRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teacher-owned student group writes (spec §4.8). A group is a named, reusable recipient set that
 * {@link LmsAssignmentCommandService} resolves in {@code group} recipient mode.
 */
@Service
@Transactional(value = "postgresTransactionManager")
public class StudentGroupCommands {

    private final StudentGroupRepository studentGroupRepository;
    private final StudentGroupMemberRepository studentGroupMemberRepository;

    public StudentGroupCommands(
            StudentGroupRepository studentGroupRepository,
            StudentGroupMemberRepository studentGroupMemberRepository
    ) {
        this.studentGroupRepository = studentGroupRepository;
        this.studentGroupMemberRepository = studentGroupMemberRepository;
    }

    // PERF-05: stays tenant-wide (ErgTieredCache.clear() only clears the caller's tenant). A group appears in lists keyed
    // by its class, its owner, any allowed-class set containing its class, or "*", so no single scope owns it.
    @CacheEvict(cacheNames = "lmsStudentGroups", allEntries = true)
    public StudentGroupResponse createGroup(String tenantId, String userId, StudentGroupRequest request) {
        if (request == null || blankToNull(request.name()) == null) {
            throw new BadRequestException("group name is required");
        }
        List<String> studentIds = request.studentIds() == null ? List.of() : request.studentIds();
        StudentGroup group = new StudentGroup();
        group.setTenantId(tenantId);
        group.setName(request.name().trim());
        group.setClassId(blankToNull(request.classId()));
        group.setNote(blankToNull(request.note()));
        group.setColor(blankToNull(request.color()));
        group.setOwnerUserId(userId);
        group.setStudentCount(studentIds.size());
        StudentGroup saved = studentGroupRepository.save(group);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<StudentGroupMember> members = new ArrayList<>();
        for (String studentId : new LinkedHashSet<>(studentIds)) {
            StudentGroupMember member = new StudentGroupMember();
            member.setGroupId(saved.getId());
            member.setStudentId(studentId);
            member.setTenantId(tenantId);
            member.setCreatedAt(now);
            members.add(member);
        }
        if (!members.isEmpty()) {
            studentGroupMemberRepository.saveAll(members);
        }
        return new StudentGroupResponse(
                saved.getId(),
                saved.getName(),
                saved.getClassId(),
                saved.getNote(),
                saved.getColor(),
                studentIds.size()
        );
    }

    @CacheEvict(cacheNames = "lmsStudentGroups", allEntries = true)
    public StudentGroupResponse updateGroup(String tenantId, String groupId, StudentGroupRequest request) {
        StudentGroup group = studentGroupRepository.findByIdAndTenantId(groupId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("student group not found"));
        if (request != null && blankToNull(request.name()) != null) {
            group.setName(request.name().trim());
        }
        if (request != null && request.classId() != null) {
            group.setClassId(blankToNull(request.classId()));
        }
        if (request != null && request.note() != null) {
            group.setNote(blankToNull(request.note()));
        }
        if (request != null && request.color() != null) {
            group.setColor(blankToNull(request.color()));
        }
        // Replace membership wholesale when studentIds is supplied (null = leave unchanged).
        if (request != null && request.studentIds() != null) {
            studentGroupMemberRepository.deleteByGroupIdAndTenantId(groupId, tenantId);
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            Set<String> ids = new LinkedHashSet<>(request.studentIds());
            List<StudentGroupMember> members = new ArrayList<>(ids.size());
            for (String studentId : ids) {
                StudentGroupMember member = new StudentGroupMember();
                member.setGroupId(groupId);
                member.setStudentId(studentId);
                member.setTenantId(tenantId);
                member.setCreatedAt(now);
                members.add(member);
            }
            if (!members.isEmpty()) {
                studentGroupMemberRepository.saveAll(members);
            }
            group.setStudentCount(ids.size());
        }
        StudentGroup saved = studentGroupRepository.save(group);
        return new StudentGroupResponse(
                saved.getId(),
                saved.getName(),
                saved.getClassId(),
                saved.getNote(),
                saved.getColor(),
                saved.getStudentCount() == null ? 0 : saved.getStudentCount()
        );
    }

    @CacheEvict(cacheNames = "lmsStudentGroups", allEntries = true)
    public void deleteGroup(String tenantId, String groupId) {
        StudentGroup group = studentGroupRepository.findByIdAndTenantId(groupId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("student group not found"));
        studentGroupMemberRepository.deleteByGroupIdAndTenantId(groupId, tenantId);
        studentGroupRepository.delete(group);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
