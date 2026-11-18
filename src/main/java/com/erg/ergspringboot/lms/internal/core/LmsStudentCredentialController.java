package com.erg.ergspringboot.lms.internal.core;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.common.response.lms.LmsOperationalResponses.StudentCredentialResetResponse;
import com.erg.ergspringboot.lms.LmsOperationalOperations;
import com.erg.ergspringboot.lms.authorization.LmsStudentCredentialReset;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Staff reissue of a student's password (BUG-A-008 / BUG-C-011); the new password is in this response only. */
@RestController
public class LmsStudentCredentialController {

    private final LmsOperationalOperations operations;

    public LmsStudentCredentialController(LmsOperationalOperations operations) {
        this.operations = operations;
    }

    @PostMapping("/api/v1/lms/classes/{classId}/students/{studentId}/reset-credential")
    @LmsStudentCredentialReset
    public ApiResponse<StudentCredentialResetResponse> reset(
            @PathVariable String classId,
            @PathVariable String studentId,
            HttpServletRequest request
    ) {
        StudentCredentialResetResponse data = operations.resetStudentCredential(
                UserAccessContext.from(request), classId, studentId,
                com.erg.ergspringboot.platform.web.RequestCorrelation.current(request),
                request.getRemoteAddr(), request.getHeader("User-Agent"));
        return ApiResponse.success(200, "Student password reissued", data, request.getRequestURI(),
                com.erg.ergspringboot.platform.web.RequestCorrelation.current(request));
    }
}
