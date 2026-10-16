package com.erg.ergspringboot.controller.lcms.graphql;

import static com.erg.ergspringboot.common.request.lms.LmsOperationalRequests.*;
import static com.erg.ergspringboot.common.response.lms.LmsOperationalResponses.*;

import com.erg.ergspringboot.common.response.lms.LmsOperationalResponses;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.lcms.authorization.LcmsClassManage;
import com.erg.ergspringboot.lcms.authorization.LcmsEnrollmentManage;
import com.erg.ergspringboot.lcms.authorization.LcmsStudentManage;
import com.erg.ergspringboot.lcms.authorization.LcmsSubjectManage;
import com.erg.ergspringboot.lms.LmsOperationalOperations;
import com.erg.ergspringboot.platform.security.AuthenticationRequiredException;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.AccessScopeAssembler;
import com.erg.ergspringboot.platform.security.AccessScopeSet;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

/** GraphQL command adapter for the tenant-scoped school operations boundary. */
@Controller
public class LcmsSchoolOperationsMutationGraphQlController {

    private final LmsOperationalOperations operations;
    private final GraphQlAccessSupport accessSupport;

    public LcmsSchoolOperationsMutationGraphQlController(
            LmsOperationalOperations operations, GraphQlAccessSupport accessSupport) {
        this.operations = operations;
        this.accessSupport = accessSupport;
    }

    @MutationMapping
    @LcmsClassManage
    public OperationalClassResponse createSchoolClass(@Argument CreateSchoolClassInput input) {
        ErgPrincipal principal = principal();
        UserAccessContext access = access(principal, input.tenantId(), "lms.class.manage", "*");
        accessSupport.assertEducationUnitScope(principal, input.schoolId());
        return classResponse(operations.createClass(input.schoolId(), new CreateClassRequest(
                input.name(), input.grade(), input.academicYear(), input.homeroomTeacherId(), input.status()), access));
    }

    @MutationMapping
    @LcmsClassManage
    public OperationalClassResponse updateSchoolClass(@Argument UpdateSchoolClassInput input) {
        ErgPrincipal principal = principal();
        UserAccessContext access = access(principal, input.tenantId(), "lms.class.manage", "*");
        accessSupport.assertEducationUnitScope(principal, input.schoolId());
        return classResponse(operations.updateClass(input.schoolId(), input.classId(), new UpdateClassRequest(
                version(input.version()), input.name(), input.grade(), input.academicYear(),
                input.homeroomTeacherId(), input.status()), access));
    }

    @MutationMapping
    @LcmsClassManage
    public OperationalClassResponse archiveSchoolClass(@Argument ArchiveSchoolClassInput input) {
        ErgPrincipal principal = principal();
        UserAccessContext access = access(principal, input.tenantId(), "lms.class.manage", "*");
        accessSupport.assertEducationUnitScope(principal, input.schoolId());
        return classResponse(operations.archiveClass(input.schoolId(), input.classId(), version(input.version()), access));
    }

    @MutationMapping
    @LcmsStudentManage
    public OperationalStudentResponse createSchoolStudent(@Argument CreateSchoolStudentInput input) {
        ErgPrincipal principal = principal();
        UserAccessContext access = access(principal, input.tenantId(), "lms.student.manage", "lms.class.manage", "*");
        accessSupport.assertEducationUnitScope(principal, input.schoolId());
        return studentResponse(operations.createStudent(input.schoolId(), studentRequest(input), access));
    }

    @MutationMapping
    @LcmsStudentManage
    public OperationalStudentResponse updateSchoolStudent(@Argument UpdateSchoolStudentInput input) {
        ErgPrincipal principal = principal();
        UserAccessContext access = access(principal, input.tenantId(), "lms.student.manage", "lms.class.manage", "*");
        accessSupport.assertEducationUnitScope(principal, input.schoolId());
        return studentResponse(operations.updateStudent(input.schoolId(), input.studentId(), new UpdateStudentRequest(
                version(input.version()), input.studentCode(), input.fullName(), input.academicClassId(), input.email(),
                input.username(), input.gender(), date(input.birthday()), input.phone(), input.address(), input.parentName(),
                input.parentPhone(), input.parentEmail(), input.parentRelationship(), date(input.enrollmentDate()),
                input.note(), input.status()), access));
    }

    @MutationMapping
    @LcmsStudentManage
    public OperationalStudentResponse archiveSchoolStudent(@Argument ArchiveSchoolStudentInput input) {
        ErgPrincipal principal = principal();
        UserAccessContext access = access(principal, input.tenantId(), "lms.student.manage", "lms.class.manage", "*");
        accessSupport.assertEducationUnitScope(principal, input.schoolId());
        return studentResponse(operations.archiveStudent(input.schoolId(), input.studentId(), version(input.version()), access));
    }

    @MutationMapping
    @LcmsStudentManage
    public BulkStudentImportResponse importSchoolStudents(@Argument ImportSchoolStudentsInput input) {
        ErgPrincipal principal = principal();
        UserAccessContext access = access(principal, input.tenantId(), "lms.student.manage", "lms.class.manage", "*");
        accessSupport.assertEducationUnitScope(principal, input.schoolId());
        List<CreateStudentRequest> items = input.items().stream().map(this::studentRequest).toList();
        return studentImportResponse(operations.importStudents(
                input.schoolId(), new BulkStudentImportRequest(items), input.idempotencyKey(), access));
    }

