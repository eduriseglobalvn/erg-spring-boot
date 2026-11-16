package com.erg.ergspringboot.authorization.web;

import com.erg.ergspringboot.authorization.authorization.UsersManage;
import com.erg.ergspringboot.platform.security.AccountActionGuard;
import com.erg.ergspringboot.platform.security.AccountActionGuard.AssignableRole;
import com.erg.ergspringboot.platform.security.SecurityUtils;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Roles the caller may hand out: every one of their permissions lies within the caller's own. */
@RestController
@RequestMapping("/api/v1/rbac")
public class AssignableRolesController {

    private final AccountActionGuard guard;

    public AssignableRolesController(AccountActionGuard guard) {
        this.guard = guard;
    }

    @GetMapping("/assignable-roles")
    @UsersManage
    public List<AssignableRole> assignableRoles() {
        return guard.assignableRoles(SecurityUtils.requirePrincipal());
    }
}
