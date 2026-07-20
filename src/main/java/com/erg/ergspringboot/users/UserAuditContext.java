package com.erg.ergspringboot.users;

import com.erg.ergspringboot.service.users.UserService;

/** Framework-neutral audit metadata published by the users module boundary. */
public record UserAuditContext(String actorUserId, String requestId, String ipAddress, String userAgent) {
    public UserService.AuditContext toLegacy() {
        return new UserService.AuditContext(actorUserId, requestId, ipAddress, userAgent);
    }
}
