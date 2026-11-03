package com.erg.ergspringboot.lms.internal.core;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.model.lms.Student;
import com.erg.ergspringboot.model.lms.StudentGroupMember;
import com.erg.ergspringboot.repository.lms.StudentGroupMemberRepository;
import com.erg.ergspringboot.repository.lms.StudentRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Resolves an assignment's recipient mode (class / group / students) into a flat, request-ordered student list. */
final class AssignmentRecipientResolver {

    private static final String MODE_CLASS = "class";
    private static final String MODE_GROUP = "group";
    private static final String MODE_STUDENTS = "students";

    private final StudentRepository studentRepository;
    private final StudentGroupMemberRepository studentGroupMemberRepository;

    AssignmentRecipientResolver(StudentRepository studentRepository,
            StudentGroupMemberRepository studentGroupMemberRepository) {
        this.studentRepository = studentRepository;
        this.studentGroupMemberRepository = studentGroupMemberRepository;
    }

    List<Student> resolveRecipients(String tenantId, String mode, String classId, String groupId, List<String> studentIds) {
        return switch (mode) {
            case MODE_CLASS -> {
                String resolvedClass = blankToNull(classId);
                if (resolvedClass == null) {
                    throw new BadRequestException("classId is required for class recipient mode");
                }
                yield studentRepository.findByTenantIdAndAcademicClassIdOrderByFullNameAsc(tenantId, resolvedClass);
            }
            case MODE_GROUP -> {
                String resolvedGroup = blankToNull(groupId);
                if (resolvedGroup == null) {
                    throw new BadRequestException("groupId is required for group recipient mode");
                }
                Set<String> memberIds = new LinkedHashSet<>(
                        studentGroupMemberRepository.findByGroupIdAndTenantId(resolvedGroup, tenantId).stream()
                                .map(StudentGroupMember::getStudentId)
                                .toList());
                yield findStudentsByIds(tenantId, memberIds);
            }
            case MODE_STUDENTS -> {
                if (studentIds == null || studentIds.isEmpty()) {
                    throw new BadRequestException("studentIds is required for students recipient mode");
                }
                yield findStudentsByIds(tenantId, new LinkedHashSet<>(studentIds));
            }
            default -> throw new BadRequestException("unknown recipient mode: " + mode);
        };
    }

    private List<Student> findStudentsByIds(String tenantId, Set<String> ids) {
        Map<String, Student> byId = new LinkedHashMap<>();
        studentRepository.findByTenantIdAndIdIn(tenantId, ids).forEach(student -> byId.put(student.getId(), student));
        // Preserve request order and drop unknown/other-tenant ids.
        List<Student> ordered = new ArrayList<>();
        for (String id : ids) {
            Student student = byId.get(id);
            if (student != null) {
                ordered.add(student);
            }
        }
        return ordered;
    }

    String primaryClassId(String mode, String classId, List<Student> recipients) {
        String resolved = blankToNull(classId);
        if (resolved != null) {
            return resolved;
        }
        return recipients.stream()
                .map(Student::getAcademicClassId)
                .filter(id -> id != null && !id.isBlank())
                .findFirst()
                .orElseThrow(() -> new BadRequestException("cannot resolve a primary class for the assignment"));
    }

    String normalizeMode(String mode) {
        String value = blankToNull(mode);
        if (value == null) {
            return MODE_CLASS;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!normalized.equals(MODE_CLASS) && !normalized.equals(MODE_GROUP) && !normalized.equals(MODE_STUDENTS)) {
            throw new BadRequestException("recipientMode must be 'class', 'group' or 'students'");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
