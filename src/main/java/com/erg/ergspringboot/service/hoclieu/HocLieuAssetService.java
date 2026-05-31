package com.erg.ergspringboot.service.hoclieu;

import com.erg.ergspringboot.model.hoclieu.AssetRecord;
import com.erg.ergspringboot.model.hoclieu.AssetSlideCache;
import com.erg.ergspringboot.repository.hoclieu.AssetRecordRepository;
import com.erg.ergspringboot.repository.hoclieu.AssetSlideCacheRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(value = "postgresTransactionManager", readOnly = true)
public class HocLieuAssetService {

    private final AssetRecordRepository assetRepository;
    private final AssetSlideCacheRepository slideCacheRepository;

    public HocLieuAssetService(AssetRecordRepository assetRepository, AssetSlideCacheRepository slideCacheRepository) {
        this.assetRepository = assetRepository;
        this.slideCacheRepository = slideCacheRepository;
    }

    public List<AssetRecord> findByResourceIdOrderByUpdatedAtDesc(String resourceId) {
        return assetRepository.findByResourceIdOrderByUpdatedAtDesc(resourceId);
    }

    public List<AssetRecord> findByResourceIdInOrderByUpdatedAtDesc(Collection<String> resourceIds) {
        return assetRepository.findByResourceIdInOrderByUpdatedAtDesc(resourceIds);
    }

    public Optional<AssetRecord> findById(String assetId) {
        return assetRepository.findById(assetId);
    }

    public List<AssetSlideCache> findSlides(String tenantId, String assetId, String sourceVersion, String status) {
        return slideCacheRepository.findByTenantIdAndAssetIdAndSourceVersionAndStatusIgnoreCaseOrderBySlideIndexAsc(tenantId, assetId, sourceVersion, status);
    }

    public Optional<AssetSlideCache> findSlide(String tenantId, String assetId, String sourceVersion, Integer slideIndex, String status) {
        return slideCacheRepository.findFirstByTenantIdAndAssetIdAndSourceVersionAndSlideIndexAndStatusIgnoreCase(tenantId, assetId, sourceVersion, slideIndex, status);
    }

    @Transactional("postgresTransactionManager")
    public AssetRecord save(AssetRecord asset) {
        return assetRepository.save(asset);
    }

    @Transactional("postgresTransactionManager")
    public AssetSlideCache saveSlide(AssetSlideCache slide) {
        return slideCacheRepository.save(slide);
    }

    @Transactional("postgresTransactionManager")
    public void delete(AssetRecord asset) {
        assetRepository.delete(asset);
    }

    @Transactional("postgresTransactionManager")
    public void deleteByResourceId(String resourceId) {
        assetRepository.deleteByResourceId(resourceId);
    }

    @Transactional("postgresTransactionManager")
    public void deleteSlidesByAssetId(String assetId) {
        slideCacheRepository.deleteByAssetId(assetId);
    }
}
