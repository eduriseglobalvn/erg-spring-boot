package com.erg.ergspringboot.service.elearning.readmodel;

import com.erg.ergspringboot.repository.lms.projection.AnnouncementCardProjection;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcStudentDashboardAnnouncementReadPort implements StudentDashboardAnnouncementReadPort {

    private final JdbcTemplate jdbc;

    public JdbcStudentDashboardAnnouncementReadPort(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<AnnouncementCardProjection> findStudentDashboardAnnouncements(
            String tenantId,
            String studentId,
            String schoolId,
            String academicClassId,
            int limit
    ) {
        return jdbc.query("""
                select a.id, a.title, a.content, a.pinned, a.published_at, a.updated_at
                from lms_announcements a
                where a.tenant_id = ? and a.status = 'PUBLISHED'
                  and (a.target_type = 'ALL'
                    or jsonb_exists(a.academic_class_ids, ?)
                    or jsonb_exists(a.student_ids, ?))
                order by a.pinned desc, a.published_at desc nulls last, a.created_at desc
                limit ?
                """, (rs, row) -> new AnnouncementCardProjection(
                        rs.getString("id"), rs.getString("title"), rs.getString("content"), rs.getBoolean("pinned"),
                        rs.getObject("published_at", java.time.OffsetDateTime.class),
                        rs.getObject("updated_at", java.time.OffsetDateTime.class)
                ), tenantId, academicClassId, studentId, Math.max(1, Math.min(limit, 50)));
    }
}
