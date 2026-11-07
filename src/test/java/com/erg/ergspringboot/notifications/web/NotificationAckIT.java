package com.erg.ergspringboot.notifications.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import com.erg.ergspringboot.notifications.CampaignAudience;
import com.erg.ergspringboot.notifications.CampaignStatus;
import com.erg.ergspringboot.notifications.NotificationPriority;
import com.erg.ergspringboot.notifications.persistence.NotificationCampaignEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationMessageEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationRecipientEntity;
import com.erg.ergspringboot.users.persistence.User;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;

/** Recipient inbox for campaign messages: cross-portal visibility, new fields and read acknowledgement. */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIf("dockerAvailable")
class NotificationAckIT {

    private static final String TENANT = "tenant-ack";
    private static final String OTHER_TENANT = "tenant-ack-other";
    private static final String BASE = "/api/v1/notifications";

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager entityManager;

    private String ackMessage;
    private String infoMessage;
    private String legacyMessage;
    private String otherUsersMessage;

    @BeforeEach
    void seed() {
        entityManager.persist(user("admin-ack", "Cô Hoa"));
        entityManager.persist(user("stu-ack", "Học sinh A"));
        entityManager.persist(user("stu-other", "Học sinh B"));
        NotificationCampaignEntity needsAck = campaign(true, NotificationPriority.URGENT);
        NotificationCampaignEntity noAck = campaign(false, NotificationPriority.INFO);
        entityManager.persist(needsAck);
        entityManager.persist(noAck);
        entityManager.flush();
        ackMessage = deliver("stu-ack", needsAck.getId(), "URGENT", "lms");
        otherUsersMessage = deliver("stu-other", needsAck.getId(), "URGENT", "lms");
        infoMessage = deliver("stu-ack", noAck.getId(), "INFO", "lms");
        legacyMessage = deliver("stu-ack", null, "high", "lms");
        entityManager.flush();
    }

