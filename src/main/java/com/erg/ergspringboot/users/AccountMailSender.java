package com.erg.ergspringboot.users;

public interface AccountMailSender {

    void sendTeacherInvite(String recipient, String fullName, String temporaryCredential);

    void sendRecoveryEmailCode(String recipient, String fullName, String code);
}
