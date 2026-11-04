package com.erg.ergspringboot.lms.internal.core;

import static com.erg.ergspringboot.common.response.lms.LmsOperationalResponses.*;

import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.lms.persistence.StudentEnrollment;
import com.erg.ergspringboot.lms.persistence.Subject;

/** Maps LMS operational aggregates to their published response records. */
final class LmsOperationalResponseMapper {

    private LmsOperationalResponseMapper() {
    }

    static ClassResponse classResponse(Classroom value) {
        return new ClassResponse(value.getId(), value.getTenantId(), value.getSchoolId(), value.getName(), value.getGrade(), value.getAcademicYear(), value.getHomeroomTeacherId(), value.getStatus(), version(value.getRecordVersion()), value.getUpdatedAt());
    }

    static StudentResponse studentResponse(Student value) {
        return new StudentResponse(value.getId(), value.getTenantId(), value.getSchoolId(), value.getAcademicClassId(), value.getStudentCode(), value.getFullName(), value.getUsername(), value.getAuthUserId(), value.getEmail(), value.getGender(), value.getBirthday(), value.getPhone(), value.getAddress(), value.getParentName(), value.getParentPhone(), value.getParentEmail(), value.getParentRelationship(), value.getEnrollmentDate(), value.getNote(), value.getStatus(), version(value.getRecordVersion()), value.getUpdatedAt());
    }

    static SubjectResponse subjectResponse(Subject value) {
        String schoolId = value.getScope() == null ? null : value.getScope().getSchoolId();
        return new SubjectResponse(value.getId(), value.getTenantId(), schoolId, value.getName(), value.getCode(), value.getStatus(), version(value.getRecordVersion()), value.getUpdatedAt());
    }

    static EnrollmentResponse enrollmentResponse(StudentEnrollment value, boolean replayed) {
        return new EnrollmentResponse(value.getId(), value.getTenantId(), value.getSchoolId(), value.getStudentId(), value.getClassId(), value.getAcademicYear(), value.getStatus(), version(value.getRecordVersion()), replayed, value.getUpdatedAt());
    }
    private static long version(Long value) { return value == null ? 0L : value; }
}
