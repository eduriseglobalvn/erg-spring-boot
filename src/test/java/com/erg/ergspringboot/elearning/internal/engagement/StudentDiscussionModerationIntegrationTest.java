package com.erg.ergspringboot.elearning.internal.engagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionCommentResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionModerationClassResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionPostResponse;
import com.erg.ergspringboot.config.persistence.FlywayConfig;
import com.erg.ergspringboot.elearning.internal.engagement.StudentDiscussionModerationService.Moderator;
import com.erg.ergspringboot.elearning.internal.engagement.StudentEngagementService.FeedScope;
import com.erg.ergspringboot.elearning.internal.engagement.StudentEngagementService.ReplyCommand;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.lms.persistence.StudentRepository;
import com.erg.ergspringboot.lms.persistence.projection.ClassOptionProjection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** V137 two-level comments, root-normalised replies, hidden filtering for students and moderator class scope. */
@Testcontainers(disabledWithoutDocker = true)
class StudentDiscussionModerationIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("erg_discussion")
            .withUsername("erg_owner")
            .withPassword("erg_owner");

    private static final String STUDENT_USER = "usr_discussion_student";
    private static final String TEACHER_USER = "usr_discussion_teacher";

    private static JdbcTemplate jdbc;
    private static String tenantId;
    private static String classId;

    private final StudentRepository students = mock(StudentRepository.class);
    private final ClassroomRepository classrooms = mock(ClassroomRepository.class);

    @BeforeAll
    static void migrateWithLegacyThirdLevelComments() {
        flyway().target(MigrationVersion.fromVersion("136")).load().migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        Map<String, Object> academicClass = jdbc.queryForMap("select id, tenant_id from academic_classes order by id limit 1");
        classId = (String) academicClass.get("id");
        tenantId = (String) academicClass.get("tenant_id");
        insertPost("post-legacy");
        insertComment("c-root", "post-legacy", null);
        insertComment("c-reply", "post-legacy", "c-root");
        insertComment("c-level3", "post-legacy", "c-reply");
        insertComment("c-level4", "post-legacy", "c-level3");
        flyway().load().migrate();
    }

    @Test
    void migrationAttachesDeeperCommentsToTheirRootComment() {
        assertThat(parentOf("c-root")).isNull();
        assertThat(parentOf("c-reply")).isEqualTo("c-root");
        assertThat(parentOf("c-level3")).isEqualTo("c-root");
        assertThat(parentOf("c-level4")).isEqualTo("c-root");
    }

    @Test
    void replyToAReplyAttachesToTheRootComment() {
        insertPost("post-reply");
        insertComment("r-root", "post-reply", null);
        insertComment("r-child", "post-reply", "r-root");

        StudentDiscussionCommentResponse reply = studentService().reply(new ReplyCommand(
                tenantId, STUDENT_USER, "reply-to-child", "post-reply", "r-child", "Đồng ý", List.of()));

        assertThat(reply.parentId()).isEqualTo("r-root");
        assertThat(parentOf(reply.id())).isEqualTo("r-root");
    }

    @Test
    void hiddenItemsLeaveTheStudentFeedButStayFlaggedForModerators() {
        insertPost("post-hide");
        insertComment("h-root", "post-hide", null);
        insertComment("h-child", "post-hide", "h-root");
        insertPost("post-hidden");
        StudentDiscussionModerationService moderation = moderationService();
        Moderator admin = new Moderator(tenantId, "usr_admin", true);
        long before = studentFeed().size();

        moderation.setHidden(admin, "COMMENT", "h-root", true);
        moderation.setHidden(admin, "POST", "post-hidden", true);

        List<StudentDiscussionPostResponse> studentPosts = studentFeed();
        assertThat(post(studentPosts, "post-hide").comments()).isEmpty();
        assertThat(studentPosts).extracting(StudentDiscussionPostResponse::id).doesNotContain("post-hidden");
        assertThat(studentPosts).hasSize((int) before - 1);
        assertThat(studentService().feed(new FeedScope(tenantId, STUDENT_USER, 0, 50)).totalItems()).isEqualTo(before - 1);
        assertThatThrownBy(() -> studentService().reply(new ReplyCommand(
                tenantId, STUDENT_USER, "reply-hidden", "post-hide", "h-child", "x", List.of())))
                .hasMessageContaining("Parent comment was not found");

        List<StudentDiscussionPostResponse> moderatorPosts = moderation.feed(admin, classId, 0, 50).items();
        StudentDiscussionCommentResponse hiddenRoot = post(moderatorPosts, "post-hide").comments().getFirst();
        assertThat(hiddenRoot.hidden()).isTrue();
        assertThat(hiddenRoot.replies()).extracting(StudentDiscussionCommentResponse::id).containsExactly("h-child");
        assertThat(post(moderatorPosts, "post-hidden").hidden()).isTrue();

        moderation.setHidden(admin, "COMMENT", "h-root", false);
        assertThat(post(studentFeed(), "post-hide").comments()).hasSize(1);
    }

    @Test
    void teacherModeratesOnlyClassesTheyTeach() {
        insertPost("post-scope");
        insertComment("s-root", "post-scope", null);
        insertComment("s-child", "post-scope", "s-root");
        Moderator teacher = new Moderator(tenantId, TEACHER_USER, false);
        StudentDiscussionModerationService moderation = moderationService();

        when(classrooms.findTeacherClassOptions(tenantId, TEACHER_USER)).thenReturn(List.of());
        assertThatThrownBy(() -> moderation.feed(teacher, classId, 0, 20)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> moderation.delete(teacher, "COMMENT", "s-root")).isInstanceOf(ForbiddenException.class);

        when(classrooms.findTeacherClassOptions(tenantId, TEACHER_USER))
                .thenReturn(List.of(new ClassOptionProjection(classId, null, "Lớp", null, null, "ACTIVE")));
        assertThat(moderation.classes(teacher)).extracting(StudentDiscussionModerationClassResponse::id).containsExactly(classId);
        moderation.delete(teacher, "COMMENT", "s-root");
        assertThat(jdbc.queryForObject(
                "select count(*) from student_discussion_comments where post_id = 'post-scope'", Integer.class)).isZero();
    }

    private List<StudentDiscussionPostResponse> studentFeed() {
        return studentService().feed(new FeedScope(tenantId, STUDENT_USER, 0, 50)).items();
    }

    private StudentEngagementService studentService() {
        Student student = new Student();
        student.setId("student-discussion");
        student.setTenantId(tenantId);
        student.setAuthUserId(STUDENT_USER);
        student.setAcademicClassId(classId);
        when(students.findFirstByTenantIdAndAuthUserId(tenantId, STUDENT_USER)).thenReturn(Optional.of(student));
        return new StudentEngagementService(students, jdbc);
    }

    private StudentDiscussionModerationService moderationService() {
        return new StudentDiscussionModerationService(classrooms, jdbc);
    }

    private static StudentDiscussionPostResponse post(List<StudentDiscussionPostResponse> posts, String id) {
        return posts.stream().filter(post -> post.id().equals(id)).findFirst().orElseThrow();
    }

    private static String parentOf(String commentId) {
        return jdbc.queryForObject("select parent_id from student_discussion_comments where id = ?", String.class, commentId);
    }

    private static void insertPost(String id) {
        jdbc.update("""
                insert into student_discussion_threads (id, tenant_id, academic_class_id, title, content, author_id,
                  idempotency_key, latest_activity_at, created_at, updated_at)
                values (?, ?, ?, 'Tiêu đề', 'Nội dung', ?, ?, now(), now(), now())
                """, id, tenantId, classId, STUDENT_USER, id);
    }

    private static void insertComment(String id, String postId, String parentId) {
        jdbc.update("""
                insert into student_discussion_comments (id, tenant_id, post_id, parent_id, author_id, content,
                  idempotency_key, created_at, updated_at)
                values (?, ?, ?, ?, ?, 'Bình luận', ?, clock_timestamp(), clock_timestamp())
                """, id, tenantId, postId, parentId, STUDENT_USER, id);
    }

    private static FluentConfiguration flyway() {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration/postgres")
                .configuration(FlywayConfig.SESSION_LEVEL_LOCK);
    }
}
