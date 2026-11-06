package com.erg.ergspringboot.notifications.internal.campaign;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import com.erg.ergspringboot.lms.LmsAudienceReadPort;
import com.erg.ergspringboot.users.UserDirectoryPort;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;

/** Smoke test: every query behind the audience ports is valid against the real schema. */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
@EnabledIf("dockerAvailable")
class NotificationAudienceQueriesIT {

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @Autowired
    private LmsAudienceReadPort lms;

    @Autowired
    private UserDirectoryPort users;

    @Test
    void portsAnswerEmptyForUnknownTenant() {
        assertThat(lms.classIdsOf("nobody", List.of("s"), List.of("7"))).isEmpty();
        assertThat(lms.classIdsTaughtBy("nobody", "u")).isEmpty();
        assertThat(lms.studentUserIdsInClasses("nobody", List.of("c"), 10)).isEmpty();
        assertThat(lms.studentUserIdsInGroups("nobody", List.of("g"), 10)).isEmpty();
        assertThat(lms.allStudentUserIds("nobody", 10)).isEmpty();
        assertThat(lms.studentUserIdsInClassesAmong("nobody", List.of("c"), List.of("u"))).isEmpty();
        assertThat(users.findActiveStaffUserIds("nobody", PageRequest.ofSize(10))).isEmpty();
    }

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }
}
