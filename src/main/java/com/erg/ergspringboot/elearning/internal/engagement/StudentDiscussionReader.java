package com.erg.ergspringboot.elearning.internal.engagement;

import com.erg.ergspringboot.common.response.elearning.DiscussionFeedSnapshot;
import com.erg.ergspringboot.common.response.elearning.DiscussionFeedSnapshot.Comment;
import com.erg.ergspringboot.common.response.elearning.DiscussionFeedSnapshot.Post;
import com.erg.ergspringboot.common.response.elearning.DiscussionFeedSnapshot.ReactionCount;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionCommentPageResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionCommentResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionFeedResponse;
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
 * Read side of student discussions. The class-shared part of a feed page (posts, comment window, reaction counts, totals) is
 * loaded as a {@link DiscussionFeedSnapshot} that {@link StudentDiscussionFeedCache} keeps for a few seconds; the viewer's own
 * reactions and follows are always read per call. Commands live in {@link StudentEngagementService}.
 */
final class StudentDiscussionReader {

    /** Top-level comments shown under a post, and replies shown under a comment; the rest is paged by {@code commentPage}. */
    static final int EMBEDDED_LIMIT = 20;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    StudentDiscussionReader(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
    }

    /** Pinned threads first, then the latest activity. Students pass {@code includeHidden = false}. */
    DiscussionFeedSnapshot classSnapshot(
            String tenantId, String actorUserId, String classId, int size, int offset, boolean includeHidden, PostFilter filter
    ) {
        List<Post> posts = jdbc.query(POST_SELECT + """
                where p.tenant_id = ? and p.academic_class_id = ? and (? or p.hidden_at is null)
                """ + filter.sql(includeHidden) + """
                order by (p.pinned_at is not null) desc, p.latest_activity_at desc, p.created_at desc
                limit ? offset ?
                """, this::mapPost, filter.args(actorUserId, tenantId, classId, includeHidden, size, offset));
        return snapshot(tenantId, posts, includeHidden, countPosts(tenantId, actorUserId, classId, includeHidden, filter));
    }

    long countPosts(String tenantId, String actorUserId, String classId, boolean includeHidden, PostFilter filter) {
        Long total = jdbc.queryForObject("""
                select count(*) from student_discussion_threads p
                where p.tenant_id = ? and p.academic_class_id = ? and (? or p.hidden_at is null)
                """ + filter.sql(includeHidden), Long.class, filter.args(actorUserId, tenantId, classId, includeHidden));
        return total == null ? 0 : total;
    }

