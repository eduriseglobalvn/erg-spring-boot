package com.erg.ergspringboot.elearning.internal.engagement;

import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionCommentResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionPostResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionReactionCountResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Read side of student discussions: loads posts and comments of one class and assembles them
 * with reaction counts and the viewer's own reaction. Commands live in {@link StudentEngagementService}.
 */
final class StudentDiscussionReader {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    StudentDiscussionReader(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
    }

    /** Students pass {@code includeHidden = false}: hidden posts, hidden comments and replies under a hidden comment are left out. */
    List<StudentDiscussionPostResponse> classPosts(
            String tenantId, String actorUserId, String classId, int size, int offset, boolean includeHidden
    ) {
        return classPosts(tenantId, actorUserId, classId, size, offset, includeHidden, PostFilter.ALL);
    }

    /** Pinned threads first, then the latest activity. */
    List<StudentDiscussionPostResponse> classPosts(
            String tenantId, String actorUserId, String classId, int size, int offset, boolean includeHidden, PostFilter filter
    ) {
        List<PostRow> posts = jdbc.query(POST_SELECT + """
                where p.tenant_id = ? and p.academic_class_id = ? and (? or p.hidden_at is null)
                """ + filter.sql(includeHidden) + """
                order by (p.pinned_at is not null) desc, p.latest_activity_at desc, p.created_at desc
                limit ? offset ?
                """, this::mapPost, filter.args(actorUserId, tenantId, classId, includeHidden, size, offset));
        return assemblePosts(tenantId, actorUserId, posts, includeHidden);
    }

    long countPosts(String tenantId, String actorUserId, String classId, boolean includeHidden, PostFilter filter) {
        Long total = jdbc.queryForObject("""
                select count(*) from student_discussion_threads p
                where p.tenant_id = ? and p.academic_class_id = ? and (? or p.hidden_at is null)
                """ + filter.sql(includeHidden), Long.class, filter.args(actorUserId, tenantId, classId, includeHidden));
        return total == null ? 0 : total;
    }

    /** {@code kind}: ALL, UNANSWERED (no visible reply) or MINE (written by the viewer); {@code subject} narrows by subject chip. */
    record PostFilter(String kind, String subject) {
        static final PostFilter ALL = new PostFilter("ALL", null);

        static PostFilter of(String kind, String subject) {
            String k = kind == null || kind.isBlank() ? "ALL" : kind.trim().toUpperCase(java.util.Locale.ROOT);
            if (!k.equals("ALL") && !k.equals("UNANSWERED") && !k.equals("MINE")) {
                throw new BadRequestException("filter must be ALL, UNANSWERED or MINE.");
            }
            return new PostFilter(k, subject == null || subject.isBlank() ? null : subject.trim());
        }

        private String sql(boolean includeHidden) {
            String sql = "";
            if (subject != null) sql += " and p.subject = ? ";
            if (kind.equals("MINE")) sql += " and p.author_id = ? ";
            if (kind.equals("UNANSWERED")) {
                sql += " and not exists (select 1 from student_discussion_comments r where r.tenant_id = p.tenant_id and r.post_id = p.id"
                        + (includeHidden ? "" : " and r.hidden_at is null") + ") ";
            }
            return sql;
        }

        /** Bind order follows {@link #sql}: tenant, class, includeHidden, subject, author, then paging. */
        private Object[] args(String actorUserId, String tenantId, String classId, boolean includeHidden, Object... paging) {
            List<Object> args = new ArrayList<>(List.of(tenantId, classId, includeHidden));
            if (subject != null) args.add(subject);
            if (kind.equals("MINE")) args.add(actorUserId);
            args.addAll(List.of(paging));
            return args.toArray();
        }
    }

    private static final String POST_SELECT = """
            select p.id, p.author_id, coalesce(u.full_name, 'Học viên') author_name,
                   p.academic_class_id, coalesce(c.name, '') class_name, p.title, p.content,
                   p.created_at, p.attachment_ids::text, p.assignment_id, p.hidden_at is not null hidden,
                   p.subject, p.pinned_at is not null pinned, p.accepted_comment_id
            from student_discussion_threads p
            left join users u on u.id = p.author_id and u.tenant_id = p.tenant_id
            left join academic_classes c on c.id = p.academic_class_id and c.tenant_id = p.tenant_id
            """;

    List<StudentDiscussionReactionCountResponse> reactionCounts(String tenantId, String targetType, String targetId) {
        TargetKey key = new TargetKey(targetType, targetId);
        return reactionCounts(tenantId, List.of(key)).getOrDefault(key, List.of());
    }

