package com.erg.ergspringboot.elearning.internal.engagement;

import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.QuestionMessageResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.QuestionThreadResponse;
import com.erg.ergspringboot.elearning.ElearningNotice;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.lms.LmsStatePolicy;
import com.erg.ergspringboot.lms.LmsStudentIdentityLookup;
import com.erg.ergspringboot.lms.LmsStudentIdentityLookup.StudentIdentity;
import com.erg.ergspringboot.utils.IdGenerator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hộp thư "Hỏi giáo viên": a student asks the assignment's teacher about one question and they exchange messages.
 * The student sees their own threads; the teacher the threads addressed to them (a tenant-wide admin all of them).
 */
@Service
public class StudentQuestionThreadService {

    private static final int LIST_LIMIT = 50;

    private final LmsStudentIdentityLookup identities;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final ApplicationEventPublisher events;

    public StudentQuestionThreadService(LmsStudentIdentityLookup identities, JdbcTemplate jdbc, ApplicationEventPublisher events) {
        this.identities = identities;
        this.jdbc = jdbc;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
        this.events = events;
    }

    @Transactional
    public QuestionThreadResponse ask(Actor actor, AskCommand command) {
        StudentIdentity student = identities.byAuthUser(actor.tenantId(), actor.userId());
        if (!student.exists() || student.academicClassId() == null || student.academicClassId().isBlank()) {
            throw new ResourceNotFoundException("Authenticated student enrollment was not found.");
        }
        requireText(command.idempotencyKey(), "idempotencyKey", 192);
        requireText(command.assignmentId(), "assignmentId", 36);
        requireText(command.message(), "message", 4000);
        if (command.questionIndex() < 1) throw new BadRequestException("questionIndex starts at 1.");
        if (command.studentAnswerText() != null && command.studentAnswerText().length() > 2000) {
            throw new BadRequestException("studentAnswerText exceeds 2000 characters.");
        }
        String existing = jdbc.query("select id from student_question_threads where tenant_id = ? and student_user_id = ? and idempotency_key = ?",
                rs -> rs.next() ? rs.getString(1) : null, actor.tenantId(), actor.userId(), command.idempotencyKey().trim());
        if (existing != null) return detail(actor, existing);
        String teacherId = jdbc.query("""
                select a.assigned_by from lms_assignments a
                where a.tenant_id = ? and a.id = ? and a.academic_class_id = ?
                  and (a.student_ids is null or jsonb_array_length(a.student_ids) = 0 or jsonb_exists(a.student_ids, ?))
                """, rs -> rs.next() ? rs.getString(1) : null,
                actor.tenantId(), command.assignmentId().trim(), student.academicClassId(), student.studentId());
        if (teacherId == null) throw new ResourceNotFoundException("Assignment was not found for the student.");
        String id = IdGenerator.newId();
        OffsetDateTime now = OffsetDateTime.now();
        try {
            jdbc.update("""
                    insert into student_question_threads
                      (id, tenant_id, student_id, student_user_id, academic_class_id, teacher_id, assignment_id, question_index,
                       question_prompt, student_answer_text, status, idempotency_key, last_message_at, created_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?, ?)
                    """, id, actor.tenantId(), student.studentId(), actor.userId(), student.academicClassId(), teacherId,
                    command.assignmentId().trim(), command.questionIndex(), blank(command.questionPrompt()),
                    blank(command.studentAnswerText()), command.idempotencyKey().trim(), now, now);
        } catch (DataIntegrityViolationException race) {
            String raced = jdbc.query("select id from student_question_threads where tenant_id = ? and student_user_id = ? and idempotency_key = ?",
                    rs -> rs.next() ? rs.getString(1) : null, actor.tenantId(), actor.userId(), command.idempotencyKey().trim());
            if (raced != null) return detail(actor, raced);
            throw race;
        }
        insertMessage(actor.tenantId(), id, actor.userId(), "STUDENT", command.message(), command.attachmentIds(), now);
        notifyOther(actor, id, "QUESTION_ASKED", teacherId, command.message());
        return detail(actor, id);
    }