    @Test
    void studentOnElearningSeesCampaignRowsWithTheNewFieldsButNotLegacyLmsRows() throws Exception {
        String body = mockMvc.perform(student(get(BASE).param("portal", "elearning")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(2))
                .andReturn().getResponse().getContentAsString();

        List<String> ids = JsonPath.read(body, "$.data.items[*].id");
        assertThat(ids).containsExactlyInAnyOrder(ackMessage, infoMessage).doesNotContain(legacyMessage, otherUsersMessage);

        mockMvc.perform(student(get(BASE + "/" + ackMessage).param("portal", "elearning")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priority").value("URGENT"))
                .andExpect(jsonPath("$.data.priorityLevel").value("URGENT"))
                .andExpect(jsonPath("$.data.requireReadAck").value(true))
                .andExpect(jsonPath("$.data.campaignId").isNotEmpty())
                .andExpect(jsonPath("$.data.senderName").value("Cô Hoa"))
                .andExpect(jsonPath("$.data.ackedAt").doesNotExist());
        mockMvc.perform(student(get(BASE + "/unread-count").param("portal", "elearning")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unread").value(2));
    }

    @Test
    void lmsSessionStillSeesLegacyRowsWithNormalisedPriorityAndNoCampaignFields() throws Exception {
        mockMvc.perform(as(get(BASE + "/" + legacyMessage), "stu-ack", TENANT, "lms").param("portal", "lms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priority").value("high"))
                .andExpect(jsonPath("$.data.priorityLevel").value("IMPORTANT"))
                .andExpect(jsonPath("$.data.requireReadAck").value(false))
                .andExpect(jsonPath("$.data.campaignId").doesNotExist())
                .andExpect(jsonPath("$.data.senderName").doesNotExist());
        mockMvc.perform(student(get(BASE + "/" + legacyMessage).param("portal", "elearning")))
                .andExpect(status().isNotFound());
    }

    @Test
    void ackSetsAckedAtOnceMarksReadAndIsIdempotent() throws Exception {
        String first = mockMvc.perform(student(post(BASE + "/" + ackMessage + "/ack").param("portal", "elearning")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ackMessage))
                .andExpect(jsonPath("$.data.ackedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.readAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        entityManager.flush();
        entityManager.clear();
        String second = mockMvc.perform(student(post(BASE + "/" + ackMessage + "/ack").param("portal", "elearning")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String firstAckedAt = JsonPath.read(first, "$.data.ackedAt");
        String secondAckedAt = JsonPath.read(second, "$.data.ackedAt");
        assertThat(java.time.OffsetDateTime.parse(secondAckedAt).toInstant())
                .isEqualTo(java.time.OffsetDateTime.parse(firstAckedAt).toInstant());
        mockMvc.perform(student(get(BASE + "/" + ackMessage).param("portal", "elearning")))
                .andExpect(jsonPath("$.data.status").value("read"))
                .andExpect(jsonPath("$.data.ackedAt").isNotEmpty());
    }

    @Test
    void ackOfAnotherUsersMessageOrFromAnotherTenantIsNotFound() throws Exception {
        mockMvc.perform(student(post(BASE + "/" + otherUsersMessage + "/ack").param("portal", "elearning")))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(post(BASE + "/" + ackMessage + "/ack"), "stu-ack", OTHER_TENANT, "elearning")
                        .param("portal", "elearning"))
                .andExpect(status().isNotFound());
        mockMvc.perform(student(patch(BASE + "/" + otherUsersMessage + "/read").param("portal", "elearning")))
                .andExpect(status().isNotFound());
    }

    @Test
    void ackOfAMessageThatDoesNotRequireItIsAConflict() throws Exception {
        mockMvc.perform(student(post(BASE + "/" + infoMessage + "/ack").param("portal", "elearning")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Thông báo này không yêu cầu xác nhận đã đọc"));
        mockMvc.perform(as(post(BASE + "/" + legacyMessage + "/ack"), "stu-ack", TENANT, "lms").param("portal", "lms"))
                .andExpect(status().isConflict());
    }

    @Test
    void markReadAndReadAllReachCampaignRowsFromTheStudentPortal() throws Exception {
        mockMvc.perform(student(patch(BASE + "/" + infoMessage + "/read").param("portal", "elearning")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(1));
        mockMvc.perform(student(patch(BASE + "/read-all").param("portal", "elearning")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(1));
        mockMvc.perform(student(get(BASE + "/unread-count").param("portal", "elearning")))
                .andExpect(jsonPath("$.data.unread").value(0));
        // The legacy lms row is untouched by an elearning read-all.
        mockMvc.perform(as(get(BASE + "/unread-count"), "stu-ack", TENANT, "lms").param("portal", "lms"))
                .andExpect(jsonPath("$.data.unread").value(1));
        // Another user's campaign row of the same campaign stays unread.
        mockMvc.perform(as(get(BASE + "/unread-count"), "stu-other", TENANT, "elearning").param("portal", "elearning"))
                .andExpect(jsonPath("$.data.unread").value(1));
    }

    @Test
    void archiveFromTheStudentPortalWorksOnlyForTheOwnCampaignRow() throws Exception {
        mockMvc.perform(student(delete(BASE + "/" + otherUsersMessage).param("portal", "elearning")))
                .andExpect(status().isNotFound());
        mockMvc.perform(student(delete(BASE + "/" + infoMessage).param("portal", "elearning")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.archived").value(true));

        mockMvc.perform(student(get(BASE + "/" + infoMessage).param("portal", "elearning")))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(get(BASE + "/" + otherUsersMessage), "stu-other", TENANT, "elearning").param("portal", "elearning"))
                .andExpect(status().isOk());
    }

    private String deliver(String userId, String campaignId, String priority, String portal) {
        NotificationMessageEntity message = new NotificationMessageEntity();
        message.setTenantId(TENANT);
        message.setEventType(campaignId == null ? "SCHEDULE_PUBLISHED" : "CAMPAIGN");
        message.setCampaignId(campaignId);
        message.setTitle("Họp phụ huynh");
        message.setBodyPreview("Tối thứ 6");
        message.setPriority(priority);
        message.setCreatedBy(campaignId == null ? null : "admin-ack");
        entityManager.persist(message);
        NotificationRecipientEntity recipient = new NotificationRecipientEntity();
        recipient.setTenantId(TENANT);
        recipient.setNotificationId(message.getId());
        recipient.setUserId(userId);
        recipient.setPortal(portal);
        entityManager.persist(recipient);
        return message.getId();
    }

    private static NotificationCampaignEntity campaign(boolean requireReadAck, NotificationPriority priority) {
        NotificationCampaignEntity campaign = new NotificationCampaignEntity();
        campaign.setTenantId(TENANT);
        campaign.setCreatedBy("admin-ack");
        campaign.setSenderRole("ADMIN");
        campaign.setTitle("Họp phụ huynh");
        campaign.setBody("Tối thứ 6");
        campaign.setPriority(priority);
        campaign.setRequireReadAck(requireReadAck);
        campaign.setAudience(new CampaignAudience(false, null, null, List.of("class-x"), null, null, null));
        campaign.setStatus(CampaignStatus.SENT);
        return campaign;
    }

    private static MockHttpServletRequestBuilder student(MockHttpServletRequestBuilder request) {
        return as(request, "stu-ack", TENANT, "elearning");
    }

    private static MockHttpServletRequestBuilder as(
            MockHttpServletRequestBuilder request, String userId, String tenant, String portal) {
        return request.header("X-Tenant-ID", tenant)
                .header("X-User-ID", userId)
                .header("X-Roles", "student")
                .header("X-Permissions", "profile.self")
                .header("X-Portals", portal);
    }

    private static User user(String id, String fullName) {
        User user = new User();
        user.setId(id);
        user.setTenantId(TENANT);
        user.setEmail(id + "@example.test");
        user.setFullName(fullName);
        user.setStatus("ACTIVE");
        user.setAccountType("staff");
        user.setMustChangePassword(false);
        user.setIsProfileCompleted(true);
        user.setRecoveryEmailVerified(false);
        return user;
    }

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }
}
