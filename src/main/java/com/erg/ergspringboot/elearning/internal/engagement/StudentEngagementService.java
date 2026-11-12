package com.erg.ergspringboot.elearning.internal.engagement;

import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.AnnouncementReadResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionCommentResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionFeedResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionPostResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionReactionResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.lms.persistence.StudentRepository;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Module-internal persistent engagement service; callers outside ELEARNING use {@code ElearningOperations}. */
@Service
public class StudentEngagementService {

    private static final Set<String> REACTIONS = Set.of("like", "love", "care", "haha", "wow", "sad", "angry");
    private static final Set<String> TARGET_TYPES = Set.of("POST", "COMMENT");

    private final StudentRepository students;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final StudentDiscussionReader reader;

    public StudentEngagementService(StudentRepository students, JdbcTemplate jdbc) {
        this.students = students;
        this.jdbc = jdbc;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
        this.reader = new StudentDiscussionReader(jdbc, namedJdbc);
    }

    @Transactional(readOnly = true)
    public StudentDiscussionFeedResponse feed(FeedScope scope) {
        Student student = requireStudent(scope.tenantId(), scope.actorUserId());
        int page = Math.max(0, scope.page());
        int size = Math.max(1, Math.min(50, scope.size()));
        Long total = jdbc.queryForObject(
                "select count(*) from student_discussion_threads where tenant_id = ? and academic_class_id = ? and hidden_at is null",
                Long.class, scope.tenantId(), student.getAcademicClassId()
        );
        return new StudentDiscussionFeedResponse(
                reader.classPosts(scope.tenantId(), scope.actorUserId(), student.getAcademicClassId(), size, page * size, false), page, size,
                total == null ? 0 : total, total != null && (long) (page + 1) * size < total
        );
    }

    @Transactional
    public StudentDiscussionPostResponse createDiscussion(CreateDiscussionCommand command) {
        Student student = requireStudent(command.tenantId(), command.actorUserId());
        requireText(command.idempotencyKey(), "idempotencyKey", 192);
        requireText(command.title(), "title", 250);
        requireText(command.content(), "content", 4000);
        String existing = existingId("student_discussion_threads", command.tenantId(), command.actorUserId(), command.idempotencyKey());
        if (existing != null) return reader.postById(command.tenantId(), command.actorUserId(), existing);
        String id = IdGenerator.newId();
        OffsetDateTime now = OffsetDateTime.now();
        try {
            jdbc.update("""
                    insert into student_discussion_threads
                      (id, tenant_id, academic_class_id, title, content, assignment_id, attachment_ids,
                       author_id, idempotency_key, latest_activity_at, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?)
                    """, id, command.tenantId(), student.getAcademicClassId(), command.title().trim(),
                    command.content().trim(), blank(command.assignmentId()), json(command.attachmentIds()),
                    command.actorUserId(), command.idempotencyKey().trim(), now, now, now);
        } catch (DataIntegrityViolationException race) {
            String raced = existingId("student_discussion_threads", command.tenantId(), command.actorUserId(), command.idempotencyKey());
            if (raced != null) return reader.postById(command.tenantId(), command.actorUserId(), raced);
            throw race;
        }
        return reader.postById(command.tenantId(), command.actorUserId(), id);
    }

