package com.erg.ergspringboot.lms.internal.notification;

import com.erg.ergspringboot.lms.LmsAudienceReadPort;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.lms.persistence.StudentGroupMemberRepository;
import com.erg.ergspringboot.lms.persistence.StudentRepository;
import com.erg.ergspringboot.lms.persistence.projection.ClassOptionProjection;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(value = "postgresTransactionManager", readOnly = true)
class LmsAudienceReadAdapter implements LmsAudienceReadPort {

    private final ClassroomRepository classrooms;
    private final StudentRepository students;
    private final StudentGroupMemberRepository groupMembers;

    LmsAudienceReadAdapter(
            ClassroomRepository classrooms, StudentRepository students, StudentGroupMemberRepository groupMembers) {
        this.classrooms = classrooms;
        this.students = students;
        this.groupMembers = groupMembers;
    }

    @Override
    public List<String> classIdsOf(String tenantId, Collection<String> schoolIds, Collection<String> grades) {
        Set<String> ids = new LinkedHashSet<>();
        if (!schoolIds.isEmpty()) {
            classrooms.findByTenantIdAndSchoolIdInOrderByNameAsc(tenantId, schoolIds)
                    .forEach(classroom -> ids.add(classroom.getId()));
        }
        if (!grades.isEmpty()) {
            classrooms.findByTenantIdAndGradeInOrderByNameAsc(tenantId, grades)
                    .forEach(classroom -> ids.add(classroom.getId()));
        }
        return List.copyOf(ids);
    }

    @Override
    public List<String> classIdsTaughtBy(String tenantId, String teacherUserId) {
        return classrooms.findTeacherClassOptions(tenantId, teacherUserId).stream()
                .map(ClassOptionProjection::id)
                .toList();
    }

    @Override
    public List<String> studentUserIdsInClasses(String tenantId, List<String> classIds, int limit) {
        if (classIds.isEmpty()) {
            return List.of();
        }
        Map<String, List<String>> byClass = new LinkedHashMap<>();
        students.findByTenantIdAndAcademicClassIdInAndAuthUserIdIsNotNullOrderByFullNameAsc(
                        tenantId, classIds, PageRequest.ofSize(limit))
                .forEach(student -> byClass.computeIfAbsent(student.getAcademicClassId(), key -> new java.util.ArrayList<>())
                        .add(student.getAuthUserId()));
        Set<String> ordered = new LinkedHashSet<>();
        for (String classId : new LinkedHashSet<>(classIds)) {
            ordered.addAll(byClass.getOrDefault(classId, List.of()));
        }
        return List.copyOf(ordered);
    }

    @Override
    public List<String> studentUserIdsInGroups(String tenantId, Collection<String> groupIds, int limit) {
        if (groupIds.isEmpty()) {
            return List.of();
        }
        Set<String> studentIds = new LinkedHashSet<>();
        groupMembers.findByTenantIdAndGroupIdIn(tenantId, groupIds, PageRequest.ofSize(limit))
                .forEach(member -> studentIds.add(member.getStudentId()));
        return userIdsOf(students.findByTenantIdAndIdIn(tenantId, studentIds), studentIds);
    }

    @Override
    public List<String> allStudentUserIds(String tenantId, int limit) {
        return students.findByTenantIdAndAuthUserIdIsNotNullOrderByFullNameAsc(tenantId, PageRequest.ofSize(limit))
                .stream().map(Student::getAuthUserId).toList();
    }

    @Override
    public List<String> studentUserIdsInClassesAmong(
            String tenantId, Collection<String> classIds, Collection<String> userIds) {
        if (classIds.isEmpty() || userIds.isEmpty()) {
            return List.of();
        }
        return students.findByTenantIdAndAcademicClassIdInAndAuthUserIdIn(tenantId, classIds, userIds).stream()
                .map(Student::getAuthUserId).distinct().toList();
    }

    /** Keeps the order of {@code studentIds} (group membership order). */
    private static List<String> userIdsOf(List<Student> found, Set<String> studentIds) {
        Map<String, String> userIdByStudent = new LinkedHashMap<>();
        found.stream().filter(student -> student.getAuthUserId() != null)
                .forEach(student -> userIdByStudent.put(student.getId(), student.getAuthUserId()));
        return studentIds.stream().map(userIdByStudent::get).filter(Objects::nonNull).distinct().toList();
    }
}
