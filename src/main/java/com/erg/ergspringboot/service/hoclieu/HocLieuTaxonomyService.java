package com.erg.ergspringboot.service.hoclieu;

import com.erg.ergspringboot.model.hoclieu.TaxonomyNode;
import com.erg.ergspringboot.repository.hoclieu.TaxonomyNodeRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(value = "postgresTransactionManager", readOnly = true)
public class HocLieuTaxonomyService {

    private final TaxonomyNodeRepository repository;

    public HocLieuTaxonomyService(TaxonomyNodeRepository repository) {
        this.repository = repository;
    }

    public List<TaxonomyNode> findByTenantIdAndStatusIgnoreCaseOrderByKindAscSortOrderAscLabelAsc(String tenantId, String status) {
        return repository.findByTenantIdAndStatusIgnoreCaseOrderByKindAscSortOrderAscLabelAsc(tenantId, status);
    }

    public List<TaxonomyNode> findByTenantIdAndKindIgnoreCaseAndStatusIgnoreCaseOrderBySortOrderAscLabelAsc(String tenantId, String kind, String status) {
        return repository.findByTenantIdAndKindIgnoreCaseAndStatusIgnoreCaseOrderBySortOrderAscLabelAsc(tenantId, kind, status);
    }

    public List<TaxonomyNode> findByTenantIdAndParentIdAndStatusIgnoreCaseOrderBySortOrderAscLabelAsc(String tenantId, String parentId, String status) {
        return repository.findByTenantIdAndParentIdAndStatusIgnoreCaseOrderBySortOrderAscLabelAsc(tenantId, parentId, status);
    }

    public Optional<TaxonomyNode> findById(String nodeId) {
        return repository.findById(nodeId);
    }

    public long countByTenantIdAndParentIdAndStatusIgnoreCase(String tenantId, String parentId, String status) {
        return repository.countByTenantIdAndParentIdAndStatusIgnoreCase(tenantId, parentId, status);
    }

    @Transactional("postgresTransactionManager")
    public TaxonomyNode save(TaxonomyNode node) {
        return repository.save(node);
    }
}
