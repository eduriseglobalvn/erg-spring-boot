package com.erg.ergspringboot.service.elearning.readmodel;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class NoPersistentStudentDashboardAnnouncementReadPort {

    @Bean
    @ConditionalOnMissingBean(StudentDashboardAnnouncementReadPort.class)
    StudentDashboardAnnouncementReadPort studentDashboardAnnouncementReadPort() {
        // TODO: Wire this port to a projection repository after Announcement has a JPA entity/table.
        return (tenantId, studentId, schoolId, academicClassId, limit) -> List.of();
    }
}