    /** The shared snapshot plus this viewer's reactions and follows (two statements when the page has posts). */
    StudentDiscussionFeedResponse feedResponse(DiscussionFeedSnapshot snapshot, String tenantId, String actorUserId, int page, int size) {
        return new StudentDiscussionFeedResponse(assemblePosts(snapshot, tenantId, actorUserId), page, size, snapshot.totalItems(),
                (long) (page + 1) * size < snapshot.totalItems());
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

    private static final String COMMENT_SELECT = """
            select c.id, c.post_id, c.parent_id, c.author_id, coalesce(u.full_name, 'Học viên') author_name,
                   c.content, c.created_at, c.attachment_ids::text, c.hidden_at is not null hidden
            from student_discussion_comments c
            left join users u on u.id = c.author_id and u.tenant_id = c.tenant_id
            """;

    List<StudentDiscussionReactionCountResponse> reactionCounts(String tenantId, String targetType, String targetId) {
        TargetKey key = new TargetKey(targetType, targetId);
        return reactionCounts(tenantId, List.of(key)).getOrDefault(key, List.of());
    }

    StudentDiscussionPostResponse postById(String tenantId, String actorUserId, String id) {
        List<Post> rows = jdbc.query(POST_SELECT + " where p.tenant_id = ? and p.id = ?", this::mapPost, tenantId, id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Discussion post was not found.");
        return assemblePosts(snapshot(tenantId, rows, true, 1), tenantId, actorUserId).getFirst();
    }

    private DiscussionFeedSnapshot snapshot(String tenantId, List<Post> posts, boolean includeHidden, long total) {
        if (posts.isEmpty()) return new DiscussionFeedSnapshot(List.of(), List.of(), List.of(), Map.of(), Map.of(), Map.of(), total);
        List<String> postIds = posts.stream().map(Post::id).toList();
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("postIds", postIds)
                .addValue("includeHidden", includeHidden);
        // Bounded: the 20 newest top-level comments of each post and the 20 newest replies of each of those; the rest is
        // reached through the totals (commentCount, replyCount) and the paged discussionComments query.
        List<Comment> comments = namedJdbc.query("""
                with top as (
                    select ranked.id from (
                        select c.id, row_number() over (partition by c.post_id order by c.created_at desc, c.id desc) rn
                        from student_discussion_comments c
                        where c.tenant_id = :tenantId and c.post_id in (:postIds) and c.parent_id is null
                          and (:includeHidden or c.hidden_at is null)
                    ) ranked where ranked.rn <= 20
                ), replies as (
                    select ranked.id from (
                        select c.id, row_number() over (partition by c.parent_id order by c.created_at desc, c.id desc) rn
                        from student_discussion_comments c
                        where c.tenant_id = :tenantId and c.parent_id in (select id from top)
                          and (:includeHidden or c.hidden_at is null)
                    ) ranked where ranked.rn <= 20
                )
                """ + COMMENT_SELECT + """
                where c.tenant_id = :tenantId and c.id in (select id from top union all select id from replies)
                order by c.created_at asc
                """, params, this::mapComment);
        // Same visibility as the lists above: a hidden comment, and the replies under it, only count for moderators.
        Map<String, Integer> commentTotals = new HashMap<>();
        Map<String, Integer> topLevelTotals = new HashMap<>();
        Map<String, Integer> replyTotals = new HashMap<>();
        namedJdbc.query("""
                select c.post_id, c.parent_id, count(*) total from student_discussion_comments c
                left join student_discussion_comments parent on parent.id = c.parent_id and parent.tenant_id = c.tenant_id
                where c.tenant_id = :tenantId and c.post_id in (:postIds)
                  and (:includeHidden or (c.hidden_at is null and (parent.id is null or parent.hidden_at is null)))
                group by c.post_id, c.parent_id
                """, params, rs -> {
            int count = rs.getInt("total");
            commentTotals.merge(rs.getString("post_id"), count, Integer::sum);
            String parentId = rs.getString("parent_id");
            if (parentId == null) topLevelTotals.merge(rs.getString("post_id"), count, Integer::sum);
            else replyTotals.merge(parentId, count, Integer::sum);
        });
        List<TargetKey> targets = new ArrayList<>();
        postIds.forEach(id -> targets.add(new TargetKey("POST", id)));
        comments.forEach(comment -> targets.add(new TargetKey("COMMENT", comment.id())));
        return new DiscussionFeedSnapshot(posts, comments, reactionRows(tenantId, targets), commentTotals, topLevelTotals,
                replyTotals, total);
    }

    private List<StudentDiscussionPostResponse> assemblePosts(DiscussionFeedSnapshot snapshot, String tenantId, String actorUserId) {
        if (snapshot.posts().isEmpty()) return List.of();
        List<String> postIds = snapshot.posts().stream().map(Post::id).toList();
        List<TargetKey> targets = new ArrayList<>();
        postIds.forEach(id -> targets.add(new TargetKey("POST", id)));
        snapshot.comments().forEach(comment -> targets.add(new TargetKey("COMMENT", comment.id())));
        Map<TargetKey, List<StudentDiscussionReactionCountResponse>> counts = groupCounts(snapshot.reactions());
        Map<TargetKey, String> viewer = viewerReactions(tenantId, actorUserId, targets);
        java.util.Set<String> followed = new java.util.HashSet<>(namedJdbc.queryForList("""
                select post_id from student_discussion_follows
                where tenant_id = :tenantId and user_id = :userId and post_id in (:postIds)
                """, new MapSqlParameterSource("tenantId", tenantId).addValue("userId", actorUserId).addValue("postIds", postIds),
                String.class));
        Map<String, List<Comment>> byPost = new HashMap<>();
        snapshot.comments().forEach(comment -> byPost.computeIfAbsent(comment.postId(), ignored -> new ArrayList<>()).add(comment));
        return snapshot.posts().stream().map(post -> {
            TargetKey key = new TargetKey("POST", post.id());
            List<Comment> own = byPost.getOrDefault(post.id(), List.of());
            long shownTopLevel = own.stream().filter(comment -> comment.parentId() == null).count();
            return new StudentDiscussionPostResponse(
                    post.id(), post.authorId(), post.authorName(), initials(post.authorName()), post.academicClassId(),
                    post.className(), post.title(), post.content(), post.createdAt(), parseJsonList(post.attachmentIdsJson()),
                    post.assignmentId(), counts.getOrDefault(key, List.of()), viewer.get(key),
                    assembleComments(own, counts, viewer, snapshot.replyTotals()), post.hidden(),
                    post.subject(), post.pinned(), post.acceptedCommentId(), followed.contains(post.id()),
                    snapshot.commentTotals().getOrDefault(post.id(), 0),
                    snapshot.topLevelTotals().getOrDefault(post.id(), 0) > shownTopLevel
            );
        }).toList();
    }

    private List<StudentDiscussionCommentResponse> assembleComments(
            List<Comment> rows,
            Map<TargetKey, List<StudentDiscussionReactionCountResponse>> counts,
            Map<TargetKey, String> viewer,
            Map<String, Integer> replyTotals
    ) {
        Map<String, List<Comment>> children = new HashMap<>();
        rows.stream().filter(row -> row.parentId() != null)
                .forEach(row -> children.computeIfAbsent(row.parentId(), ignored -> new ArrayList<>()).add(row));
        return rows.stream().filter(row -> row.parentId() == null)
                .map(row -> toComment(row, children.getOrDefault(row.id(), List.of()), replyTotals.getOrDefault(row.id(), 0), counts, viewer))
                .toList();
    }

    private StudentDiscussionCommentResponse toComment(
            Comment row,
            List<Comment> replies,
            int replyTotal,
            Map<TargetKey, List<StudentDiscussionReactionCountResponse>> counts,
            Map<TargetKey, String> viewer
    ) {
        TargetKey key = new TargetKey("COMMENT", row.id());
        return new StudentDiscussionCommentResponse(
                row.id(), row.parentId(), row.authorId(), row.authorName(), initials(row.authorName()), row.content(),
                row.createdAt(), parseJsonList(row.attachmentIdsJson()), counts.getOrDefault(key, List.of()), viewer.get(key),
                replies.stream().map(child -> toComment(child, List.of(), 0, counts, viewer)).toList(),
                row.hidden(), replyTotal, replyTotal > replies.size()
        );
    }

    StudentDiscussionCommentResponse commentById(String tenantId, String actorUserId, String id) {
        List<Comment> rows = jdbc.query(COMMENT_SELECT + " where c.tenant_id = ? and c.id = ?", this::mapComment, tenantId, id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Discussion comment was not found.");
        TargetKey key = new TargetKey("COMMENT", id);
        return toComment(rows.getFirst(), List.of(), 0, reactionCounts(tenantId, List.of(key)), viewerReactions(tenantId, actorUserId, List.of(key)));
    }

    /** A visible (or, for moderators, any) top-level comment of the post; the parent of a reply page must be one. */
    void requireRootComment(String tenantId, String postId, String commentId, boolean includeHidden) {
        Integer count = jdbc.queryForObject("""
                select count(*) from student_discussion_comments c
                where c.tenant_id = ? and c.id = ? and c.post_id = ? and c.parent_id is null and (? or c.hidden_at is null)
                """, Integer.class, tenantId, commentId, postId, includeHidden);
        if (count == null || count == 0) throw new ResourceNotFoundException("Parent comment was not found in the discussion post.");
    }

    /**
     * One page of the post's top-level comments ({@code parentId == null}, each with its first 20 replies) or of the replies of
     * one top-level comment. Pages run newest first, so page 0 is the window the feed embeds; items of a page run oldest to
     * newest like the feed. Visibility and totals follow the feed (hidden comments and their replies only for moderators).
     */
    StudentDiscussionCommentPageResponse commentPage(
            String tenantId, String actorUserId, String postId, String parentId, int page, int size, boolean includeHidden
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("postId", postId)
                .addValue("parentId", parentId).addValue("includeHidden", includeHidden).addValue("limit", size)
                .addValue("offset", (long) page * size);
        String scope = " from student_discussion_comments c where c.tenant_id = :tenantId and c.post_id = :postId and "
                + (parentId == null ? "c.parent_id is null" : "c.parent_id = :parentId") + " and (:includeHidden or c.hidden_at is null)";
        Long total = namedJdbc.queryForObject("select count(*)" + scope, params, Long.class);
        long totalItems = total == null ? 0 : total;
        List<String> ids = namedJdbc.queryForList("select c.id" + scope + " order by c.created_at desc, c.id desc limit :limit offset :offset",
                params, String.class);
        if (ids.isEmpty()) return new StudentDiscussionCommentPageResponse(List.of(), page, size, totalItems, false);
        MapSqlParameterSource byIds = new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("ids", ids)
                .addValue("includeHidden", includeHidden);
        String replies = parentId != null ? "" : """
                with replies as (
                    select ranked.id from (
                        select c.id, row_number() over (partition by c.parent_id order by c.created_at desc, c.id desc) rn
                        from student_discussion_comments c
                        where c.tenant_id = :tenantId and c.parent_id in (:ids) and (:includeHidden or c.hidden_at is null)
                    ) ranked where ranked.rn <= 20
                )
                """;
        List<Comment> rows = namedJdbc.query(replies + COMMENT_SELECT + " where c.tenant_id = :tenantId and (c.id in (:ids)"
                + (parentId != null ? ")" : " or c.id in (select id from replies))") + " order by c.created_at asc", byIds, this::mapComment);
        Map<String, Integer> replyTotals = new HashMap<>();
        if (parentId == null) {
            namedJdbc.query("""
                    select c.parent_id, count(*) total from student_discussion_comments c
                    where c.tenant_id = :tenantId and c.parent_id in (:ids) and (:includeHidden or c.hidden_at is null)
                    group by c.parent_id
                    """, byIds, rs -> { replyTotals.put(rs.getString("parent_id"), rs.getInt("total")); });
        }
        List<TargetKey> targets = rows.stream().map(row -> new TargetKey("COMMENT", row.id())).toList();
        Map<TargetKey, List<StudentDiscussionReactionCountResponse>> counts = reactionCounts(tenantId, targets);
        Map<TargetKey, String> viewer = viewerReactions(tenantId, actorUserId, targets);
        List<StudentDiscussionCommentResponse> items = parentId != null
                ? rows.stream().map(row -> toComment(row, List.of(), 0, counts, viewer)).toList()
                : assembleComments(rows, counts, viewer, replyTotals);
        return new StudentDiscussionCommentPageResponse(items, page, size, totalItems, (long) (page + 1) * size < totalItems);
    }

    private Map<TargetKey, List<StudentDiscussionReactionCountResponse>> reactionCounts(String tenantId, List<TargetKey> targets) {
        return groupCounts(reactionRows(tenantId, targets));
    }

    private static Map<TargetKey, List<StudentDiscussionReactionCountResponse>> groupCounts(List<ReactionCount> rows) {
        Map<TargetKey, List<StudentDiscussionReactionCountResponse>> result = new LinkedHashMap<>();
        rows.forEach(row -> result.computeIfAbsent(new TargetKey(row.targetType(), row.targetId()), ignored -> new ArrayList<>())
                .add(new StudentDiscussionReactionCountResponse(row.reaction(), row.count())));
        return result;
    }

    private List<ReactionCount> reactionRows(String tenantId, List<TargetKey> targets) {
        if (targets.isEmpty()) return List.of();
        List<String> postIds = targets.stream().filter(key -> key.type().equals("POST")).map(TargetKey::id).toList();
        List<String> commentIds = targets.stream().filter(key -> key.type().equals("COMMENT")).map(TargetKey::id).toList();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId).addValue("postIds", postIds.isEmpty() ? List.of("-") : postIds)
                .addValue("commentIds", commentIds.isEmpty() ? List.of("-") : commentIds);
        return namedJdbc.query("""
                select target_type, target_id, reaction, count(*) reaction_count
                from student_discussion_reactions
                where tenant_id = :tenantId
                  and ((target_type = 'POST' and target_id in (:postIds))
                    or (target_type = 'COMMENT' and target_id in (:commentIds)))
                group by target_type, target_id, reaction
                """, params, (rs, rowNumber) -> new ReactionCount(rs.getString("target_type"), rs.getString("target_id"),
                        rs.getString("reaction"), rs.getInt("reaction_count")));
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

    private Post mapPost(ResultSet rs, int row) throws SQLException {
        return new Post(rs.getString("id"), rs.getString("author_id"), rs.getString("author_name"),
                rs.getString("academic_class_id"), rs.getString("class_name"), rs.getString("title"),
                rs.getString("content"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getString("attachment_ids"), rs.getString("assignment_id"), rs.getBoolean("hidden"),
                rs.getString("subject"), rs.getBoolean("pinned"), rs.getString("accepted_comment_id"));
    }

    private Comment mapComment(ResultSet rs, int row) throws SQLException {
        return new Comment(rs.getString("id"), rs.getString("post_id"), rs.getString("parent_id"),
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

    private record TargetKey(String type, String id) { }
    private record ViewerReactionRow(TargetKey target, String reaction) { }
}
