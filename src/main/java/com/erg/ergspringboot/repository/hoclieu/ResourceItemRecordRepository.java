package com.erg.ergspringboot.repository.hoclieu;

import com.erg.ergspringboot.model.hoclieu.ResourceItemRecord;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceItemRecordRepository extends JpaRepository<ResourceItemRecord, String> {

    List<ResourceItemRecord> findByTenantIdAndResourceIdInOrderBySortOrderAscUnitTitleAscLessonTitleAsc(
            String tenantId,
            Collection<String> resourceIds
    );

    List<ResourceItemRecord> findByTenantIdAndResourceIdOrderBySortOrderAscUnitTitleAscLessonTitleAsc(
            String tenantId,
            String resourceId
    );

    void deleteByResourceId(String resourceId);
}
