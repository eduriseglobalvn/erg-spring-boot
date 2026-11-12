package com.erg.ergspringboot.elearning.internal.engagement;

import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionFeedResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionModerationClassResponse;
import com.erg.ergspringboot.common.response.elearning.StudentEngagementResponses.StudentDiscussionModerationResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teacher/admin moderation of student discussions. A tenant-wide admin opens every class of the tenant;
 * anyone else only the classes they teach (homeroom or assigned homework, as the LMS teacher class list).
 */
@Service
public class StudentDiscussionModerationService {

    private final ClassroomRepository classrooms;
    private final JdbcTemplate jdbc;
    private final StudentDiscussionReader reader;

    public StudentDiscussionModerationService(ClassroomRepository classrooms, JdbcTemplate jdbc) {
        this.classrooms = classrooms;
        this.jdbc = jdbc;
        this.reader = new StudentDiscussionReader(jdbc, new NamedParameterJdbcTemplate(jdbc));
    }

    @Transactional(readOnly = true)
    public List<StudentDiscussionModerationClassResponse> classes(Moderator moderator) {
        if (moderator.tenantWide()) {
            return jdbc.query("select id, name from academic_classes where tenant_id = ? order by name, id",
                    (rs, row) -> new StudentDiscussionModerationClassResponse(rs.getString("id"), rs.getString("name")),
                    moderator.tenantId());
        }
        return classrooms.findTeacherClassOptions(moderator.tenantId(), moderator.userId()).stream()
                .map(option -> new StudentDiscussionModerationClassResponse(option.id(), option.name())).toList();
    }

    @Transactional(readOnly = true)
    public StudentDiscussionFeedResponse feed(Moderator moderator, String classId, int page, int size) {
        String safeClassId = requireClass(moderator, classId);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(50, size));
        Long total = jdbc.queryForObject(
                "select count(*) from student_discussion_threads where tenant_id = ? and academic_class_id = ?",
                Long.class, moderator.tenantId(), safeClassId);
        long count = total == null ? 0 : total;
        return new StudentDiscussionFeedResponse(
                reader.classPosts(moderator.tenantId(), moderator.userId(), safeClassId, safeSize, safePage * safeSize, true),
                safePage, safeSize, count, (long) (safePage + 1) * safeSize < count);
    }

    @Transactional
    public StudentDiscussionModerationResponse setHidden(Moderator moderator, String targetType, String targetId, boolean hidden) {
        Target target = requireTarget(moderator, targetType, targetId);
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("update " + target.table() + " set hidden_at = ?, hidden_by = ?, updated_at = ? where tenant_id = ? and id = ?",
                hidden ? now : null, hidden ? moderator.userId() : null, now, moderator.tenantId(), target.id());
        return new StudentDiscussionModerationResponse(target.type(), target.id(), hidden, false);
    }

    @Transactional
    public StudentDiscussionModerationResponse delete(Moderator moderator, String targetType, String targetId) {
        Target target = requireTarget(moderator, targetType, targetId);
        // Reactions have no foreign key: drop those of the target and of every comment the cascade removes.
        if ("POST".equals(target.type())) {
            jdbc.update("""
                    delete from student_discussion_reactions
                    where tenant_id = ? and ((target_type = 'POST' and target_id = ?)
                      or (target_type = 'COMMENT' and target_id in (
                          select id from student_discussion_comments where tenant_id = ? and post_id = ?)))
                    """, moderator.tenantId(), target.id(), moderator.tenantId(), target.id());
        } else {
            jdbc.update("""
                    delete from student_discussion_reactions
                    where tenant_id = ? and target_type = 'COMMENT' and target_id in (
                        select id from student_discussion_comments where tenant_id = ? and (id = ? or parent_id = ?))
                    """, moderator.tenantId(), moderator.tenantId(), target.id(), target.id());
        }
        jdbc.update("delete from " + target.table() + " where tenant_id = ? and id = ?", moderator.tenantId(), target.id());
        return new StudentDiscussionModerationResponse(target.type(), target.id(), false, true);
    }

    private Target requireTarget(Moderator moderator, String targetType, String targetId) {
        String type = targetType == null ? "" : targetType.trim().toUpperCase();
        String id = targetId == null ? "" : targetId.trim();
        if (!type.equals("POST") && !type.equals("COMMENT")) throw new BadRequestException("targetType must be POST or COMMENT.");
        if (id.isEmpty()) throw new BadRequestException("targetId is required.");
        String classId = jdbc.query(type.equals("POST")
                        ? "select academic_class_id from student_discussion_threads where tenant_id = ? and id = ?"
                        : """
                          select p.academic_class_id from student_discussion_comments c
                          join student_discussion_threads p on p.id = c.post_id and p.tenant_id = c.tenant_id
                          where c.tenant_id = ? and c.id = ?
                          """,
                rs -> rs.next() ? rs.getString(1) : null, moderator.tenantId(), id);
        if (classId == null) throw new ResourceNotFoundException("Discussion item was not found.");
        requireClass(moderator, classId);
        return new Target(type, id, type.equals("POST") ? "student_discussion_threads" : "student_discussion_comments");
    }

    private String requireClass(Moderator moderator, String classId) {
        if (classId == null || classId.isBlank()) throw new BadRequestException("classId is required.");
        String id = classId.trim();
        boolean allowed;
        if (moderator.tenantWide()) {
            Integer count = jdbc.queryForObject("select count(*) from academic_classes where tenant_id = ? and id = ?",
                    Integer.class, moderator.tenantId(), id);
            allowed = count != null && count > 0;
        } else {
            allowed = classrooms.findTeacherClassOptions(moderator.tenantId(), moderator.userId()).stream()
                    .anyMatch(option -> id.equals(option.id()));
        }
        if (!allowed) throw new ForbiddenException("The class is outside the discussion moderator scope.");
        return id;
    }

    public record Moderator(String tenantId, String userId, boolean tenantWide) { }

    private record Target(String type, String id, String table) { }
}