    @Transactional
    public QuestionThreadResponse send(Actor actor, String threadId, String body, List<String> attachmentIds) {
        requireText(body, "message", 4000);
        ThreadRow thread = requireVisible(actor, threadId);
        boolean teacherSide = !actor.userId().equals(thread.studentUserId());
        OffsetDateTime now = OffsetDateTime.now();
        insertMessage(actor.tenantId(), thread.id(), actor.userId(), teacherSide ? "TEACHER" : "STUDENT", body, attachmentIds, now);
        // ANSWERED is sticky: a thank-you or follow-up from the student does not take the chip back.
        jdbc.update("update student_question_threads set last_message_at = ?, status = case when ? then 'ANSWERED' else status end where tenant_id = ? and id = ?",
                now, teacherSide, actor.tenantId(), thread.id());
        notifyOther(actor, thread.id(), teacherSide ? "QUESTION_ANSWERED" : "QUESTION_FOLLOW_UP",
                teacherSide ? thread.studentUserId() : thread.teacherId(), body);
        return detail(actor, thread.id());
    }

    /** Student: own threads. Teacher side: threads addressed to the viewer (all of the tenant for a tenant-wide admin). */
    @Transactional(readOnly = true)
    public List<QuestionThreadResponse> list(Actor actor, boolean teacherSide, String status) {
        return list(actor, teacherSide, status, true);
    }

    /** {@code includeCorrectAnswer = false} skips the quiz-version lookup of every row's {@code correctAnswerText} (left null). */
    @Transactional(readOnly = true)
    public List<QuestionThreadResponse> list(Actor actor, boolean teacherSide, String status, boolean includeCorrectAnswer) {
        String wanted = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (wanted != null && !Set.of("OPEN", "ANSWERED").contains(wanted)) throw new BadRequestException("status must be OPEN or ANSWERED.");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", actor.tenantId())
                .addValue("userId", actor.userId()).addValue("status", wanted).addValue("limit", LIST_LIMIT);
        String scope = !teacherSide ? "t.student_user_id = :userId" : actor.tenantWide() ? "true" : "t.teacher_id = :userId";
        List<ThreadRow> rows = namedJdbc.query(THREAD_SELECT + " where t.tenant_id = :tenantId and " + scope
                + " and (cast(:status as varchar) is null or t.status = :status) order by t.last_message_at desc limit :limit",
                params, this::mapThread);
        return toListResponses(actor, rows, includeCorrectAnswer);
    }

    /** The list view of {@link #toResponse}: one query per kind for every row instead of about four per row. */
    private List<QuestionThreadResponse> toListResponses(Actor actor, List<ThreadRow> rows, boolean includeCorrectAnswer) {
        if (rows.isEmpty()) return List.of();
        MapSqlParameterSource byThread = new MapSqlParameterSource("tenantId", actor.tenantId())
                .addValue("ids", rows.stream().map(ThreadRow::id).toList());
        Map<String, String> previews = new HashMap<>();
        namedJdbc.query("""
                select distinct on (m.thread_id) m.thread_id, left(m.body, 120) preview from student_question_messages m
                where m.tenant_id = :tenantId and m.thread_id in (:ids) order by m.thread_id, m.created_at desc, m.id desc
                """, byThread, rs -> { previews.put(rs.getString(1), rs.getString(2)); });
        Map<String, OffsetDateTime> lastTeacherAt = new HashMap<>();
        namedJdbc.query("""
                select thread_id, max(created_at) from student_question_messages
                where tenant_id = :tenantId and thread_id in (:ids) and sender_role = 'TEACHER' group by thread_id
                """, byThread, rs -> { lastTeacherAt.put(rs.getString(1), rs.getObject(2, OffsetDateTime.class)); });
        Map<String, String> correct = includeCorrectAnswer ? correctAnswers(actor, rows) : Map.of();
        return rows.stream().map(t -> {
            boolean studentSide = actor.userId().equals(t.studentUserId());
            OffsetDateTime teacherAt = lastTeacherAt.get(t.id());
            boolean unread = studentSide && teacherAt != null && (t.studentReadAt() == null || teacherAt.isAfter(t.studentReadAt()));
            return new QuestionThreadResponse(t.id(), t.assignmentId(), t.assignmentTitle(), t.subjectName(), t.className(),
                    t.questionIndex(), t.questionPrompt(), t.studentAnswerText(), correct.get(t.id()), t.teacherId(),
                    t.teacherName(), t.studentName(), t.status(), t.lastMessageAt(), previews.get(t.id()), List.of(), unread);
        }).toList();
    }

