package com.erg.ergspringboot.service.auth;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class SmtpAccountMailSender implements AccountMailSender {

    private final JavaMailSender mailSender;

    public SmtpAccountMailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendTeacherInvite(String recipient, String fullName, String temporaryCredential) {
        send(recipient, "ERG - Thư mời tài khoản giáo viên",
                "Xin chào " + fullName + ",\n\nTài khoản ERG của bạn đã được tạo. "
                        + "Mật khẩu tạm thời: " + temporaryCredential
                        + "\nVui lòng đăng nhập và hoàn tất các bước bảo mật bắt buộc.");
    }

    @Override
    public void sendRecoveryEmailCode(String recipient, String fullName, String code) {
        send(recipient, "ERG - Mã xác minh email khôi phục",
                "Xin chào " + fullName + ",\n\nMã xác minh của bạn là: " + code
                        + "\nMã chỉ có hiệu lực trong thời gian ngắn. Không chia sẻ mã này.");
    }

    private void send(String recipient, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
