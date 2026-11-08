package com.erg.ergspringboot.notifications.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import com.erg.ergspringboot.notifications.CampaignAudience;
import com.erg.ergspringboot.notifications.CampaignStatus;
import com.erg.ergspringboot.notifications.NotificationPriority;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
@EnabledIf("dockerAvailable")
class NotificationCampaignRepositoryIT {

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @Autowired
    private NotificationCampaignRepository campaigns;

    @Autowired
    private NotificationMessageRepository messages;

    @Test
    void audienceJsonRoundTrips() {
        var audience = new CampaignAudience(false, List.of(), List.of(), List.of("c1", "c2"), List.of(), List.of("STUDENT"), List.of());
        String id = campaigns.saveAndFlush(campaign("t1", CampaignStatus.DRAFT, null, audience)).getId();

        var reloaded = campaigns.findById(id).orElseThrow();

        assertThat(reloaded.getAudience().classIds()).hasSize(2);
        assertThat(reloaded.getAudience().roles()).containsExactly("STUDENT");
        assertThat(reloaded.getPriority()).isEqualTo(NotificationPriority.IMPORTANT);
        assertThat(reloaded.isPushEnabled()).isTrue();
        assertThat(reloaded.isRequireReadAck()).isFalse();
    }

    @Test
    void findsOnlyDueScheduledCampaignsOfTheTenant() {
        OffsetDateTime now = OffsetDateTime.now();
        var audience = new CampaignAudience(true, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        String due = campaigns.save(campaign("t1", CampaignStatus.SCHEDULED, now.minusMinutes(5), audience)).getId();
        campaigns.save(campaign("t1", CampaignStatus.SCHEDULED, now.plusHours(1), audience));
        campaigns.save(campaign("t1", CampaignStatus.DRAFT, now.minusMinutes(5), audience));
        campaigns.save(campaign("t2", CampaignStatus.SCHEDULED, now.minusMinutes(5), audience));
        campaigns.flush();

        var result = campaigns.findByTenantIdAndStatusAndScheduledAtLessThanEqual("t1", CampaignStatus.SCHEDULED, now);

        assertThat(result).extracting(NotificationCampaignEntity::getId).containsExactly(due);
    }

    @Test
    void messageKeepsCampaignAndAckColumns() {
        var audience = new CampaignAudience(true, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        String campaignId = campaigns.saveAndFlush(campaign("t1", CampaignStatus.SENT, null, audience)).getId();
        var message = new NotificationMessageEntity();
        message.setTenantId("t1");
        message.setEventType("campaign");
        message.setTitle("t");
        message.setBodyPreview("b");
        message.setCampaignId(campaignId);
        message.setAckedAt(OffsetDateTime.now());
        String id = messages.saveAndFlush(message).getId();

        var reloaded = messages.findById(id).orElseThrow();

        assertThat(reloaded.getCampaignId()).isEqualTo(campaignId);
        assertThat(reloaded.getAckedAt()).isNotNull();
    }

    private static NotificationCampaignEntity campaign(
            String tenant, CampaignStatus status, OffsetDateTime scheduledAt, CampaignAudience audience) {
        var c = new NotificationCampaignEntity();
        c.setTenantId(tenant);
        c.setCreatedBy("admin-1");
        c.setSenderRole("ADMIN");
        c.setTitle("Thong bao");
        c.setBody("Noi dung");
        c.setPriority(NotificationPriority.IMPORTANT);
        c.setAudience(audience);
        c.setStatus(status);
        c.setScheduledAt(scheduledAt);
        return c;
    }

    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
