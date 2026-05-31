package com.erg.ergspringboot.service.hoclieu;

import com.erg.ergspringboot.model.hoclieu.ResourceItemRecord;
import com.erg.ergspringboot.model.hoclieu.ResourceRecord;
import com.erg.ergspringboot.repository.hoclieu.ResourceItemRecordRepository;
import com.erg.ergspringboot.repository.hoclieu.ResourceRecordRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(value = "postgresTransactionManager", readOnly = true)
public class HocLieuResourceService {

    private final ResourceRecordRepository resourceRepository;
    private final ResourceItemRecordRepository itemRepository;

    public HocLieuResourceService(ResourceRecordRepository resourceRepository, ResourceItemRecordRepository itemRepository) {
        this.resourceRepository = resourceRepository;
        this.itemRepository = itemRepository;
    }

    public List<ResourceRecord> findScoped(String tenantId, String subjectId, String categoryId, String sectionId, String lessonId, String status, String visibility) {
        return resourceRepository.findScoped(tenantId, subjectId, categoryId, sectionId, lessonId, status, visibility);
    }

    public Optional<ResourceRecord> findById(String resourceId) {
        return resourceRepository.findById(resourceId);
    }

    public long countByTenantIdAndSubjectId(String tenantId, String subjectId) {
        return resourceRepository.countByTenantIdAndSubjectId(tenantId, subjectId);
    }

    public long countByTenantIdAndCategoryId(String tenantId, String categoryId) {
        return resourceRepository.countByTenantIdAndCategoryId(tenantId, categoryId);
    }

    public long countByTenantIdAndSectionId(String tenantId, String sectionId) {
        return resourceRepository.countByTenantIdAndSectionId(tenantId, sectionId);
    }

    public List<ResourceItemRecord> findItemsByTenantIdAndResourceIdOrderBySortOrderAscUnitTitleAscLessonTitleAsc(String tenantId, String resourceId) {
        return itemRepository.findByTenantIdAndResourceIdOrderBySortOrderAscUnitTitleAscLessonTitleAsc(tenantId, resourceId);
    }

    public List<ResourceItemRecord> findItemsByTenantIdAndResourceIdInOrderBySortOrderAscUnitTitleAscLessonTitleAsc(String tenantId, Collection<String> resourceIds) {
        return itemRepository.findByTenantIdAndResourceIdInOrderBySortOrderAscUnitTitleAscLessonTitleAsc(tenantId, resourceIds);
    }

    @Transactional("postgresTransactionManager")
    public ResourceRecord save(ResourceRecord resource) {
        return resourceRepository.save(resource);
    }

    @Transactional("postgresTransactionManager")
    public void delete(ResourceRecord resource) {
        resourceRepository.delete(resource);
    }

    @Transactional("postgresTransactionManager")
    public void deleteItemsByResourceId(String resourceId) {
        itemRepository.deleteByResourceId(resourceId);
    }
}