    @Transactional
    public StudentDiscussionCommentResponse reply(ReplyCommand command) {
        Student student = requireStudent(command.tenantId(), command.actorUserId());
        requireText(command.idempotencyKey(), "idempotencyKey", 192);
        requireText(command.postId(), "postId", 36);
        requireText(command.content(), "content", 4000);
        requirePostInClass(command.tenantId(), command.postId(), student.getAcademicClassId());
        String existing = existingId("student_discussion_comments", command.tenantId(), command.actorUserId(), command.idempotencyKey());
        if (existing != null) return reader.commentById(command.tenantId(), command.actorUserId(), existing);
        String parentId = blank(command.parentCommentId());
        if (parentId != null) parentId = visibleRootComment(command.tenantId(), parentId, command.postId());
        String id = IdGenerator.newId();
        OffsetDateTime now = OffsetDateTime.now();
        try {
            jdbc.update("""
                    insert into student_discussion_comments
                      (id, tenant_id, post_id, parent_id, author_id, content, attachment_ids,
                       idempotency_key, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?)
                    """, id, command.tenantId(), command.postId(), parentId, command.actorUserId(),
                    command.content().trim(), json(command.attachmentIds()), command.idempotencyKey().trim(), now, now);
        } catch (DataIntegrityViolationException race) {
            String raced = existingId("student_discussion_comments", command.tenantId(), command.actorUserId(), command.idempotencyKey());
            if (raced != null) return reader.commentById(command.tenantId(), command.actorUserId(), raced);
            throw race;
        }
        jdbc.update("update student_discussion_threads set latest_activity_at = ?, updated_at = ? where tenant_id = ? and id = ?",
                now, now, command.tenantId(), command.postId());
        return reader.commentById(command.tenantId(), command.actorUserId(), id);
    }

    @Transactional
    public StudentDiscussionReactionResponse setReaction(SetReactionCommand command) {
        Student student = requireStudent(command.tenantId(), command.actorUserId());
        String type = upper(command.targetType());
        String reaction = lower(command.reaction());
        if (!TARGET_TYPES.contains(type)) throw new BadRequestException("targetType must be POST or COMMENT.");
        if (!REACTIONS.contains(reaction)) throw new BadRequestException("Unsupported discussion reaction.");
        requireTargetInClass(command.tenantId(), type, command.targetId(), student.getAcademicClassId());
        String current = jdbc.query("""
                select reaction from student_discussion_reactions
                where tenant_id = ? and target_type = ? and target_id = ? and user_id = ?
                """, rs -> rs.next() ? rs.getString(1) : null,
                command.tenantId(), type, command.targetId(), command.actorUserId());
        if (reaction.equals(current)) {
            jdbc.update("delete from student_discussion_reactions where tenant_id = ? and target_type = ? and target_id = ? and user_id = ?",
                    command.tenantId(), type, command.targetId(), command.actorUserId());
            current = null;
        } else {
            OffsetDateTime now = OffsetDateTime.now();
            jdbc.update("""
                    insert into student_discussion_reactions
                      (id, tenant_id, target_type, target_id, user_id, reaction, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?)
                    on conflict (tenant_id, target_type, target_id, user_id)
                    do update set reaction = excluded.reaction, updated_at = excluded.updated_at
                    """, IdGenerator.newId(), command.tenantId(), type, command.targetId(), command.actorUserId(), reaction, now, now);
            current = reaction;
        }
        return new StudentDiscussionReactionResponse(type, command.targetId(), current,
                reader.reactionCounts(command.tenantId(), type, command.targetId()));
    }