    StudentDiscussionPostResponse postById(String tenantId, String actorUserId, String id) {
        List<PostRow> rows = jdbc.query(POST_SELECT + " where p.tenant_id = ? and p.id = ?", this::mapPost, tenantId, id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Discussion post was not found.");
        return assemblePosts(tenantId, actorUserId, rows, true).getFirst();
    }

    private List<StudentDiscussionPostResponse> assemblePosts(
            String tenantId, String actorUserId, List<PostRow> posts, boolean includeHidden
    ) {
        if (posts.isEmpty()) return List.of();
        List<String> postIds = posts.stream().map(PostRow::id).toList();
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("postIds", postIds)
                .addValue("includeHidden", includeHidden);
        List<CommentRow> comments = namedJdbc.query("""
                select c.id, c.post_id, c.parent_id, c.author_id, coalesce(u.full_name, 'Học viên') author_name,
                       c.content, c.created_at, c.attachment_ids::text, c.hidden_at is not null hidden
                from student_discussion_comments c
                left join users u on u.id = c.author_id and u.tenant_id = c.tenant_id
                left join student_discussion_comments parent on parent.id = c.parent_id and parent.tenant_id = c.tenant_id
                where c.tenant_id = :tenantId and c.post_id in (:postIds)
                  and (:includeHidden or (c.hidden_at is null and parent.hidden_at is null))
                order by c.created_at asc
                """, params, this::mapComment);
        List<TargetKey> targets = new ArrayList<>();
        postIds.forEach(id -> targets.add(new TargetKey("POST", id)));
        comments.forEach(comment -> targets.add(new TargetKey("COMMENT", comment.id())));
        Map<TargetKey, List<StudentDiscussionReactionCountResponse>> counts = reactionCounts(tenantId, targets);
        Map<TargetKey, String> viewer = viewerReactions(tenantId, actorUserId, targets);
        java.util.Set<String> followed = new java.util.HashSet<>(namedJdbc.queryForList("""
                select post_id from student_discussion_follows
                where tenant_id = :tenantId and user_id = :userId and post_id in (:postIds)
                """, new MapSqlParameterSource(params.getValues()).addValue("userId", actorUserId), String.class));
        Map<String, List<CommentRow>> byPost = new HashMap<>();
        comments.forEach(comment -> byPost.computeIfAbsent(comment.postId(), ignored -> new ArrayList<>()).add(comment));
        return posts.stream().map(post -> {
            TargetKey key = new TargetKey("POST", post.id());
            return new StudentDiscussionPostResponse(
                    post.id(), post.authorId(), post.authorName(), initials(post.authorName()), post.academicClassId(),
                    post.className(), post.title(), post.content(), post.createdAt(), parseJsonList(post.attachmentIdsJson()),
                    post.assignmentId(), counts.getOrDefault(key, List.of()), viewer.get(key),
                    assembleComments(byPost.getOrDefault(post.id(), List.of()), counts, viewer), post.hidden(),
                    post.subject(), post.pinned(), post.acceptedCommentId(), followed.contains(post.id())
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
                children.getOrDefault(row.id(), List.of()).stream().map(child -> toComment(child, Map.of(), counts, viewer)).toList(),
                row.hidden()
        );
    }

    StudentDiscussionCommentResponse commentById(String tenantId, String actorUserId, String id) {
        List<CommentRow> rows = jdbc.query("""
                select c.id, c.post_id, c.parent_id, c.author_id, coalesce(u.full_name, 'Học viên') author_name,
                       c.content, c.created_at, c.attachment_ids::text, c.hidden_at is not null hidden
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

    private PostRow mapPost(ResultSet rs, int row) throws SQLException {
        return new PostRow(rs.getString("id"), rs.getString("author_id"), rs.getString("author_name"),
                rs.getString("academic_class_id"), rs.getString("class_name"), rs.getString("title"),
                rs.getString("content"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getString("attachment_ids"), rs.getString("assignment_id"), rs.getBoolean("hidden"),
                rs.getString("subject"), rs.getBoolean("pinned"), rs.getString("accepted_comment_id"));
    }

    private CommentRow mapComment(ResultSet rs, int row) throws SQLException {
        return new CommentRow(rs.getString("id"), rs.getString("post_id"), rs.getString("parent_id"),
                rs.getString("author_id"), rs.getString("author_name"), rs.getString("content"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getString("attachment_ids"), rs.getBoolean("hidden"));
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

    private record PostRow(String id, String authorId, String authorName, String academicClassId, String className,
                           String title, String content, OffsetDateTime createdAt, String attachmentIdsJson, String assignmentId,
                           boolean hidden, String subject, boolean pinned, String acceptedCommentId) { }
    private record CommentRow(String id, String postId, String parentId, String authorId, String authorName,
                              String content, OffsetDateTime createdAt, String attachmentIdsJson, boolean hidden) { }
    private record TargetKey(String type, String id) { }
    private record ReactionCountRow(TargetKey target, StudentDiscussionReactionCountResponse count) { }
    private record ViewerReactionRow(TargetKey target, String reaction) { }
}