    @MutationMapping
    @LcmsSubjectManage
    public OperationalSubjectResponse createSchoolSubject(@Argument CreateSchoolSubjectInput input) {
        ErgPrincipal principal = principal();
        UserAccessContext access = access(principal, input.tenantId(), "lms.subject.manage", "curriculum.manage", "lms.class.manage", "*");
        if (input.schoolId() != null) accessSupport.assertEducationUnitScope(principal, input.schoolId());
        return subjectResponse(operations.createSubject(new CreateSubjectRequest(
                input.name(), input.code(), input.schoolId(), input.status()), access));
    }

    @MutationMapping
    @LcmsSubjectManage
    public OperationalSubjectResponse updateSchoolSubject(@Argument UpdateSchoolSubjectInput input) {
        ErgPrincipal principal = principal();
        UserAccessContext access = access(principal, input.tenantId(), "lms.subject.manage", "curriculum.manage", "lms.class.manage", "*");
        return subjectResponse(operations.updateSubject(input.subjectId(), new UpdateSubjectRequest(
                version(input.version()), input.name(), input.code(), input.status()), access));
    }

    @MutationMapping
    @LcmsSubjectManage
    public OperationalSubjectResponse archiveSchoolSubject(@Argument ArchiveSchoolSubjectInput input) {
        ErgPrincipal principal = principal();
        UserAccessContext access = access(principal, input.tenantId(), "lms.subject.manage", "curriculum.manage", "lms.class.manage", "*");
        return subjectResponse(operations.archiveSubject(input.subjectId(), version(input.version()), access));
    }

    @MutationMapping
    @LcmsEnrollmentManage
    public OperationalEnrollmentResponse enrollSchoolStudent(@Argument EnrollSchoolStudentInput input) {
        ErgPrincipal principal = principal();
        UserAccessContext access = access(principal, input.tenantId(), "lms.enrollment.manage", "lms.class.manage", "*");
        return enrollmentResponse(operations.enroll(
                new EnrollStudentRequest(input.studentId(), input.classId(), input.academicYear()),
                input.idempotencyKey(), access));
    }

    @MutationMapping
    @LcmsEnrollmentManage
    public BulkEnrollmentResponse bulkEnrollSchoolStudents(@Argument BulkEnrollSchoolStudentsInput input) {
        ErgPrincipal principal = principal();
        UserAccessContext access = access(principal, input.tenantId(), "lms.enrollment.manage", "lms.class.manage", "*");
        List<EnrollmentItem> items = input.items().stream()
                .map(item -> new EnrollmentItem(item.studentId(), item.classId(), item.academicYear()))
                .toList();
        return bulkEnrollmentResponse(operations.bulkEnroll(new BulkEnrollRequest(items), input.idempotencyKey(), access));
    }

    private ErgPrincipal principal() {
        ErgPrincipal principal = accessSupport.currentPrincipal();
        if (principal == null) throw new AuthenticationRequiredException();
        return principal;
    }

    private UserAccessContext access(ErgPrincipal principal, String tenantId, String... permissions) {
        String verifiedTenant = accessSupport.verifiedTenantId(principal, tenantId);
        accessSupport.assertPermission(principal, permissions);
        AccessScopeSet scopes = AccessScopeAssembler.from(principal);
        return new UserAccessContext(
                principal.userId(), verifiedTenant,
                scopes.managedIds(AccessScopeSet.EDUCATION_UNIT),
                scopes.managedIds(AccessScopeSet.CLASS),
                scopes.currentId(AccessScopeSet.EDUCATION_UNIT).orElse(null),
                scopes.currentId(AccessScopeSet.CLASS).orElse(null));
    }

    private CreateStudentRequest studentRequest(CreateSchoolStudentInput input) {
        return new CreateStudentRequest(input.studentCode(), input.fullName(), input.academicClassId(), input.email(),
                input.username(), input.gender(), date(input.birthday()), input.phone(), input.address(), input.parentName(),
                input.parentPhone(), input.parentEmail(), input.parentRelationship(), date(input.enrollmentDate()),
                input.note(), input.status());
    }

