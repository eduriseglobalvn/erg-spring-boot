package com.erg.ergspringboot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * P1-7: a relay that accepts the connection and then never answers must not hold the sending
 * worker forever. Uses the same JavaMail property keys as application.yml with a short value.
 */
class HungSmtpRelayTimeoutTest {

    @Test
    void sendFailsFastWhenTheRelayNeverSendsItsGreeting() throws Exception {
        List<Socket> held = new ArrayList<>();
        try (ServerSocket relay = new ServerSocket(0)) {
            Thread acceptor = Thread.ofVirtual().start(() -> {
                try {
                    held.add(relay.accept()); // accept, then stay silent
                } catch (Exception ignored) {
                    // socket closed at the end of the test
                }
            });
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost("127.0.0.1");
            sender.setPort(relay.getLocalPort());
            Properties props = new Properties();
            props.put("mail.smtp.connectiontimeout", "500");
            props.put("mail.smtp.timeout", "500");
            props.put("mail.smtp.writetimeout", "500");
            sender.setJavaMailProperties(props);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@example.test");
            message.setTo("user@example.test");
            message.setSubject("probe");
            message.setText("probe");

            long started = System.nanoTime();
            assertThatThrownBy(() -> sender.send(message)).isInstanceOf(MailException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
            acceptor.interrupt();
        } finally {
            for (Socket socket : held) {
                socket.close();
            }
        }
    }
}
