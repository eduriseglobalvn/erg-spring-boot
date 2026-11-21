package com.erg.ergspringboot.lms.internal.core;

import com.erg.ergspringboot.common.request.lms.LmsOperationalRequests.CreateStudentRequest;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.lms.persistence.StudentRepository;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Who a new student row is when the sheet carries no student code, and the code a new student gets then.
 * Without this a re-import of a code-less sheet created every child a second time.
 */
final class StudentIdentity {
    // No 0/O, 1/I: the code is read aloud and copied by hand.
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

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
     * The student an import row already is. A row with a code is the student with that code (a different name is a
     * conflict); only when no such code exists may a row that asks for a login adopt a student the system itself
     * coded (HS-...) and that has no login yet. A code-less row falls back to name + birthday or class.
     */
    Optional<Student> forImportRow(String tenantId, String schoolId, CreateStudentRequest item) {
        String code = item.studentCode() == null || item.studentCode().isBlank() ? null : item.studentCode().trim();
        if (code == null) return existing(tenantId, schoolId, item);
        var byCode = students.findFirstByTenantIdAndSchoolIdAndStudentCodeIgnoreCase(tenantId, schoolId, code);
        if (byCode.isPresent()) {
            if (!normalize(byCode.get().getFullName()).equals(normalize(item.fullName()))) {
                throw new ConflictException("Import student code belongs to a different student.");
            }
            return byCode;
        }
        if (!wantsAccount(item)) return Optional.empty();
        return existing(tenantId, schoolId, item)
                .filter(s -> s.getAuthUserId() == null && s.getStudentCode() != null && s.getStudentCode().startsWith("HS-"));
    }

    /** A row asks for a login when it carries a username or a password (a missing password means the default one). */
    static boolean wantsAccount(CreateStudentRequest item) {
        return (item.password() != null && !item.password().isBlank()) || (item.username() != null && !item.username().isBlank());
    }

    /**
     * The student this row already is: same name in the school and the same birthday when both sides have one,
     * otherwise the same class. Two different children sharing all of that need a student code in the sheet.
     */
    Optional<Student> existing(String tenantId, String schoolId, CreateStudentRequest item) {
        if (item.fullName() == null || item.fullName().isBlank()) return Optional.empty();
        String name = normalize(item.fullName());
        String classId = item.academicClassId() == null || item.academicClassId().isBlank() ? null : item.academicClassId().trim();
        return students.findByTenantIdAndSchoolIdAndFullNameNormalized(tenantId, schoolId, name).stream()
                // An archived student is not "replayed": the row is a new enrolment unless its code matched exactly.
                .filter(student -> !"ARCHIVED".equals(student.getStatus()))
                .filter(student -> normalize(student.getFullName()).equals(name))
                .filter(student -> item.birthday() != null && student.getBirthday() != null
                        ? day(item.birthday()).equals(day(student.getBirthday()))
                        : classId != null && classId.equals(student.getAcademicClassId()))
                .findFirst();
    }

    private static LocalDate day(java.time.OffsetDateTime value) {
        return value.atZoneSameInstant(VN).toLocalDate();
    }

    /** NFC, inner whitespace collapsed to one space, trimmed. */
    static String clean(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFC).replaceAll("[\\s\\u00A0]+", " ").trim();
    }

    static String normalize(String value) {
        return Student.normalizeName(value);
    }
}
