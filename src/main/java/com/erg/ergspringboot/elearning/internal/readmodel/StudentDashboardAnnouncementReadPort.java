package com.erg.ergspringboot.elearning.internal.readmodel;

import com.erg.ergspringboot.lms.persistence.projection.AnnouncementCardProjection;
import java.util.List;

public interface StudentDashboardAnnouncementReadPort {

    List<AnnouncementCardProjection> findStudentDashboardAnnouncements(
            String tenantId,
            String studentId,
            String schoolId,
            String academicClassId,
            int limit
    );

    static StudentDashboardAnnouncementReadPort unavailable() {
        return (tenantId, studentId, schoolId, academicClassId, limit) -> List.of();
    }
}
