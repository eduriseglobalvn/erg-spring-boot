package com.erg.ergspringboot.learningcontent.internal;

import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.security.MethodAuthorizationEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * A content item or asset may be changed or deleted only by its creator or a content manager. Rows without a creator
 * (legacy) are manager-only. A call with no authentication at all is an internal one and is not restricted here: web
 * requests always carry one, and the URL rules already reject anonymous callers.
 */
@Component
class ContentOwnership {

    private final MethodAuthorizationEvaluator authorization;

    ContentOwnership(MethodAuthorizationEvaluator authorization) {
        this.authorization = authorization;
    }

    void requireCanModify(String createdBy) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return;
        if (authentication.getPrincipal() instanceof ErgPrincipal principal && createdBy != null
                && createdBy.equals(principal.userId())) {
            return;
        }
        if (!authorization.isAllowedAny(authentication, "content.manage", "*")) {
            throw new ForbiddenException("Bạn chỉ sửa được nội dung do chính mình tạo.");
        }
    }
}
