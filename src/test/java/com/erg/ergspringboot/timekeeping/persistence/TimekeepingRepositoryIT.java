package com.erg.ergspringboot.timekeeping.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import com.erg.ergspringboot.timekeeping.PunchDirection;
import com.erg.ergspringboot.timekeeping.PunchStatus;
import com.erg.ergspringboot.timekeeping.RequestStatus;
import com.erg.ergspringboot.timekeeping.RequestType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;

/** V132–V135 on real PostgreSQL: boot validates the entities (ddl-auto=validate) and the constraints hold. */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
@EnabledIf("postgresAvailable")
class TimekeepingRepositoryIT {

    private static final String TENANT = "tenant-tk";
    private static final String SCHOOL = "school-tk";

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @Autowired private TkSiteRepository sites;
    @Autowired private TkShiftRepository shifts;
    @Autowired private TkPunchRepository punches;
    @Autowired private TkPolicyRepository policies;
    @Autowired private TkRequestRepository requests;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void siteRadiusOutsideFiftyToFiveHundredIsRejected() {
        assertThat(sites.saveAndFlush(site(150)).getId()).isNotBlank();
        assertThatThrownBy(() -> sites.saveAndFlush(site(600)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateIdempotencyKeyForTheSameUserIsRejected() {
        TkShift shift = shifts.saveAndFlush(shift());
        punches.saveAndFlush(punch(shift, "CC-061126-0001", "key-1"));

        assertThatThrownBy(() -> punches.saveAndFlush(punch(shift, "CC-061126-0002", "key-1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicatePunchCodeInTheSameTenantIsRejected() {
        TkShift shift = shifts.saveAndFlush(shift());
        punches.saveAndFlush(punch(shift, "CC-061126-0003", "key-a"));

        assertThatThrownBy(() -> punches.saveAndFlush(punch(shift, "CC-061126-0003", "key-b")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateRequestCodeInTheSameTenantIsRejected() {
        requests.saveAndFlush(request("DT-2026-0418"));

        assertThatThrownBy(() -> requests.saveAndFlush(request("DT-2026-0418")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void schoolKeepsOnePolicyPerEffectiveDate() {
        policies.saveAndFlush(policy(LocalDate.of(2026, 1, 1)));
        policies.saveAndFlush(policy(LocalDate.of(2026, 12, 1)));

        assertThatThrownBy(() -> policies.saveAndFlush(policy(LocalDate.of(2026, 12, 1))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v135GrantsTimekeepingPermissionsToExplicitRoles() {
        assertThat(granted("timekeeping.self", "role_lms_teacher_standard")).isTrue();
        assertThat(granted("timekeeping.self", "role_lms_teacher")).isTrue();
        assertThat(granted("timekeeping.approve", "role_lms_teacher_lead")).isTrue();
        assertThat(granted("timekeeping.approve", "role_school_vice_principal")).isTrue();
        assertThat(granted("timekeeping.manage", "role_lms_admin")).isTrue();
        assertThat(granted("timekeeping.manage", "role_lms_teacher_lead")).isFalse();
        assertThat(granted("timekeeping.self", "role_lms_student")).isFalse();
        assertThat(jdbc.queryForObject(
                "select name from roles where id = 'role_school_vice_principal'", String.class))
                .isEqualTo("school.vice_principal");
    }

    private boolean granted(String permission, String roleId) {
        Integer count = jdbc.queryForObject("""
                select count(*) from role_permissions rp join permissions p on p.id = rp.permission_id
                where p.name = ? and rp.role_id = ?
                """, Integer.class, permission, roleId);
        return count != null && count == 1;
    }

    private static TkSite site(int radiusM) {
        TkSite site = new TkSite();
        site.setTenantId(TENANT);
        site.setSchoolId(SCHOOL);
        site.setName("Cơ sở 1");
        site.setLat(new BigDecimal("21.028511"));
        site.setLng(new BigDecimal("105.804817"));
        site.setRadiusM(radiusM);
        return site;
    }

    private static TkShift shift() {
        TkShift shift = new TkShift();
        shift.setTenantId(TENANT);
        shift.setSchoolId(SCHOOL);
        shift.setName("Ca hành chính");
        shift.setStartTime(LocalTime.of(7, 30));
        shift.setEndTime(LocalTime.of(17, 0));
        shift.setWeekdays(new Integer[] {1, 2, 3, 4, 5});
        return shift;
    }

    private static TkPunch punch(TkShift shift, String code, String idempotencyKey) {
        TkPunch punch = new TkPunch();
        punch.setTenantId(TENANT);
        punch.setSchoolId(SCHOOL);
        punch.setUserId("user-tk");
        punch.setShiftId(shift.getId());
        punch.setCode(code);
        punch.setWorkDate(LocalDate.of(2026, 11, 6));
        punch.setDirection(PunchDirection.IN);
        punch.setServerTime(Instant.parse("2026-11-06T00:25:00Z"));
        punch.setStatus(PunchStatus.VALID);
        punch.setIdempotencyKey(idempotencyKey);
        return punch;
    }

    private static TkPolicy policy(LocalDate effectiveFrom) {
        TkPolicy policy = new TkPolicy();
        policy.setTenantId(TENANT);
        policy.setSchoolId(SCHOOL);
        policy.setEffectiveFrom(effectiveFrom);
        return policy;
    }

    private static TkRequest request(String code) {
        TkRequest request = new TkRequest();
        request.setTenantId(TENANT);
        request.setSchoolId(SCHOOL);
        request.setUserId("user-tk");
        request.setCode(code);
        request.setType(RequestType.LEAVE);
        request.setStatus(RequestStatus.DRAFT);
        return request;
    }

    static boolean postgresAvailable() {
        String url = System.getProperty("erg.test.postgres.url", System.getenv("ERG_TEST_POSTGRES_URL"));
        if (url != null && !url.isBlank()) {
            return true;
        }
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException unavailable) {
            return false;
        }
    }
}
