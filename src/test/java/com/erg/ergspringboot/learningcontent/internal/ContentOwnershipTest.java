package com.erg.ergspringboot.learningcontent.internal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.MethodAuthorizationEvaluator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class ContentOwnershipTest {

    private final MethodAuthorizationEvaluator evaluator = mock(MethodAuthorizationEvaluator.class);
    private final ContentOwnership ownership = new ContentOwnership(evaluator);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void creatorMayModifyWithoutManagePermission() {
        signIn("teacher-1");
        assertThatCode(() -> ownership.requireCanModify("teacher-1")).doesNotThrowAnyException();
    }

    @Test
    void otherTeacherIsForbidden() {
        signIn("teacher-2");
        assertThatThrownBy(() -> ownership.requireCanModify("teacher-1")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void managerMayModifyAnyItem() {
        Authentication authentication = signIn("manager-1");
        when(evaluator.isAllowedAny(authentication, "content.manage", "*")).thenReturn(true);
        assertThatCode(() -> ownership.requireCanModify("teacher-1")).doesNotThrowAnyException();
    }

    @Test
    void legacyRowWithoutCreatorIsManagerOnly() {
        Authentication authentication = signIn("teacher-1");
        assertThatThrownBy(() -> ownership.requireCanModify(null)).isInstanceOf(ForbiddenException.class);
        when(evaluator.isAllowedAny(authentication, "content.manage", "*")).thenReturn(true);
        assertThatCode(() -> ownership.requireCanModify(null)).doesNotThrowAnyException();
    }

    private static Authentication signIn(String userId) {
        ErgPrincipal principal = new ErgPrincipal(userId, "s-1", "tenant-a", List.of(), List.of("lms.resource.update"),
                List.of(), List.of(), "erg", "user", Map.of());
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return authentication;
    }
}
