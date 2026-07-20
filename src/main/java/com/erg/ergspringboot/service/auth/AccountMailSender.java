package com.erg.ergspringboot.service.auth;

public interface AccountMailSender {

    void sendTeacherInvite(String recipient, String fullName, String temporaryCredential);

    void sendRecoveryEmailCode(String recipient, String fullName, String code);
}
