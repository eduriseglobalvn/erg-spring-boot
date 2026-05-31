package com.erg.ergspringboot.repository.hoclieu;

import com.erg.ergspringboot.model.hoclieu.TaxonomyNode;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxonomyNodeRepository extends JpaRepository<TaxonomyNode, String> {

    List<TaxonomyNode> findByTenantIdAndStatusIgnoreCaseOrderByKindAscSortOrderAscLabelAsc(String tenantId, String status);

    List<TaxonomyNode> findByTenantIdAndKindInAndStatusIgnoreCaseOrderByKindAscSortOrderAscLabelAsc(
            String tenantId,
            Collection<String> kinds,
            String status
    );

    List<TaxonomyNode> findByTenantIdAndKindIgnoreCaseAndStatusIgnoreCaseOrderBySortOrderAscLabelAsc(
            String tenantId,
            String kind,
            String status
    );

    List<TaxonomyNode> findByTenantIdAndParentIdAndStatusIgnoreCaseOrderBySortOrderAscLabelAsc(
            String tenantId,
            String parentId,
            String status
    );

    long countByTenantIdAndParentIdAndStatusIgnoreCase(String tenantId, String parentId, String status);
}
