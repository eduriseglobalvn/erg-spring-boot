package com.erg.ergspringboot.service.elearning.engagement;

import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.AnnouncementReadResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionCommentResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionFeedResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionPostResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionReactionCountResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionReactionResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.lms.Student;
import com.erg.ergspringboot.repository.lms.StudentRepository;
import com.erg.ergspringboot.utils.IdGenerator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit persistent compatibility adapter; callers should use {@code ElearningOperations}. */
@Deprecated(since = "2026-08-08", forRemoval = false)
@Service
public class StudentEngagementService {

    private static final Set<String> REACTIONS = Set.of("like", "love", "care", "haha", "wow", "sad", "angry");
    private static final Set<String> TARGET_TYPES = Set.of("POST", "COMMENT");

    private final StudentRepository students;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public StudentEngagementService(StudentRepository students, JdbcTemplate jdbc) {
        this.students = students;
        this.jdbc = jdbc;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    @Transactional(readOnly = true)
    public StudentDiscussionFeedResponse feed(FeedScope scope) {
        Student student = requireStudent(scope.tenantId(), scope.actorUserId());
        int page = Math.max(0, scope.page());
        int size = Math.max(1, Math.min(50, scope.size()));
        Long total = jdbc.queryForObject(
                "select count(*) from student_discussion_threads where tenant_id = ? and academic_class_id = ?",
                Long.class, scope.tenantId(), student.getAcademicClassId()
        );
        List<PostRow> posts = jdbc.query("""
                select p.id, p.author_id, coalesce(u.full_name, 'Học viên') author_name,
                       p.academic_class_id, coalesce(c.name, '') class_name, p.title, p.content,
                       p.created_at, p.attachment_ids::text, p.assignment_id
                from student_discussion_threads p
                left join users u on u.id = p.author_id and u.tenant_id = p.tenant_id
                left join academic_classes c on c.id = p.academic_class_id and c.tenant_id = p.tenant_id
                where p.tenant_id = ? and p.academic_class_id = ?
                order by p.latest_activity_at desc, p.created_at desc
                limit ? offset ?
                """, this::mapPost, scope.tenantId(), student.getAcademicClassId(), size, page * size);
        return new StudentDiscussionFeedResponse(
                assemblePosts(scope.tenantId(), scope.actorUserId(), posts), page, size,
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
        if (existing != null) return postById(command.tenantId(), command.actorUserId(), existing);
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
            if (raced != null) return postById(command.tenantId(), command.actorUserId(), raced);
            throw race;
        }
        return postById(command.tenantId(), command.actorUserId(), id);
    }

    @Transactional
    public StudentDiscussionCommentResponse reply(ReplyCommand command) {
        Student student = requireStudent(command.tenantId(), command.actorUserId());
        requireText(command.idempotencyKey(), "idempotencyKey", 192);
        requireText(command.postId(), "postId", 36);
        requireText(command.content(), "content", 4000);
        requirePostInClass(command.tenantId(), command.postId(), student.getAcademicClassId());
        String existing = existingId("student_discussion_comments", command.tenantId(), command.actorUserId(), command.idempotencyKey());
        if (existing != null) return commentById(command.tenantId(), command.actorUserId(), existing);
        String parentId = blank(command.parentCommentId());
        if (parentId != null) requireCommentInPost(command.tenantId(), parentId, command.postId());
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
            if (raced != null) return commentById(command.tenantId(), command.actorUserId(), raced);
            throw race;
        }
        jdbc.update("update student_discussion_threads set latest_activity_at = ?, updated_at = ? where tenant_id = ? and id = ?",
                now, now, command.tenantId(), command.postId());
        return commentById(command.tenantId(), command.actorUserId(), id);
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
                reactionCounts(command.tenantId(), List.of(new TargetKey(type, command.targetId()))).getOrDefault(new TargetKey(type, command.targetId()), List.of()));
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

