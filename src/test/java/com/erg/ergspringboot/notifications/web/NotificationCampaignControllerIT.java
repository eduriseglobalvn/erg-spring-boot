package com.erg.ergspringboot.notifications.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.erg.ergspringboot.config.PostgreSqlTestDatabase;
import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.notifications.CampaignAudience;
import com.erg.ergspringboot.notifications.CampaignStatus;
import com.erg.ergspringboot.notifications.NotificationPriority;
import com.erg.ergspringboot.notifications.internal.campaign.ScheduledCampaignDispatchJob;
import com.erg.ergspringboot.notifications.persistence.NotificationCampaignEntity;
import com.erg.ergspringboot.notifications.persistence.NotificationCampaignRepository;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.authorization.Role;
import com.erg.ergspringboot.users.persistence.organization.EducationUnit;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
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

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIf("dockerAvailable")
class NotificationCampaignControllerIT {

    private static final String TENANT = "tenant-camp";
    private static final String CLASS_ID = "class-camp";
    private static final String BASE = "/api/v1/notification-campaigns";

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        PostgreSqlTestDatabase.register(registry);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager entityManager;
    @Autowired private NotificationCampaignRepository campaigns;
    @Autowired private ScheduledCampaignDispatchJob job;

    @BeforeEach
    void seed() {
        EducationUnit school = new EducationUnit();
        school.setId("school-camp");
        school.setTenantId(TENANT);
        school.setName("Campaign School");
        school.setType("school");
        entityManager.persist(school);
        Classroom classroom = new Classroom();
        classroom.setId(CLASS_ID);
        classroom.setTenantId(TENANT);
        classroom.setSchoolId("school-camp");
        classroom.setName("7A");
        classroom.setGrade("7");
        classroom.setAcademicYear("2026");
        classroom.setStatus("active");
        entityManager.persist(classroom);
        entityManager.persist(user("admin-1"));
        for (String id : List.of("stu-1", "stu-2", "stu-3")) {
            entityManager.persist(user(id));
            Student student = new Student();
            student.setId("s-" + id);
            student.setTenantId(TENANT);
            student.setSchoolId("school-camp");
            student.setAcademicClassId(CLASS_ID);
            student.setStudentCode("C-" + id);
            student.setFullName(id);
            student.setUsername(id);
            student.setAuthUserId(id);
            student.setStatus("active");
            entityManager.persist(student);
        }
        entityManager.flush();
    }

