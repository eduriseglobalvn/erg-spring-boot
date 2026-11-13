package com.erg.ergspringboot.elearning.internal.engagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.QuestionThreadResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionPostResponse;
import com.erg.ergspringboot.config.persistence.FlywayConfig;
import com.erg.ergspringboot.elearning.ElearningNotice;
import com.erg.ergspringboot.elearning.internal.engagement.StudentDiscussionModerationService.Moderator;
import com.erg.ergspringboot.elearning.internal.engagement.StudentEngagementService.CreateDiscussionCommand;
import com.erg.ergspringboot.elearning.internal.engagement.StudentEngagementService.FeedScope;
import com.erg.ergspringboot.elearning.internal.engagement.StudentEngagementService.ReplyCommand;
import com.erg.ergspringboot.elearning.internal.engagement.StudentQuestionThreadService.Actor;
import com.erg.ergspringboot.elearning.internal.engagement.StudentQuestionThreadService.AskCommand;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.lms.persistence.StudentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** V142: pin, chosen answer, subject, list filters, follow notices; Hộp thư question threads and the answer-visibility gate. */
@Testcontainers(disabledWithoutDocker = true)
class StudentDiscussionPinFollowQuestionThreadIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("erg_pin_follow")
            .withUsername("erg_owner")
            .withPassword("erg_owner");

    private static final String STUDENT_USER = "usr_pf_student";
    private static final String OTHER_USER = "usr_pf_other";
    private static final String STRANGER_TEACHER = "usr_pf_stranger";

    private static JdbcTemplate jdbc;
    private static String tenantId;
    private static String TEACHER_USER;
    private static String studentId;
    private static String classId;

    private final StudentRepository students = mock(StudentRepository.class);
    private final ClassroomRepository classrooms = mock(ClassroomRepository.class);
    private final List<Object> events = new ArrayList<>();

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration/postgres")
                .configuration(FlywayConfig.SESSION_LEVEL_LOCK)
                .load().migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        Map<String, Object> academicClass = jdbc.queryForMap("select id, tenant_id from academic_classes order by id limit 1");
        classId = (String) academicClass.get("id");
        tenantId = (String) academicClass.get("tenant_id");
        studentId = jdbc.queryForObject("select id from lms_students where tenant_id = ? order by id limit 1", String.class, tenantId);
        TEACHER_USER = jdbc.queryForObject("select id from users where tenant_id = ? order by id limit 1", String.class, tenantId);
    }

    @Test
    void pinnedThreadsComeFirstAndOnlyModeratorsPin() {
        StudentEngagementService student = studentService(STUDENT_USER);
        String older = student.createDiscussion(create("pin-1", "Cũ", "Toán")).id();
        String newer = student.createDiscussion(create("pin-2", "Mới", "Toán")).id();
        assertThat(ids(student.feed(new FeedScope(tenantId, STUDENT_USER, 0, 50)).items()).subList(0, 2)).containsExactly(newer, older);

        StudentDiscussionPostResponse pinned = moderation().setPinned(new Moderator(tenantId, TEACHER_USER, true), older, true);

        assertThat(pinned.pinned()).isTrue();
        assertThat(ids(student.feed(new FeedScope(tenantId, STUDENT_USER, 0, 50)).items()).getFirst()).isEqualTo(older);
        assertThatThrownBy(() -> moderation().setPinned(new Moderator(tenantId, STRANGER_TEACHER, false), older, false))
                .isInstanceOf(ForbiddenException.class);
        assertThat(moderation().setPinned(new Moderator(tenantId, TEACHER_USER, true), older, false).pinned()).isFalse();
    }

    @Test
    void teacherChoosesAReplyAndHidingItClearsTheChoice() {
        StudentEngagementService student = studentService(STUDENT_USER);
        String post = student.createDiscussion(create("acc-1", "Câu hỏi", "Lý")).id();
        String other = student.createDiscussion(create("acc-2", "Câu khác", "Lý")).id();
        String reply = student.reply(new ReplyCommand(tenantId, STUDENT_USER, "acc-r1", post, null, "Lời giải", List.of())).id();
        String foreign = student.reply(new ReplyCommand(tenantId, STUDENT_USER, "acc-r2", other, null, "Lạc đề", List.of())).id();
        Moderator admin = new Moderator(tenantId, TEACHER_USER, true);

        assertThat(moderation().acceptReply(admin, post, reply).acceptedReplyId()).isEqualTo(reply);
        assertThatThrownBy(() -> moderation().acceptReply(admin, post, foreign)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(findPost(student.feed(new FeedScope(tenantId, STUDENT_USER, 0, 50)).items(), post).acceptedReplyId()).isEqualTo(reply);

        moderation().setHidden(admin, "COMMENT", reply, true);
        assertThat(moderation().acceptReply(admin, post, null).acceptedReplyId()).isNull();
        assertThat(jdbc.queryForObject("select accepted_comment_id from student_discussion_threads where id = ?", String.class, post)).isNull();
    }

    @Test
    void feedFiltersByUnansweredMineAndSubject() {
        StudentEngagementService student = studentService(STUDENT_USER);
        StudentEngagementService other = studentService(OTHER_USER);
        String open = student.createDiscussion(create("flt-1", "Chưa ai trả lời", "Hóa")).id();
        String answered = student.createDiscussion(create("flt-2", "Đã có người trả lời", "Hóa")).id();
        String theirs = other.createDiscussion(create(OTHER_USER, "flt-3", "Của bạn khác", "Sinh")).id();
        other.reply(new ReplyCommand(tenantId, OTHER_USER, "flt-r", answered, null, "Trả lời", List.of()));

        assertThat(ids(feed(student, "UNANSWERED", null))).contains(open, theirs).doesNotContain(answered);
        assertThat(ids(feed(student, "MINE", null))).contains(open, answered).doesNotContain(theirs);
        assertThat(ids(feed(student, "ALL", "Sinh"))).containsExactly(theirs);
        assertThat(student.feed(new FeedScope(tenantId, STUDENT_USER, 0, 50, "MINE", "Hóa")).totalItems()).isEqualTo(2);
        assertThatThrownBy(() -> feed(student, "BOGUS", null)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void followersAreNoticedOfNewRepliesExceptTheirOwn() {
        StudentEngagementService student = studentService(STUDENT_USER);
        StudentEngagementService other = studentService(OTHER_USER);
        String post = student.createDiscussion(create("fol-1", "Theo dõi", "Anh")).id();
        assertThat(student.follow(tenantId, STUDENT_USER, post, true)).isTrue();
        student.follow(tenantId, STUDENT_USER, post, true);
        assertThat(findPost(student.feed(new FeedScope(tenantId, STUDENT_USER, 0, 50)).items(), post).following()).isTrue();
        assertThat(findPost(other.feed(new FeedScope(tenantId, OTHER_USER, 0, 50)).items(), post).following()).isFalse();

        other.reply(new ReplyCommand(tenantId, OTHER_USER, "fol-r1", post, null, "Mình trả lời", List.of()));
        student.reply(new ReplyCommand(tenantId, STUDENT_USER, "fol-r2", post, null, "Tự trả lời", List.of()));

        assertThat(events).hasSize(1);
        ElearningNotice notice = (ElearningNotice) events.getFirst();
        assertThat(notice.recipientUserIds()).containsExactly(STUDENT_USER);
        assertThat(notice.sourceId()).isEqualTo(post);

        student.follow(tenantId, STUDENT_USER, post, false);
        assertThat(findPost(student.feed(new FeedScope(tenantId, STUDENT_USER, 0, 50)).items(), post).following()).isFalse();
    }

    @Test
    void studentAsksTeacherAboutAQuestionAndTheTeacherAnswers() {
        seedQuiz("qa", true, true);
        StudentQuestionThreadService service = threadService(STUDENT_USER);
        AskCommand ask = new AskCommand("ask-1", "asg-qa", 2, "2x + 5 = 13", "x = 9", "Em không hiểu câu này ạ", List.of("img-1"));

        QuestionThreadResponse thread = service.ask(new Actor(tenantId, STUDENT_USER, false), ask);

        assertThat(thread.status()).isEqualTo("OPEN");
        assertThat(thread.teacherId()).isEqualTo(TEACHER_USER);
        assertThat(thread.questionIndex()).isEqualTo(2);
        assertThat(thread.studentAnswerText()).isEqualTo("x = 9");
        assertThat(thread.correctAnswerText()).isEqualTo("x = 4");
        assertThat(thread.messages()).hasSize(1);
        assertThat(thread.messages().getFirst().attachmentIds()).containsExactly("img-1");
        assertThat(thread.messages().getFirst().mine()).isTrue();
        assertThat(service.ask(new Actor(tenantId, STUDENT_USER, false), ask).id()).isEqualTo(thread.id());
        assertThat(service.list(new Actor(tenantId, TEACHER_USER, false), true, "OPEN")).extracting(QuestionThreadResponse::id).contains(thread.id());
        assertThat(service.list(new Actor(tenantId, STRANGER_TEACHER, false), true, null)).isEmpty();
        assertThat(events).hasSize(1);

        QuestionThreadResponse answered = service.send(new Actor(tenantId, TEACHER_USER, false), thread.id(), "Em thử chuyển vế nhé", List.of());
        assertThat(answered.status()).isEqualTo("ANSWERED");
        assertThat(answered.messages()).extracting(m -> m.senderRole()).containsExactly("STUDENT", "TEACHER");
        assertThat(answered.messages().getLast().mine()).isTrue();

        QuestionThreadResponse thanks = service.send(new Actor(tenantId, STUDENT_USER, false), thread.id(), "Em cảm ơn cô ạ!", List.of());
        assertThat(thanks.status()).isEqualTo("ANSWERED");
        assertThat(thanks.messages()).hasSize(3);
        assertThat(service.list(new Actor(tenantId, STUDENT_USER, false), false, "ANSWERED")).hasSize(1);
        assertThatThrownBy(() -> service.detail(new Actor(tenantId, OTHER_USER, false), thread.id())).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.send(new Actor(tenantId, OTHER_USER, false), thread.id(), "chen ngang", List.of()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void correctAnswerStaysHiddenFromTheStudentUntilTheQuizLetsThemSeeIt() {
        seedQuiz("hid", false, true);
        seedQuiz("nosub", true, false);
        StudentQuestionThreadService service = threadService(STUDENT_USER);

        QuestionThreadResponse hidden = service.ask(new Actor(tenantId, STUDENT_USER, false),
                new AskCommand("ask-hid", "asg-hid", 1, null, null, "Câu 1?", null));
        QuestionThreadResponse notHandedIn = service.ask(new Actor(tenantId, STUDENT_USER, false),
                new AskCommand("ask-nosub", "asg-nosub", 1, null, null, "Câu 1?", null));

        assertThat(hidden.correctAnswerText()).isNull();
        assertThat(notHandedIn.correctAnswerText()).isNull();
        assertThat(service.detail(new Actor(tenantId, TEACHER_USER, false), hidden.id()).correctAnswerText()).isEqualTo("x = 4");
        assertThatThrownBy(() -> service.ask(new Actor(tenantId, STUDENT_USER, false),
                new AskCommand("ask-x", "asg-missing", 1, null, null, "?", null))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.ask(new Actor(tenantId, STUDENT_USER, false),
                new AskCommand("ask-0", "asg-hid", 0, null, null, "?", null))).isInstanceOf(BadRequestException.class);
    }

    private void seedQuiz(String key, boolean showAnswers, boolean handedIn) {
        String quizId = "quiz-" + key;
        String versionId = "ver-" + key;
        jdbc.update("""
                insert into lms_quizzes (id, created_at, updated_at, tenant_id, title, kind, settings, status)
                values (?, now(), now(), ?, 'Phương trình', 'practice', cast(? as jsonb), 'PUBLISHED')
                """, quizId, tenantId, "{\"showAnswersAfterSubmit\": " + showAnswers + "}");
        jdbc.update("""
                insert into lms_assignments (id, created_at, updated_at, tenant_id, academic_class_id, quiz_id, status, assigned_by)
                values (?, now(), now(), ?, ?, ?, 'ASSIGNED', ?)
                """, "asg-" + key, tenantId, classId, quizId, TEACHER_USER);
        jdbc.update("""
                insert into quiz_versions (id, created_at, updated_at, quiz_id, tenant_id, version_label, version_number)
                values (?, now(), now(), ?, ?, 'v1', 1)
                """, versionId, quizId, tenantId);
        for (int i = 0; i < 2; i++) {
            jdbc.update("""
                    insert into quiz_version_questions (id, created_at, updated_at, quiz_version_id, tenant_id, runtime_question_id,
                      kind, order_index, body, answer_key)
                    values (?, now(), now(), ?, ?, ?, 'single_choice', ?, cast(? as jsonb), cast(? as jsonb))
                    """, versionId + "-q" + i, versionId, tenantId, "rq" + i, i,
                    "{\"choices\": [{\"id\": \"a\", \"label\": \"x = 9\"}, {\"id\": \"b\", \"label\": \"x = 4\"}]}",
                    "{\"correctChoiceId\": \"b\"}");
        }
        if (handedIn) {
            jdbc.update("""
                    insert into lms_attempts (id, tenant_id, assignment_id, quiz_id, student_id, status, quiz_version_id, submitted_at, updated_at)
                    values (?, ?, ?, ?, ?, 'submitted', ?, now(), now())
                    """, "att-" + key, tenantId, "asg-" + key, quizId, studentId, versionId);
        }
    }

    private StudentEngagementService.CreateDiscussionCommand create(String key, String title, String subject) {
        return create(STUDENT_USER, key, title, subject);
    }

    private StudentEngagementService.CreateDiscussionCommand create(String user, String key, String title, String subject) {
        return new CreateDiscussionCommand(tenantId, user, key, title, "Nội dung", null, List.of(), subject);
    }

    private List<StudentDiscussionPostResponse> feed(StudentEngagementService service, String filter, String subject) {
        return service.feed(new FeedScope(tenantId, STUDENT_USER, 0, 50, filter, subject)).items();
    }

    private StudentEngagementService studentService(String user) {
        registerStudent(user);
        return new StudentEngagementService(students, jdbc, events::add);
    }

    private StudentQuestionThreadService threadService(String user) {
        registerStudent(user);
        return new StudentQuestionThreadService(students, jdbc, events::add);
    }

    private void registerStudent(String user) {
        Student student = new Student();
        student.setId(studentId);
        student.setTenantId(tenantId);
        student.setAuthUserId(user);
        student.setAcademicClassId(classId);
        when(students.findFirstByTenantIdAndAuthUserId(tenantId, user)).thenReturn(Optional.of(student));
    }

    private StudentDiscussionModerationService moderation() {
        return new StudentDiscussionModerationService(classrooms, jdbc);
    }

    private static List<String> ids(List<StudentDiscussionPostResponse> posts) {
        return posts.stream().map(StudentDiscussionPostResponse::id).toList();
    }

    private static StudentDiscussionPostResponse findPost(List<StudentDiscussionPostResponse> posts, String id) {
        return posts.stream().filter(post -> post.id().equals(id)).findFirst().orElseThrow();
    }
}
