package com.erg.ergspringboot.config.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/** JdbcTemplate writes inside a JPA transaction must run on that transaction's connection, not a side one. */
@ActiveProfiles("test")
@SpringBootTest
class JdbcJoinsJpaTransactionIntegrationTest {

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @Autowired private JdbcTemplate jdbcTemplate;
    @PersistenceContext private EntityManager entityManager;

    @Test
    @Transactional
    void jdbcAndJpaShareTheTransactionConnection() {
        Object jpaBackend = entityManager.createNativeQuery("select pg_backend_pid()").getSingleResult();
        Integer jdbcBackend = jdbcTemplate.queryForObject("select pg_backend_pid()", Integer.class);

        assertThat(jdbcBackend).isEqualTo(((Number) jpaBackend).intValue());
    }
}