    private StudentDiscussionPostResponse postById(String tenantId, String actorUserId, String id) {
        List<PostRow> rows = jdbc.query("""
                select p.id, p.author_id, coalesce(u.full_name, 'Học viên') author_name,
                       p.academic_class_id, coalesce(c.name, '') class_name, p.title, p.content,
                       p.created_at, p.attachment_ids::text, p.assignment_id
                from student_discussion_threads p
                left join users u on u.id = p.author_id and u.tenant_id = p.tenant_id
                left join academic_classes c on c.id = p.academic_class_id and c.tenant_id = p.tenant_id
                where p.tenant_id = ? and p.id = ?
                """, this::mapPost, tenantId, id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Discussion post was not found.");
        return assemblePosts(tenantId, actorUserId, rows).getFirst();
    }

    private List<StudentDiscussionPostResponse> assemblePosts(String tenantId, String actorUserId, List<PostRow> posts) {
        if (posts.isEmpty()) return List.of();
        List<String> postIds = posts.stream().map(PostRow::id).toList();
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("postIds", postIds);
        List<CommentRow> comments = namedJdbc.query("""
                select c.id, c.post_id, c.parent_id, c.author_id, coalesce(u.full_name, 'Học viên') author_name,
                       c.content, c.created_at, c.attachment_ids::text
                from student_discussion_comments c
                left join users u on u.id = c.author_id and u.tenant_id = c.tenant_id
                where c.tenant_id = :tenantId and c.post_id in (:postIds)
                order by c.created_at asc
                """, params, this::mapComment);
        List<TargetKey> targets = new ArrayList<>();
        postIds.forEach(id -> targets.add(new TargetKey("POST", id)));
        comments.forEach(comment -> targets.add(new TargetKey("COMMENT", comment.id())));
        Map<TargetKey, List<StudentDiscussionReactionCountResponse>> counts = reactionCounts(tenantId, targets);
        Map<TargetKey, String> viewer = viewerReactions(tenantId, actorUserId, targets);
        Map<String, List<CommentRow>> byPost = new HashMap<>();
        comments.forEach(comment -> byPost.computeIfAbsent(comment.postId(), ignored -> new ArrayList<>()).add(comment));
        return posts.stream().map(post -> {
            TargetKey key = new TargetKey("POST", post.id());
            return new StudentDiscussionPostResponse(
                    post.id(), post.authorId(), post.authorName(), initials(post.authorName()), post.academicClassId(),
                    post.className(), post.title(), post.content(), post.createdAt(), parseJsonList(post.attachmentIdsJson()),
                    post.assignmentId(), counts.getOrDefault(key, List.of()), viewer.get(key),
                    assembleComments(byPost.getOrDefault(post.id(), List.of()), counts, viewer)
            );
        }).toList();
    }

    private List<StudentDiscussionCommentResponse> assembleComments(
            List<CommentRow> rows,
            Map<TargetKey, List<StudentDiscussionReactionCountResponse>> counts,
            Map<TargetKey, String> viewer
    ) {
        Map<String, List<CommentRow>> children = new HashMap<>();
        rows.stream().filter(row -> row.parentId() != null)
                .forEach(row -> children.computeIfAbsent(row.parentId(), ignored -> new ArrayList<>()).add(row));
        return rows.stream().filter(row -> row.parentId() == null)
                .map(row -> toComment(row, children, counts, viewer)).toList();
    }

    private StudentDiscussionCommentResponse toComment(
            CommentRow row,
            Map<String, List<CommentRow>> children,
            Map<TargetKey, List<StudentDiscussionReactionCountResponse>> counts,
            Map<TargetKey, String> viewer
    ) {
        TargetKey key = new TargetKey("COMMENT", row.id());
        return new StudentDiscussionCommentResponse(
                row.id(), row.parentId(), row.authorId(), row.authorName(), initials(row.authorName()), row.content(),
                row.createdAt(), parseJsonList(row.attachmentIdsJson()), counts.getOrDefault(key, List.of()), viewer.get(key),
                children.getOrDefault(row.id(), List.of()).stream().map(child -> toComment(child, Map.of(), counts, viewer)).toList()
        );
    }

    private StudentDiscussionCommentResponse commentById(String tenantId, String actorUserId, String id) {
        List<CommentRow> rows = jdbc.query("""
                select c.id, c.post_id, c.parent_id, c.author_id, coalesce(u.full_name, 'Học viên') author_name,
                       c.content, c.created_at, c.attachment_ids::text
                from student_discussion_comments c left join users u on u.id = c.author_id and u.tenant_id = c.tenant_id
                where c.tenant_id = ? and c.id = ?
                """, this::mapComment, tenantId, id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Discussion comment was not found.");
        TargetKey key = new TargetKey("COMMENT", id);
        return toComment(rows.getFirst(), Map.of(), reactionCounts(tenantId, List.of(key)), viewerReactions(tenantId, actorUserId, List.of(key)));
    }

    private Map<TargetKey, List<StudentDiscussionReactionCountResponse>> reactionCounts(String tenantId, List<TargetKey> targets) {
        Map<TargetKey, List<StudentDiscussionReactionCountResponse>> result = new LinkedHashMap<>();
        if (targets.isEmpty()) return result;
        List<String> postIds = targets.stream().filter(key -> key.type().equals("POST")).map(TargetKey::id).toList();
        List<String> commentIds = targets.stream().filter(key -> key.type().equals("COMMENT")).map(TargetKey::id).toList();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId).addValue("postIds", postIds.isEmpty() ? List.of("-") : postIds)
                .addValue("commentIds", commentIds.isEmpty() ? List.of("-") : commentIds);
        List<ReactionCountRow> rows = namedJdbc.query("""
                select target_type, target_id, reaction, count(*) reaction_count
                from student_discussion_reactions
                where tenant_id = :tenantId
                  and ((target_type = 'POST' and target_id in (:postIds))
                    or (target_type = 'COMMENT' and target_id in (:commentIds)))
                group by target_type, target_id, reaction
                """, params, (rs, rowNumber) -> new ReactionCountRow(
                        new TargetKey(rs.getString("target_type"), rs.getString("target_id")),
                        new StudentDiscussionReactionCountResponse(rs.getString("reaction"), rs.getInt("reaction_count"))));
        rows.forEach(row -> {
                    TargetKey key = row.target();
                    result.computeIfAbsent(key, ignored -> new ArrayList<>()).add(
                            row.count()
                    );
                });
        return result;
    }

    private Map<TargetKey, String> viewerReactions(String tenantId, String actorUserId, List<TargetKey> targets) {
        Map<TargetKey, String> result = new HashMap<>();
        if (targets.isEmpty()) return result;
        List<String> ids = targets.stream().map(TargetKey::id).distinct().toList();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId).addValue("userId", actorUserId).addValue("ids", ids);
        List<ViewerReactionRow> rows = namedJdbc.query("""
                select target_type, target_id, reaction from student_discussion_reactions
                where tenant_id = :tenantId and user_id = :userId and target_id in (:ids)
                """, params, (rs, rowNumber) -> new ViewerReactionRow(
                        new TargetKey(rs.getString("target_type"), rs.getString("target_id")), rs.getString("reaction")));
        rows.forEach(row -> result.put(row.target(), row.reaction()));
        return result;
    }