    @Transactional
    public AnnouncementReadResponse markAnnouncementRead(MarkAnnouncementReadCommand command) {
        Student student = requireStudent(command.tenantId(), command.actorUserId());
        Integer allowed = jdbc.queryForObject("""
                select count(*) from lms_announcements a
                where a.tenant_id = ? and a.id = ? and a.status = 'PUBLISHED'
                  and (a.target_type = 'ALL'
                    or jsonb_exists(a.academic_class_ids, ?)
                    or jsonb_exists(a.student_ids, ?))
                """, Integer.class, command.tenantId(), command.announcementId(),
                student.getAcademicClassId(), student.getId());
        if (allowed == null || allowed == 0) throw new ResourceNotFoundException("Announcement was not found in the student scope.");
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                insert into student_announcement_reads
                  (id, tenant_id, announcement_id, student_id, user_id, read_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (tenant_id, announcement_id, user_id)
                do update set read_at = excluded.read_at, updated_at = excluded.updated_at
                """, IdGenerator.newId(), command.tenantId(), command.announcementId(), student.getId(),
                command.actorUserId(), now, now, now);
        return new AnnouncementReadResponse(command.announcementId(), true, now);
    }

    private Student requireStudent(String tenantId, String actorUserId) {
        return students.findFirstByTenantIdAndAuthUserId(tenantId, actorUserId)
                .filter(student -> student.getAcademicClassId() != null && !student.getAcademicClassId().isBlank())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated student enrollment was not found."));
    }

    private String existingId(String table, String tenantId, String actorUserId, String idempotencyKey) {
        String sql = "select id from " + table + " where tenant_id = ? and author_id = ? and idempotency_key = ?";
        return jdbc.query(sql, rs -> rs.next() ? rs.getString(1) : null, tenantId, actorUserId, idempotencyKey.trim());
    }

    private void requirePostInClass(String tenantId, String postId, String classId) {
        Integer count = jdbc.queryForObject("""
                select count(*) from student_discussion_threads
                where tenant_id = ? and id = ? and academic_class_id = ? and hidden_at is null
                """,
                Integer.class, tenantId, postId, classId);
        if (count == null || count == 0) throw new ResourceNotFoundException("Discussion post was not found in the student class.");
    }

    /** Comments have two levels: a reply to a reply attaches to the root comment of the same post. */
    private String visibleRootComment(String tenantId, String commentId, String postId) {
        String root = jdbc.query("""
                select coalesce(c.parent_id, c.id) from student_discussion_comments c
                left join student_discussion_comments parent on parent.id = c.parent_id and parent.tenant_id = c.tenant_id
                where c.tenant_id = ? and c.id = ? and c.post_id = ? and c.hidden_at is null and parent.hidden_at is null
                """, rs -> rs.next() ? rs.getString(1) : null, tenantId, commentId, postId);
        if (root == null) throw new ResourceNotFoundException("Parent comment was not found in the discussion post.");
        return root;
    }

    private void requireTargetInClass(String tenantId, String type, String targetId, String classId) {
        if ("POST".equals(type)) {
            requirePostInClass(tenantId, targetId, classId);
            return;
        }
        Integer count = jdbc.queryForObject("""
                select count(*) from student_discussion_comments c
                join student_discussion_threads p on p.id = c.post_id and p.tenant_id = c.tenant_id
                left join student_discussion_comments parent on parent.id = c.parent_id and parent.tenant_id = c.tenant_id
                where c.tenant_id = ? and c.id = ? and p.academic_class_id = ?
                  and c.hidden_at is null and parent.hidden_at is null and p.hidden_at is null
                """, Integer.class, tenantId, targetId, classId);
        if (count == null || count == 0) throw new ResourceNotFoundException("Discussion comment was not found in the student class.");
    }

    private void requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) throw new BadRequestException(field + " is required.");
        if (value.trim().length() > maxLength) throw new BadRequestException(field + " exceeds " + maxLength + " characters.");
    }

    private String json(List<String> values) {
        if (values == null || values.isEmpty()) return "[]";
        return values.stream().map(value -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .reduce("[", (left, value) -> left.equals("[") ? left + value : left + "," + value) + "]";
    }

    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String upper(String value) { return value == null ? "" : value.trim().toUpperCase(); }
    private String lower(String value) { return value == null ? "" : value.trim().toLowerCase(); }

    public record FeedScope(String tenantId, String actorUserId, int page, int size) { }
    public record CreateDiscussionCommand(String tenantId, String actorUserId, String idempotencyKey, String title,
                                          String content, String assignmentId, List<String> attachmentIds) { }
    public record ReplyCommand(String tenantId, String actorUserId, String idempotencyKey, String postId,
                               String parentCommentId, String content, List<String> attachmentIds) { }
    public record SetReactionCommand(String tenantId, String actorUserId, String targetType, String targetId, String reaction) { }
    public record MarkAnnouncementReadCommand(String tenantId, String actorUserId, String announcementId) { }

}
