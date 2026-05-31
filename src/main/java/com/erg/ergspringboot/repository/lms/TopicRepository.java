package com.erg.ergspringboot.repository.lms;

import com.erg.ergspringboot.model.lms.Topic;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicRepository extends JpaRepository<Topic, String> {

    List<Topic> findByTenantIdAndLevelIdAndStatusIgnoreCaseOrderByOrderAscNameAsc(String tenantId, String levelId, String status);
}
