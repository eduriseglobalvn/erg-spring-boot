package com.erg.ergspringboot.lms.internal.notification;

import com.erg.ergspringboot.lms.LmsStudentLookupPort;
import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.lms.persistence.StudentRepository;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(value = "postgresTransactionManager", readOnly = true)
class LmsStudentLookupAdapter implements LmsStudentLookupPort {

    private final StudentRepository students;
    private final ClassroomRepository classrooms;

    LmsStudentLookupAdapter(StudentRepository students, ClassroomRepository classrooms) {
        this.students = students;
        this.classrooms = classrooms;
    }

    @Override
    public List<StudentCard> findByKeys(
            String tenantId, Collection<String> classIds,
            Collection<String> codes, Collection<String> emails, Collection<String> phones) {
        if ((classIds != null && classIds.isEmpty())
                || (codes.isEmpty() && emails.isEmpty() && phones.isEmpty())) {
            return List.of();
        }
        // Empty IN lists are not portable in JPQL, so a placeholder stands in for an unused key kind.
        List<Student> found = students.findAudienceStudentsByKeys(
                tenantId, orNone(codes), orNone(emails), orNone(phones));
        return cards(tenantId, classIds == null ? found : found.stream()
                .filter(student -> classIds.contains(student.getAcademicClassId())).toList());
    }

    @Override
    public List<StudentCard> listStudents(String tenantId, Collection<String> classIds, int limit) {
        if (classIds != null && classIds.isEmpty()) {
            return List.of();
        }
        List<Student> found = classIds == null
                ? students.findAudienceStudents(tenantId, PageRequest.ofSize(limit))
                : students.findAudienceStudentsInClasses(tenantId, classIds, PageRequest.ofSize(limit));
        return cards(tenantId, found);
    }

    private List<StudentCard> cards(String tenantId, List<Student> found) {
        Set<String> classIds = new HashSet<>();
        found.forEach(student -> classIds.add(student.getAcademicClassId()));
        classIds.remove(null);
        Map<String, String> names = classIds.isEmpty() ? Map.of() : classrooms.findAllById(classIds).stream()
                .filter(classroom -> tenantId.equals(classroom.getTenantId()))
                .collect(Collectors.toMap(Classroom::getId, Classroom::getName, (a, b) -> a));
        return found.stream()
                .map(student -> new StudentCard(student.getAuthUserId(), student.getFullName(),
                        student.getStudentCode(), student.getEmail(), student.getPhone(),
                        names.get(student.getAcademicClassId())))
                .toList();
    }

    private static Collection<String> orNone(Collection<String> keys) {
        return keys.isEmpty() ? List.of("\u0001") : keys;
    }
}
