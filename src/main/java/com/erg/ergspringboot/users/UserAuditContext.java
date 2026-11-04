package com.erg.ergspringboot.users;

/** Framework-neutral audit metadata published by the users module boundary. */
public record UserAuditContext(String actorUserId, String requestId, String ipAddress, String userAgent) { }
