package com.erg.ergspringboot.lms.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonLogChangeRepository extends JpaRepository<LessonLogChange, String> {

    List<LessonLogChange> findByTenantIdAndEntryIdInOrderByCreatedAtDesc(String tenantId, Collection<String> entryIds);
}
