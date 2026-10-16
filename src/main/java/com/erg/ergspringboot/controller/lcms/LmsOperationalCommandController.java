package com.erg.ergspringboot.controller.lcms;

import static com.erg.ergspringboot.common.request.lms.LmsOperationalRequests.*;
import static com.erg.ergspringboot.common.response.lms.LmsOperationalResponses.*;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.lms.LmsOperationalOperations;
import com.erg.ergspringboot.lcms.authorization.LcmsClassManage;
import com.erg.ergspringboot.lcms.authorization.LcmsEnrollmentManage;
import com.erg.ergspringboot.lcms.authorization.LcmsStudentManage;
import com.erg.ergspringboot.lcms.authorization.LcmsSubjectManage;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

/** Command endpoints for the canonical Admin LMS school directory. */
@RestController
@RequestMapping("/api/v1/lcms")
public class LmsOperationalCommandController {
    private final LmsOperationalOperations operations;

    public LmsOperationalCommandController(LmsOperationalOperations operations) { this.operations = operations; }

    @PostMapping("/schools/{schoolId}/classes")
    @LcmsClassManage
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ClassResponse> createClass(@PathVariable String schoolId, @Valid @RequestBody CreateClassRequest body, HttpServletRequest request) {
        return ok(201, "Class created", operations.createClass(schoolId, body, access(request)), request);
    }

    @RequestMapping(value = "/schools/{schoolId}/classes/{classId}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @LcmsClassManage
    public ApiResponse<ClassResponse> updateClass(@PathVariable String schoolId, @PathVariable String classId, @Valid @RequestBody UpdateClassRequest body, HttpServletRequest request) {
        return ok(200, "Class updated", operations.updateClass(schoolId, classId, body, access(request)), request);
    }

    @DeleteMapping("/schools/{schoolId}/classes/{classId}")
    @LcmsClassManage
    public ApiResponse<ClassResponse> archiveClass(@PathVariable String schoolId, @PathVariable String classId, @RequestParam(required = false) Long version, HttpServletRequest request) {
        return ok(200, "Class archived", operations.archiveClass(schoolId, classId, version, access(request)), request);
    }

    @PostMapping("/schools/{schoolId}/students")
    @LcmsStudentManage
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StudentResponse> createStudent(@PathVariable String schoolId, @Valid @RequestBody CreateStudentRequest body, HttpServletRequest request) {
        return ok(201, "Student created", operations.createStudent(schoolId, body, access(request)), request);
    }

    @RequestMapping(value = "/schools/{schoolId}/students/{studentId}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @LcmsStudentManage
    public ApiResponse<StudentResponse> updateStudent(@PathVariable String schoolId, @PathVariable String studentId, @Valid @RequestBody UpdateStudentRequest body, HttpServletRequest request) {
        return ok(200, "Student updated", operations.updateStudent(schoolId, studentId, body, access(request)), request);
    }

    @DeleteMapping("/schools/{schoolId}/students/{studentId}")
    @LcmsStudentManage
    public ApiResponse<StudentResponse> archiveStudent(@PathVariable String schoolId, @PathVariable String studentId, @RequestParam(required = false) Long version, HttpServletRequest request) {
        return ok(200, "Student archived", operations.archiveStudent(schoolId, studentId, version, access(request)), request);
    }

    @PostMapping({"/schools/{schoolId}/students/import", "/schools/{schoolId}/students/bulk"})
    @LcmsStudentManage
    public ApiResponse<BulkStudentImportResponse> importStudents(@PathVariable String schoolId, @Valid @RequestBody BulkStudentImportRequest body,
                                                                  @RequestHeader(value = "X-Idempotency-Key", required = false) String key, HttpServletRequest request) {
        return ok(200, "Students imported", operations.importStudents(schoolId, body, key, access(request)), request);
    }

    @PostMapping("/subjects")
    @LcmsSubjectManage
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SubjectResponse> createSubject(@Valid @RequestBody CreateSubjectRequest body, HttpServletRequest request) {
        return ok(201, "Subject created", operations.createSubject(body, access(request)), request);
    }

    /** Read-side subject directory is intentionally a separate contract; keep the old placeholder fail-closed. */
    @GetMapping("/subjects")
    public ResponseEntity<Void> subjectsReadNotPublished() { return ResponseEntity.notFound().build(); }

    @RequestMapping(value = "/subjects/{subjectId}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @LcmsSubjectManage
    public ApiResponse<SubjectResponse> updateSubject(@PathVariable String subjectId, @Valid @RequestBody UpdateSubjectRequest body, HttpServletRequest request) {
        return ok(200, "Subject updated", operations.updateSubject(subjectId, body, access(request)), request);
    }

    @DeleteMapping("/subjects/{subjectId}")
    @LcmsSubjectManage
    public ApiResponse<SubjectResponse> archiveSubject(@PathVariable String subjectId, @RequestParam(required = false) Long version, HttpServletRequest request) {
        return ok(200, "Subject archived", operations.archiveSubject(subjectId, version, access(request)), request);
    }

    @PostMapping("/enrollments")
    @LcmsEnrollmentManage
    public ApiResponse<EnrollmentResponse> enroll(@Valid @RequestBody EnrollStudentRequest body,
                                                   @RequestHeader(value = "X-Idempotency-Key", required = false) String key, HttpServletRequest request) {
        return ok(200, "Student enrolled", operations.enroll(body, key, access(request)), request);
    }

    @PostMapping("/enrollments/bulk")
    @LcmsEnrollmentManage
    public ApiResponse<BulkEnrollmentResponse> bulkEnroll(@Valid @RequestBody BulkEnrollRequest body,
                                                           @RequestHeader(value = "X-Idempotency-Key", required = false) String key, HttpServletRequest request) {
        return ok(200, "Students enrolled", operations.bulkEnroll(body, key, access(request)), request);
    }

    private UserAccessContext access(HttpServletRequest request) { return UserAccessContext.from(request); }
    private <T> ApiResponse<T> ok(int status, String message, T data, HttpServletRequest request) {
        return ApiResponse.success(status, message, data, request.getRequestURI(), com.erg.ergspringboot.platform.web.RequestCorrelation.current(request));
    }
}