    /** Thread id to the text of the correct answer, for the rows that may show it (same rules as {@link #answersVisible}). */
    private Map<String, String> correctAnswers(Actor actor, List<ThreadRow> rows) {
        String tenantId = actor.tenantId();
        Set<String> studentSideAssignments = new HashSet<>();
        rows.stream().filter(t -> actor.userId().equals(t.studentUserId())).forEach(t -> studentSideAssignments.add(t.assignmentId()));
        Set<String> showing = new HashSet<>();
        if (!studentSideAssignments.isEmpty()) {
            namedJdbc.query("""
                    select a.id from lms_assignments a join lms_quizzes q on q.id = a.quiz_id and q.tenant_id = a.tenant_id
                    where a.tenant_id = :tenantId and a.id in (:ids)
                      and coalesce(q.settings ->> 'showAnswersAfterSubmit', q.settings -> 'runtime' ->> 'showAnswersAfterSubmit') = 'true'
                    """, new MapSqlParameterSource("tenantId", tenantId).addValue("ids", studentSideAssignments),
                    rs -> { showing.add(rs.getString(1)); });
        }
        Map<String, String> versions = new HashMap<>();
        namedJdbc.query("""
                select distinct on (assignment_id, student_id) assignment_id, student_id, quiz_version_id from lms_attempts
                where tenant_id = :tenantId and assignment_id in (:aids) and student_id in (:sids)
                  and quiz_version_id is not null and lower(status) in (:states)
                order by assignment_id, student_id, submitted_at desc nulls last
                """, new MapSqlParameterSource("tenantId", tenantId)
                        .addValue("aids", rows.stream().map(ThreadRow::assignmentId).distinct().toList())
                        .addValue("sids", rows.stream().map(ThreadRow::studentId).distinct().toList())
                        .addValue("states", LmsStatePolicy.completedAttemptStates()),
                rs -> { versions.put(rs.getString(1) + '|' + rs.getString(2), rs.getString(3)); });
        Map<String, String> versionOf = new HashMap<>();
        for (ThreadRow t : rows) {
            boolean studentSide = actor.userId().equals(t.studentUserId());
            String version = versions.get(t.assignmentId() + '|' + t.studentId());
            if (version != null && (!studentSide || showing.contains(t.assignmentId()))) versionOf.put(t.id(), version);
        }
        if (versionOf.isEmpty()) return Map.of();
        Map<String, String> texts = new HashMap<>();
        namedJdbc.query("""
                -- the window ranks ids only; body and answer key are read for the matching rows alone
                select x.quiz_version_id, x.rn, q.body::text, q.answer_key::text from (
                  select id, quiz_version_id, row_number() over (partition by quiz_version_id order by order_index, id) rn
                  from quiz_version_questions where tenant_id = :tenantId and quiz_version_id in (:vids)) x
                join quiz_version_questions q on q.tenant_id = :tenantId and q.id = x.id
                where x.rn in (:positions)
                """, new MapSqlParameterSource("tenantId", tenantId).addValue("vids", Set.copyOf(versionOf.values()))
                        .addValue("positions", rows.stream().map(ThreadRow::questionIndex).distinct().toList()),
                rs -> { texts.put(rs.getString(1) + '#' + rs.getInt(2), QuestionAnswerText.of(rs.getString(3), rs.getString(4))); });
        Map<String, String> result = new HashMap<>();
        for (ThreadRow t : rows) {
            String text = versionOf.containsKey(t.id()) ? texts.get(versionOf.get(t.id()) + '#' + t.questionIndex()) : null;
            if (text != null) result.put(t.id(), text);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public QuestionThreadResponse detail(Actor actor, String threadId) {
        return toResponse(actor, requireVisible(actor, threadId), true);
    }

    /** Student only: marks the thread as read up to now. */
    @Transactional
    public boolean markRead(Actor actor, String threadId) {
        ThreadRow thread = requireVisible(actor, threadId);
        if (!actor.userId().equals(thread.studentUserId())) throw new ForbiddenException("Only the student of the thread can mark it read.");
        jdbc.update("update student_question_threads set student_read_at = ? where tenant_id = ? and id = ?",
                OffsetDateTime.now(), actor.tenantId(), thread.id());
        return true;
    }

    private QuestionThreadResponse toResponse(Actor actor, ThreadRow t, boolean withMessages) {
        List<QuestionMessageResponse> messages = jdbc.query("""
                select m.id, m.sender_id, coalesce(u.full_name, '') sender_name, m.sender_role, m.body,
                       m.attachment_ids::text, m.created_at
                from student_question_messages m left join users u on u.id = m.sender_id and u.tenant_id = m.tenant_id
                where m.tenant_id = ? and m.thread_id = ? order by m.created_at, m.id
                """, (rs, row) -> new QuestionMessageResponse(rs.getString("id"), rs.getString("sender_id"),
                        rs.getString("sender_name"), rs.getString("sender_role"), actor.userId().equals(rs.getString("sender_id")),
                        rs.getString("body"), parseJsonList(rs.getString("attachment_ids")),
                        rs.getObject("created_at", OffsetDateTime.class)), actor.tenantId(), t.id());
        QuestionMessageResponse last = messages.isEmpty() ? null : messages.getLast();
        String preview = last == null ? null : last.body().length() > 120 ? last.body().substring(0, 120) : last.body();
        boolean studentSide = actor.userId().equals(t.studentUserId());
        boolean unread = studentSide && messages.stream().anyMatch(m -> "TEACHER".equals(m.senderRole())
                && (t.studentReadAt() == null || m.createdAt().isAfter(t.studentReadAt())));
        String correct = !studentSide || answersVisible(actor.tenantId(), t) ? correctAnswerText(actor.tenantId(), t) : null;
        return new QuestionThreadResponse(t.id(), t.assignmentId(), t.assignmentTitle(), t.subjectName(), t.className(),
                t.questionIndex(), t.questionPrompt(), t.studentAnswerText(), correct, t.teacherId(), t.teacherName(),
                t.studentName(), t.status(), t.lastMessageAt(), preview, withMessages ? messages : List.of(), unread);
    }

    /** The quiz lets learners see answers after submitting ({@code settings.showAnswersAfterSubmit}) and this student handed in. */
    private boolean answersVisible(String tenantId, ThreadRow t) {
        Boolean allowed = jdbc.query("""
                select coalesce(q.settings ->> 'showAnswersAfterSubmit', q.settings -> 'runtime' ->> 'showAnswersAfterSubmit') = 'true'
                from lms_assignments a join lms_quizzes q on q.id = a.quiz_id and q.tenant_id = a.tenant_id
                where a.tenant_id = ? and a.id = ?
                """, rs -> rs.next() ? rs.getBoolean(1) : false, tenantId, t.assignmentId());
        return Boolean.TRUE.equals(allowed) && submittedVersionId(tenantId, t) != null;
    }

    private String submittedVersionId(String tenantId, ThreadRow t) {
        return namedJdbc.query("""
                select quiz_version_id from lms_attempts
                where tenant_id = :tenantId and assignment_id = :assignmentId and student_id = :studentId
                  and quiz_version_id is not null and lower(status) in (:states)
                order by submitted_at desc nulls last limit 1
                """, new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("assignmentId", t.assignmentId())
                        .addValue("studentId", t.studentId()).addValue("states", LmsStatePolicy.completedAttemptStates()),
                rs -> rs.next() ? rs.getString(1) : null);
    }

    private String correctAnswerText(String tenantId, ThreadRow t) {
        String versionId = submittedVersionId(tenantId, t);
        if (versionId == null) return null;
        return jdbc.query("""
                select body::text body, answer_key::text answer_key from quiz_version_questions
                where tenant_id = ? and quiz_version_id = ? order by order_index, id limit 1 offset ?
                """, rs -> rs.next() ? QuestionAnswerText.of(rs.getString("body"), rs.getString("answer_key")) : null,
                tenantId, versionId, t.questionIndex() - 1);
    }

    private ThreadRow requireVisible(Actor actor, String threadId) {
        requireText(threadId, "threadId", 36);
        List<ThreadRow> rows = jdbc.query(THREAD_SELECT + " where t.tenant_id = ? and t.id = ?", this::mapThread,
                actor.tenantId(), threadId.trim());
        if (rows.isEmpty()) throw new ResourceNotFoundException("Question thread was not found.");
        ThreadRow thread = rows.getFirst();
        boolean allowed = actor.userId().equals(thread.studentUserId()) || actor.userId().equals(thread.teacherId()) || actor.tenantWide();
        if (!allowed) throw new ForbiddenException("The question thread belongs to someone else.");
        return thread;
    }

    private void insertMessage(String tenantId, String threadId, String senderId, String role, String body,
                               List<String> attachmentIds, OffsetDateTime now) {
        jdbc.update("""
                insert into student_question_messages (id, tenant_id, thread_id, sender_id, sender_role, body, attachment_ids, created_at)
                values (?, ?, ?, ?, ?, ?, cast(? as jsonb), ?)
                """, IdGenerator.newId(), tenantId, threadId, senderId, role, body.trim(), jsonList(attachmentIds), now);
    }

    private void notifyOther(Actor actor, String threadId, String type, String recipientUserId, String body) {
        if (recipientUserId == null || recipientUserId.equals(actor.userId())) return;
        String text = body.trim();
        events.publishEvent(new ElearningNotice("question-thread:" + IdGenerator.newId(), actor.tenantId(), type, "question_thread",
                threadId, type.equals("QUESTION_ANSWERED") ? "Giáo viên đã trả lời câu hỏi của bạn" : "Tin nhắn mới trong Hộp thư",
                text.length() > 140 ? text.substring(0, 140) : text, actor.userId(), List.of(recipientUserId)));
    }

    private static final String THREAD_SELECT = """
            select t.id, t.student_id, t.student_user_id, t.teacher_id, t.assignment_id, t.question_index, t.question_prompt,
                   t.student_answer_text, t.status, t.last_message_at, t.student_read_at,
                   coalesce(a.title, '') assignment_title, coalesce(s.name, '') subject_name, coalesce(c.name, '') class_name,
                   coalesce(tu.full_name, '') teacher_name, coalesce(su.full_name, '') student_name
            from student_question_threads t
            left join lms_assignments a on a.id = t.assignment_id and a.tenant_id = t.tenant_id
            left join curriculum_subjects s on s.id = a.subject_id and s.tenant_id = t.tenant_id
            left join academic_classes c on c.id = t.academic_class_id and c.tenant_id = t.tenant_id
            left join users tu on tu.id = t.teacher_id and tu.tenant_id = t.tenant_id
            left join users su on su.id = t.student_user_id and su.tenant_id = t.tenant_id
            """;

    private ThreadRow mapThread(ResultSet rs, int row) throws SQLException {
        return new ThreadRow(rs.getString("id"), rs.getString("student_id"), rs.getString("student_user_id"),
                rs.getString("teacher_id"), rs.getString("assignment_id"), rs.getInt("question_index"),
                rs.getString("question_prompt"), rs.getString("student_answer_text"), rs.getString("status"),
                rs.getObject("last_message_at", OffsetDateTime.class), rs.getObject("student_read_at", OffsetDateTime.class),
                rs.getString("assignment_title"),
                rs.getString("subject_name"), rs.getString("class_name"), rs.getString("teacher_name"), rs.getString("student_name"));
    }

    private void requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) throw new BadRequestException(field + " is required.");
        if (value.trim().length() > maxLength) throw new BadRequestException(field + " exceeds " + maxLength + " characters.");
    }

    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private String jsonList(List<String> values) {
        if (values == null || values.isEmpty()) return "[]";
        List<String> quoted = new ArrayList<>();
        values.forEach(value -> quoted.add("\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""));
        return "[" + String.join(",", quoted) + "]";
    }

    private List<String> parseJsonList(String value) {
        if (value == null || value.length() < 3) return List.of();
        return List.of(value.substring(1, value.length() - 1).split(",")).stream()
                .map(item -> item.trim().replaceAll("^\"|\"$", "")).filter(item -> !item.isEmpty()).toList();
    }

    public record Actor(String tenantId, String userId, boolean tenantWide) { }

    public record AskCommand(String idempotencyKey, String assignmentId, int questionIndex, String questionPrompt,
                             String studentAnswerText, String message, List<String> attachmentIds) { }

    private record ThreadRow(String id, String studentId, String studentUserId, String teacherId, String assignmentId,
                             int questionIndex, String questionPrompt, String studentAnswerText, String status,
                             OffsetDateTime lastMessageAt, OffsetDateTime studentReadAt, String assignmentTitle, String subjectName, String className,
                             String teacherName, String studentName) { }
}