    private OffsetDateTime date(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return OffsetDateTime.parse(value);
        } catch (RuntimeException exception) {
            throw new BadRequestException("Date-time must use ISO-8601 offset format.");
        }
    }

    private Long version(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Version must be a non-negative integer.");
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0) throw new NumberFormatException();
            return parsed;
        } catch (RuntimeException exception) {
            throw new BadRequestException("Version must be a non-negative integer.");
        }
    }

    private String text(Object value) { return value == null ? null : value.toString(); }
    private String version(long value) { return Long.toString(value); }
    private String date(OffsetDateTime value) { return text(value); }

    private OperationalClassResponse classResponse(ClassResponse value) {
        return new OperationalClassResponse(value.id(), value.tenantId(), value.schoolId(), value.name(), value.grade(),
                value.academicYear(), value.homeroomTeacherId(), value.status(), version(value.version()), date(value.updatedAt()));
    }

    private OperationalStudentResponse studentResponse(StudentResponse value) {
        return new OperationalStudentResponse(value.id(), value.tenantId(), value.schoolId(), value.academicClassId(),
                value.studentCode(), value.fullName(), value.username(), value.authUserId(), value.email(), value.gender(),
                date(value.birthday()), value.phone(), value.address(), value.parentName(), value.parentPhone(), value.parentEmail(),
                value.parentRelationship(), date(value.enrollmentDate()), value.note(), value.status(), version(value.version()),
                date(value.updatedAt()));
    }

    private OperationalSubjectResponse subjectResponse(SubjectResponse value) {
        return new OperationalSubjectResponse(value.id(), value.tenantId(), value.schoolId(), value.name(), value.code(),
                value.status(), version(value.version()), date(value.updatedAt()));
    }

    private OperationalEnrollmentResponse enrollmentResponse(EnrollmentResponse value) {
        return new OperationalEnrollmentResponse(value.id(), value.tenantId(), value.schoolId(), value.studentId(), value.classId(),
                value.academicYear(), value.status(), version(value.version()), value.replayed(), date(value.updatedAt()));
    }

    private BulkEnrollmentResponse bulkEnrollmentResponse(LmsOperationalResponses.BulkEnrollmentResponse value) {
        return new BulkEnrollmentResponse(value.idempotencyKey(), value.requested(), value.enrolled(), value.replayed(),
                value.items().stream().map(this::enrollmentResponse).toList());
    }

    private BulkStudentImportResponse studentImportResponse(LmsOperationalResponses.BulkStudentImportResponse value) {
        return new BulkStudentImportResponse(value.idempotencyKey(), value.requested(), value.created(), value.replayed(),
                value.items().stream().map(this::studentResponse).toList());
    }

    public record CreateSchoolClassInput(String tenantId, String schoolId, String name, String grade,
                                         String academicYear, String homeroomTeacherId, String status) {}
    public record UpdateSchoolClassInput(String tenantId, String schoolId, String classId, String version,
                                         String name, String grade, String academicYear, String homeroomTeacherId, String status) {}
    public record ArchiveSchoolClassInput(String tenantId, String schoolId, String classId, String version) {}
    public record CreateSchoolStudentInput(String tenantId, String schoolId, String studentCode, String fullName, String academicClassId, String email,
                                           String username, String gender, String birthday, String phone, String address,
                                           String parentName, String parentPhone, String parentEmail, String parentRelationship,
                                           String enrollmentDate, String note, String status) {}
    public record UpdateSchoolStudentInput(String tenantId, String schoolId, String studentId, String version,
                                           String studentCode, String fullName, String academicClassId, String email, String username,
                                           String gender, String birthday, String phone, String address, String parentName,
                                           String parentPhone, String parentEmail, String parentRelationship, String enrollmentDate,
                                           String note, String status) {}
    public record ArchiveSchoolStudentInput(String tenantId, String schoolId, String studentId, String version) {}
    public record ImportSchoolStudentsInput(String tenantId, String schoolId, String idempotencyKey,
                                            List<CreateSchoolStudentInput> items) {}
    public record CreateSchoolSubjectInput(String tenantId, String schoolId, String name, String code, String status) {}
    public record UpdateSchoolSubjectInput(String tenantId, String subjectId, String version, String name, String code, String status) {}
    public record ArchiveSchoolSubjectInput(String tenantId, String subjectId, String version) {}
    public record EnrollSchoolStudentInput(String tenantId, String studentId, String classId,
                                           String academicYear, String idempotencyKey) {}
    public record BulkEnrollSchoolStudentsInput(String tenantId, String idempotencyKey,
                                                List<EnrollSchoolStudentItemInput> items) {}
    public record EnrollSchoolStudentItemInput(String studentId, String classId, String academicYear) {}

    public record OperationalClassResponse(String id, String tenantId, String schoolId, String name, String grade,
                                           String academicYear, String homeroomTeacherId, String status, String version,
                                           String updatedAt) {}
    public record OperationalStudentResponse(String id, String tenantId, String schoolId, String academicClassId,
                                            String studentCode, String fullName, String username, String authUserId, String email,
                                            String gender, String birthday, String phone, String address, String parentName,
                                            String parentPhone, String parentEmail, String parentRelationship, String enrollmentDate,
                                            String note, String status, String version, String updatedAt) {}
    public record OperationalSubjectResponse(String id, String tenantId, String schoolId, String name, String code,
                                             String status, String version, String updatedAt) {}
    public record OperationalEnrollmentResponse(String id, String tenantId, String schoolId, String studentId, String classId,
                                                String academicYear, String status, String version, boolean replayed,
                                                String updatedAt) {}
    public record BulkEnrollmentResponse(String idempotencyKey, int requested, int enrolled, int replayed,
                                         List<OperationalEnrollmentResponse> items) {}
    public record BulkStudentImportResponse(String idempotencyKey, int requested, int created, int replayed,
                                            List<OperationalStudentResponse> items) {}
}
