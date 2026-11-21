package com.erg.ergspringboot.lms.internal.core;

import com.erg.ergspringboot.common.request.quiz.AssignmentRequests.StudentGroupRequest;
import com.erg.ergspringboot.common.response.quiz.AssignmentResponses.StudentGroupResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.lms.persistence.StudentGroup;
import com.erg.ergspringboot.lms.persistence.StudentGroupMember;
import com.erg.ergspringboot.lms.persistence.StudentGroupMemberRepository;
import com.erg.ergspringboot.lms.persistence.StudentGroupRepository;
import com.erg.ergspringboot.lms.persistence.StudentRepository;
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

    static final int MAX_MEMBERS = 500;
    private final StudentGroupRepository studentGroupRepository;
    private final StudentGroupMemberRepository studentGroupMemberRepository;
    private final StudentRepository studentRepository;

    public StudentGroupCommands(
            StudentGroupRepository studentGroupRepository,
            StudentGroupMemberRepository studentGroupMemberRepository,
            StudentRepository studentRepository
    ) {
        this.studentGroupRepository = studentGroupRepository;
        this.studentGroupMemberRepository = studentGroupMemberRepository;
        this.studentRepository = studentRepository;
    }

    // PERF-05: stays tenant-wide (ErgTieredCache.clear() only clears the caller's tenant). A group appears in lists keyed
    // by its class, its owner, any allowed-class set containing its class, or "*", so no single scope owns it.
    @CacheEvict(cacheNames = "lmsStudentGroups", allEntries = true)
    public StudentGroupResponse createGroup(String tenantId, String userId, StudentGroupRequest request) {
        if (request == null || blankToNull(request.name()) == null) {
            throw new BadRequestException("group name is required");
        }
        List<String> studentIds = request.studentIds() == null ? List.of() : request.studentIds();
        validateMembers(tenantId, studentIds, 0);
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
            validateMembers(tenantId, request.studentIds(), group.getStudentCount() == null ? 0 : group.getStudentCount());
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

    /**
     * At most {@link #MAX_MEMBERS} distinct members (a group that already exceeds it may shrink or stay, never grow) and
     * every id must belong to the tenant; the error names how many are invalid, never the ids.
     */
    private void validateMembers(String tenantId, List<String> studentIds, int currentCount) {
        Set<String> ids = new LinkedHashSet<>(studentIds);
        if (ids.size() > MAX_MEMBERS && ids.size() > currentCount) {
            throw new BadRequestException("Một nhóm có tối đa " + MAX_MEMBERS + " học sinh.");
        }
        if (ids.isEmpty()) return;
        long found = studentRepository.countByTenantIdAndIdIn(tenantId, ids);
        if (found < ids.size()) {
            throw new BadRequestException((ids.size() - found) + " học sinh không hợp lệ hoặc không thuộc đơn vị này.");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
