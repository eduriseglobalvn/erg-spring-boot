package com.erg.ergspringboot.lms;

import static com.erg.ergspringboot.common.request.lms.LmsOperationalRequests.*;
import static com.erg.ergspringboot.common.response.lms.LmsOperationalResponses.*;

import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.service.lms.LmsOperationalCommandService;
import org.springframework.stereotype.Service;

/** Public LMS boundary for school-directory write commands. */
@Service
public class LmsOperationalOperations {
    private final LmsOperationalCommandService commands;

    public LmsOperationalOperations(LmsOperationalCommandService commands) {
        this.commands = commands;
    }

    public ClassResponse createClass(String schoolId, CreateClassRequest request, UserAccessContext access) { return commands.createClass(schoolId, request, access); }
    public ClassResponse updateClass(String schoolId, String classId, UpdateClassRequest request, UserAccessContext access) { return commands.updateClass(schoolId, classId, request, access); }
    public ClassResponse archiveClass(String schoolId, String classId, Long version, UserAccessContext access) { return commands.archiveClass(schoolId, classId, version, access); }
    public StudentResponse createStudent(String schoolId, CreateStudentRequest request, UserAccessContext access) { return commands.createStudent(schoolId, request, access); }
    public StudentResponse updateStudent(String schoolId, String studentId, UpdateStudentRequest request, UserAccessContext access) { return commands.updateStudent(schoolId, studentId, request, access); }
    public StudentResponse archiveStudent(String schoolId, String studentId, Long version, UserAccessContext access) { return commands.archiveStudent(schoolId, studentId, version, access); }
    public BulkStudentImportResponse importStudents(String schoolId, BulkStudentImportRequest request, String key, UserAccessContext access) { return commands.importStudents(schoolId, request, key, access); }
    public SubjectResponse createSubject(CreateSubjectRequest request, UserAccessContext access) { return commands.createSubject(request, access); }
    public SubjectResponse updateSubject(String subjectId, UpdateSubjectRequest request, UserAccessContext access) { return commands.updateSubject(subjectId, request, access); }
    public SubjectResponse archiveSubject(String subjectId, Long version, UserAccessContext access) { return commands.archiveSubject(subjectId, version, access); }
    public EnrollmentResponse enroll(EnrollStudentRequest request, String key, UserAccessContext access) { return commands.enroll(request, key, access); }
    public BulkEnrollmentResponse bulkEnroll(BulkEnrollRequest request, String key, UserAccessContext access) { return commands.bulkEnroll(request, key, access); }
}