    private String existingId(String table, String tenantId, String actorUserId, String idempotencyKey) {
        String sql = "select id from " + table + " where tenant_id = ? and author_id = ? and idempotency_key = ?";
        return jdbc.query(sql, rs -> rs.next() ? rs.getString(1) : null, tenantId, actorUserId, idempotencyKey.trim());
    }

    private void requirePostInClass(String tenantId, String postId, String classId) {
        Integer count = jdbc.queryForObject("select count(*) from student_discussion_threads where tenant_id = ? and id = ? and academic_class_id = ?",
                Integer.class, tenantId, postId, classId);
        if (count == null || count == 0) throw new ResourceNotFoundException("Discussion post was not found in the student class.");
    }

    private void requireCommentInPost(String tenantId, String commentId, String postId) {
        Integer count = jdbc.queryForObject("select count(*) from student_discussion_comments where tenant_id = ? and id = ? and post_id = ?",
                Integer.class, tenantId, commentId, postId);
        if (count == null || count == 0) throw new ResourceNotFoundException("Parent comment was not found in the discussion post.");
    }

    private void requireTargetInClass(String tenantId, String type, String targetId, String classId) {
        if ("POST".equals(type)) {
            requirePostInClass(tenantId, targetId, classId);
            return;
        }
        Integer count = jdbc.queryForObject("""
                select count(*) from student_discussion_comments c
                join student_discussion_threads p on p.id = c.post_id and p.tenant_id = c.tenant_id
                where c.tenant_id = ? and c.id = ? and p.academic_class_id = ?
                """, Integer.class, tenantId, targetId, classId);
        if (count == null || count == 0) throw new ResourceNotFoundException("Discussion comment was not found in the student class.");
    }

    private PostRow mapPost(ResultSet rs, int row) throws SQLException {
        return new PostRow(rs.getString("id"), rs.getString("author_id"), rs.getString("author_name"),
                rs.getString("academic_class_id"), rs.getString("class_name"), rs.getString("title"),
                rs.getString("content"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getString("attachment_ids"), rs.getString("assignment_id"));
    }

    private CommentRow mapComment(ResultSet rs, int row) throws SQLException {
        return new CommentRow(rs.getString("id"), rs.getString("post_id"), rs.getString("parent_id"),
                rs.getString("author_id"), rs.getString("author_name"), rs.getString("content"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getString("attachment_ids"));
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

    private List<String> parseJsonList(String value) {
        if (value == null || value.length() < 2) return List.of();
        String body = value.substring(1, value.length() - 1).trim();
        if (body.isEmpty()) return List.of();
        return List.of(body.split(",")).stream().map(item -> item.trim().replaceAll("^\"|\"$", "")).toList();
    }

    private String initials(String name) {
        if (name == null || name.isBlank()) return "HV";
        String[] words = name.trim().split("\\s+");
        return (words[0].substring(0, 1) + (words.length > 1 ? words[words.length - 1].substring(0, 1) : "")).toUpperCase();
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

    private record PostRow(String id, String authorId, String authorName, String academicClassId, String className,
                           String title, String content, OffsetDateTime createdAt, String attachmentIdsJson, String assignmentId) { }
    private record CommentRow(String id, String postId, String parentId, String authorId, String authorName,
                              String content, OffsetDateTime createdAt, String attachmentIdsJson) { }
    private record TargetKey(String type, String id) { }
    private record ReactionCountRow(TargetKey target, StudentDiscussionReactionCountResponse count) { }
    private record ViewerReactionRow(TargetKey target, String reaction) { }
}