    @Test
    void teacherEstimateForAClassTheyDoNotTeachIsForbidden() throws Exception {
        mockMvc.perform(as(post(BASE + "/estimate"), "teacher-1", "teacher").contentType("application/json")
                        .content(classAudienceJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void platformSuperAdminHoldingTheWildcardActsAsAdmin() throws Exception {
        mockMvc.perform(post(BASE + "/estimate").header("X-Tenant-ID", TENANT).header("X-User-ID", "root")
                        .header("X-Roles", "system.super_admin").header("X-Permissions", "*")
                        .contentType("application/json").content(classAudienceJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3));
    }

    @Test
    void withoutTheSendPermissionTheApiIsForbidden() throws Exception {
        mockMvc.perform(get(BASE + "/summary").header("X-Tenant-ID", TENANT).header("X-User-ID", "teacher-1")
                        .header("X-Roles", "teacher"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSendsADraftThenSeesRecipientCountsAndCannotSendTwice() throws Exception {
        String id = createDraft();
        mockMvc.perform(as(post(BASE + "/estimate"), "admin-1", "admin").contentType("application/json")
                        .content(classAudienceJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3));

        mockMvc.perform(as(post(BASE + "/" + id + "/send"), "admin-1", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SENT"));

        mockMvc.perform(as(get(BASE + "/" + id), "admin-1", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recipientCount").value(3))
                .andExpect(jsonPath("$.data.viewedCount").value(0))
                .andExpect(jsonPath("$.data.resolvedCount").value(3));
        mockMvc.perform(as(get(BASE + "/" + id + "/recipients").param("filter", "UNREAD"), "admin-1", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(3))
                .andExpect(jsonPath("$.data.items[0].displayName").value("stu-1"));
        mockMvc.perform(as(get(BASE + "/summary"), "admin-1", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sentThisMonth").value(1))
                .andExpect(jsonPath("$.data.avgViewedRate").value(0.0));
        mockMvc.perform(as(get(BASE).param("box", "SENT"), "admin-1", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(id))
                .andExpect(jsonPath("$.data.items[0].recipientCount").value(3));

        mockMvc.perform(as(post(BASE + "/" + id + "/send"), "admin-1", "admin"))
                .andExpect(status().isConflict());
        mockMvc.perform(as(post(BASE + "/" + id + "/cancel"), "admin-1", "admin"))
                .andExpect(status().isConflict());
    }

    @Test
    void teacherCannotReadAnotherTeachersCampaign() throws Exception {
        NotificationCampaignEntity other = campaigns.saveAndFlush(
                campaign("teacher-2", CampaignStatus.DRAFT, null));

        mockMvc.perform(as(get(BASE + "/" + other.getId()), "teacher-1", "teacher"))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(get(BASE).param("box", "DRAFT").param("mine", "false"), "teacher-1", "teacher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(0));
        mockMvc.perform(as(get(BASE + "/" + other.getId()), "admin-1", "admin"))
                .andExpect(status().isOk());
    }

    @Test
    void scheduleIsBoundedToTheNextThirtyDaysAndCanBeCancelled() throws Exception {
        String id = createDraft();
        mockMvc.perform(schedule(id, OffsetDateTime.now().minusMinutes(1))).andExpect(status().isBadRequest());
        mockMvc.perform(schedule(id, OffsetDateTime.now().plusDays(31))).andExpect(status().isBadRequest());

        mockMvc.perform(schedule(id, OffsetDateTime.now().plusDays(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"));
        mockMvc.perform(as(post(BASE + "/" + id + "/cancel"), "admin-1", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void theJobSendsOnlyDueCampaignsAndNeverTwice() {
        // A real super admin (role system.super_admin holding '*') created both campaigns.
        User creator = user("creator-1");
        creator.getRoles().add(entityManager.find(Role.class, "role_super_admin"));
        entityManager.persist(creator);
        NotificationCampaignEntity due = campaigns.save(
                campaign("creator-1", CampaignStatus.SCHEDULED, OffsetDateTime.now().minusMinutes(1)));
        NotificationCampaignEntity later = campaigns.save(
                campaign("creator-1", CampaignStatus.SCHEDULED, OffsetDateTime.now().plusHours(1)));
        entityManager.flush();

        int firstPass = job.dispatchDue();
        int secondPass = job.dispatchDue();

        entityManager.clear();
        assertThat(firstPass).isGreaterThanOrEqualTo(1);
        assertThat(secondPass).isZero();
        assertThat(campaigns.findById(due.getId()).orElseThrow().getStatus()).isEqualTo(CampaignStatus.SENT);
        assertThat(campaigns.findById(due.getId()).orElseThrow().getResolvedCount()).isEqualTo(3);
        assertThat(campaigns.findById(later.getId()).orElseThrow().getStatus()).isEqualTo(CampaignStatus.SCHEDULED);
    }

    private String createDraft() throws Exception {
        String body = """
                {"title":"Họp phụ huynh","body":"Tối thứ Sáu","priority":"IMPORTANT","requireReadAck":true,
                 "audience":%s}
                """.formatted(classAudienceJson());
        String response = mockMvc.perform(as(post(BASE), "admin-1", "admin").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.estimatedCount").value(3))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.id");
    }

    private MockHttpServletRequestBuilder schedule(String id, OffsetDateTime at) {
        return as(post(BASE + "/" + id + "/schedule"), "admin-1", "admin").contentType("application/json")
                .content("{\"scheduledAt\":\"" + at + "\"}");
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String userId, String role) {
        return request.header("X-Tenant-ID", TENANT)
                .header("X-User-ID", userId)
                .header("X-Roles", role)
                .header("X-Permissions", "notification.campaign.send");
    }

    private static String classAudienceJson() {
        return "{\"wholeTenant\":false,\"classIds\":[\"" + CLASS_ID + "\"]}";
    }

    private static NotificationCampaignEntity campaign(String createdBy, CampaignStatus status, OffsetDateTime at) {
        NotificationCampaignEntity campaign = new NotificationCampaignEntity();
        campaign.setTenantId(TENANT);
        campaign.setCreatedBy(createdBy);
        campaign.setSenderRole("ADMIN");
        campaign.setTitle("Lịch thi");
        campaign.setBody("Nội dung");
        campaign.setPriority(NotificationPriority.INFO);
        campaign.setAudience(new CampaignAudience(false, null, null, List.of(CLASS_ID), null, null, null));
        campaign.setStatus(status);
        campaign.setScheduledAt(at);
        return campaign;
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        user.setTenantId(TENANT);
        user.setEmail(id + "@example.test");
        user.setFullName(id);
        user.setStatus("ACTIVE");
        user.setAccountType("student");
        user.setMustChangePassword(false);
        user.setIsProfileCompleted(true);
        user.setRecoveryEmailVerified(false);
        return user;
    }

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }
}
