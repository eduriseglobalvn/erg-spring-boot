package com.erg.ergspringboot.lms.internal.readmodel;

import com.erg.ergspringboot.common.response.lms.LmsHomeworkWorkspaceResponses.*;
import com.erg.ergspringboot.model.lms.StudentGroup;
import com.erg.ergspringboot.repository.lms.StudentGroupMemberRepository;
import com.erg.ergspringboot.repository.lms.StudentGroupRepository;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** Scoped student-group projection for the LMS homework workspace. */
@Service
public class LmsStudentGroupReadUseCase {
    private final StudentGroupRepository groups;
    private final StudentGroupMemberRepository members;

    public LmsStudentGroupReadUseCase(StudentGroupRepository groups, StudentGroupMemberRepository members) {
        this.groups = groups; this.members = members;
    }

    @Cacheable(cacheNames = "lmsStudentGroups", key = "{#tenantId, #classId, #allowedClassIds, #ownerUserId}", unless = "#result == null")
    public StudentGroupWorkspaceResponse query(String tenantId, String classId,
            Collection<String> allowedClassIds, String ownerUserId) {
        String tenant = requireText(tenantId, "tenantId"); String requestedClass = blank(classId);
        Set<String> allowed = normalize(allowedClassIds); String owner = blank(ownerUserId);
        PageRequest limit = PageRequest.of(0, 201); List<StudentGroup> source;
        if (requestedClass != null) source = groups.findByTenantIdAndClassIdOrderByCreatedAtDesc(tenant, requestedClass, limit);
        else if (allowed.contains("*")) source = groups.findByTenantIdOrderByCreatedAtDesc(tenant, limit);
        else if (owner != null && !allowed.isEmpty()) source = groups.findByTenantIdAndOwnerUserIdOrClassIdInOrderByCreatedAtDesc(tenant, owner, allowed, limit);
        else if (owner != null) source = groups.findByTenantIdAndOwnerUserIdOrderByCreatedAtDesc(tenant, owner, limit);
        else if (!allowed.isEmpty()) source = groups.findByTenantIdAndClassIdInOrderByCreatedAtDesc(tenant, allowed, limit);
        else source = List.of();
        if (source == null) source = groups.findByTenantIdOrderByCreatedAtDesc(tenant, limit);
        source = source.stream().filter(group -> matches(group, requestedClass, allowed, owner)).limit(200).toList();
        List<String> ids = source.stream().map(StudentGroup::getId).toList();
        Map<String,List<String>> studentIds = ids.isEmpty() ? Map.of() : members
                .findByTenantIdAndGroupIdIn(tenant, ids, PageRequest.of(0, 10_000)).stream()
                .collect(Collectors.groupingBy(item -> item.getGroupId(),
                        Collectors.mapping(item -> item.getStudentId(), Collectors.toList())));
        return new StudentGroupWorkspaceResponse(tenant,
                source.stream().map(group -> response(group, studentIds.getOrDefault(group.getId(), List.of()))).toList());
    }

    private StudentGroupResponse response(StudentGroup group, List<String> ids) { List<String> ordered = ids.stream().filter(id -> id != null && !id.isBlank()).distinct().toList(); int count = group.getStudentCount() == null ? ordered.size() : group.getStudentCount(); return new StudentGroupResponse(group.getId(), group.getName(), group.getClassId(), group.getNote(), group.getColor(), group.getOwnerUserId(), ordered, count, group.getCreatedAt(), group.getUpdatedAt()); }
    private boolean matches(StudentGroup group, String requestedClass, Set<String> allowed, String owner) { String groupClass = blank(group.getClassId()); if (requestedClass != null && !requestedClass.equals(groupClass)) return false; if (allowed.contains("*")) return true; String groupOwner = blank(group.getOwnerUserId()); if (owner != null && owner.equals(groupOwner)) return true; return groupClass != null && allowed.contains(groupClass); }
    private Set<String> normalize(Collection<String> ids) { if (ids == null) return Set.of(); return ids.stream().map(this::blank).filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new)); }
    private String requireText(String value, String field) { String result = blank(value); if (result == null) throw new IllegalArgumentException(field + " is required."); return result; }
    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
