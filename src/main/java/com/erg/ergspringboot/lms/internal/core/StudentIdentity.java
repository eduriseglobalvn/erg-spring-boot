package com.erg.ergspringboot.lms.internal.core;

import com.erg.ergspringboot.common.request.lms.LmsOperationalRequests.CreateStudentRequest;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.lms.persistence.StudentRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Who a new student row is when the sheet carries no student code, and the code a new student gets then.
 * Without this a re-import of a code-less sheet created every child a second time.
 */
final class StudentIdentity {
    // No 0/O, 1/I: the code is read aloud and copied by hand.
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private final StudentRepository students;

    StudentIdentity(StudentRepository students) {
        this.students = students;
    }

    /** A short code such as HS-7K2M9Q that no student of the school has yet. */
    String newCode(String tenantId, String schoolId) {
        while (true) {
            StringBuilder code = new StringBuilder("HS-");
            for (int i = 0; i < 6; i++) code.append(ALPHABET.charAt(ThreadLocalRandom.current().nextInt(ALPHABET.length())));
            if (students.findFirstByTenantIdAndSchoolIdAndStudentCodeIgnoreCase(tenantId, schoolId, code.toString()).isEmpty()) {
                return code.toString();
            }
        }
    }

    /**
     * The student this row already is: same name in the school and the same birthday when both sides have one,
     * otherwise the same class. Two different children sharing all of that need a student code in the sheet.
     */
    Optional<Student> existing(String tenantId, String schoolId, CreateStudentRequest item) {
        if (item.fullName() == null || item.fullName().isBlank()) return Optional.empty();
        String name = normalize(item.fullName());
        String classId = item.academicClassId() == null || item.academicClassId().isBlank() ? null : item.academicClassId().trim();
        return students.findByTenantIdAndSchoolIdAndFullNameIgnoreCase(tenantId, schoolId, item.fullName().trim()).stream()
                .filter(student -> normalize(student.getFullName()).equals(name))
                .filter(student -> item.birthday() != null && student.getBirthday() != null
                        ? item.birthday().toInstant().equals(student.getBirthday().toInstant())
                        : classId != null && classId.equals(student.getAcademicClassId()))
                .findFirst();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
