package com.erg.ergspringboot.repository.hoclieu;

import com.erg.ergspringboot.model.hoclieu.AssetSlideCache;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetSlideCacheRepository extends JpaRepository<AssetSlideCache, String> {

    List<AssetSlideCache> findByTenantIdAndAssetIdAndSourceVersionAndStatusIgnoreCaseOrderBySlideIndexAsc(
            String tenantId,
            String assetId,
            String sourceVersion,
            String status
    );

    Optional<AssetSlideCache> findFirstByTenantIdAndAssetIdAndSourceVersionAndSlideIndexAndStatusIgnoreCase(
            String tenantId,
            String assetId,
            String sourceVersion,
            Integer slideIndex,
            String status
    );

    void deleteByAssetId(String assetId);
}
