package com.erg.ergspringboot.service.learningcontent;

import static com.erg.ergspringboot.service.learningcontent.LearningContentSupport.blankToNull;
import static com.erg.ergspringboot.service.learningcontent.LearningContentSupport.copyMetadata;
import static com.erg.ergspringboot.service.learningcontent.LearningContentSupport.nonNegative;
import static com.erg.ergspringboot.service.learningcontent.LearningContentSupport.normalizedOrDefault;
import static com.erg.ergspringboot.service.learningcontent.LearningContentSupport.positive;
import static com.erg.ergspringboot.service.learningcontent.LearningContentSupport.required;
import static com.erg.ergspringboot.service.learningcontent.LearningContentSupport.requiredUpper;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.learningcontent.ContentAsset;
import com.erg.ergspringboot.model.learningcontent.ContentItem;
import com.erg.ergspringboot.model.learningcontent.ContentItemAsset;
import com.erg.ergspringboot.platform.sync.LmsSyncOperationService;
import com.erg.ergspringboot.repository.learningcontent.ContentAssetRepository;
import com.erg.ergspringboot.repository.learningcontent.ContentItemAssetRepository;
import com.erg.ergspringboot.repository.learningcontent.ContentItemRepository;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.AttachContentAssetRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.CreateContentAssetRequest;
import com.erg.ergspringboot.service.learningcontent.LearningContentRequests.UpdateContentAssetRequest;
import java.util.Map;

/**
 * Content-asset writes (create, versioned update) and attaching an asset to a content item.
 * A plain collaborator of {@link LearningContentService}: transactions and cache eviction stay on the facade.
 */
final class ContentAssetCommands {

    private static final String DEFAULT_ASSET_STATUS = "active";
    private static final String DEFAULT_ASSET_ROLE = "primary";

    private final ContentItemRepository contentItemRepository;
    private final ContentAssetRepository contentAssetRepository;
    private final ContentItemAssetRepository contentItemAssetRepository;
    private final LearningContentSupport.SyncReceipts receipts;

    ContentAssetCommands(
            ContentItemRepository contentItemRepository,
            ContentAssetRepository contentAssetRepository,
            ContentItemAssetRepository contentItemAssetRepository,
            LearningContentSupport.SyncReceipts receipts
    ) {
        this.contentItemRepository = contentItemRepository;
        this.contentAssetRepository = contentAssetRepository;
        this.contentItemAssetRepository = contentItemAssetRepository;
        this.receipts = receipts;
    }

    ContentAsset create(String tenantId, String userId, CreateContentAssetRequest request) {
        if (request == null) {
            throw new BadRequestException("content asset request is required");
        }
        ContentAsset asset = new ContentAsset();
        asset.setTenantId(required(tenantId, "tenantId"));
        asset.setAssetType(requiredUpper(request.assetType(), "assetType"));
        asset.setTitle(blankToNull(request.title()));
        asset.setStorageProvider(blankToNull(request.storageProvider()));
        asset.setStorageKey(blankToNull(request.storageKey()));
        asset.setStorageUrl(blankToNull(request.storageUrl()));
        asset.setMimeType(blankToNull(request.mimeType()));
        asset.setByteSize(nonNegative(request.byteSize(), "byteSize"));
        asset.setChecksumSha256(blankToNull(request.checksumSha256()));
        asset.setStatus(normalizedOrDefault(request.status(), DEFAULT_ASSET_STATUS));
        asset.setMetadata(copyMetadata(request.metadata()));
        asset.setCreatedBy(blankToNull(userId));
        asset.setUpdatedBy(blankToNull(userId));
        return contentAssetRepository.save(asset);
    }

    ContentAsset update(String tenantId, String contentAssetId, String userId, UpdateContentAssetRequest request) {
        if (request == null) {
            throw new BadRequestException("content asset update request is required");
        }
        ContentAsset asset = contentAssetRepository.findByIdAndTenantId(
                        required(contentAssetId, "contentAssetId"),
                        required(tenantId, "tenantId")
                )
                .orElseThrow(() -> new ResourceNotFoundException("content asset not found for tenant"));
        String resolvedTenantId = required(tenantId, "tenantId");
        String key = blankToNull(request.idempotencyKey());
        LmsSyncOperationService.Receipt receipt = receipts.reserve(resolvedTenantId, userId, request.operationId(), key,
                "lms.content_asset", asset.getId(), request, request.expectedVersion());
        if (receipt != null && receipt.replayed()) return receipts.result(receipt, ContentAsset.class);
        try {
            receipts.checkVersion(request.operationId(), key, "lms.content_asset", asset.getId(),
                    request.expectedVersion(), version(asset));
            if (request.assetType() != null) {
                asset.setAssetType(requiredUpper(request.assetType(), "assetType"));
            }
            if (request.title() != null) {
                asset.setTitle(blankToNull(request.title()));
            }
            if (request.storageProvider() != null) {
                asset.setStorageProvider(blankToNull(request.storageProvider()));
            }
            if (request.storageKey() != null) {
                asset.setStorageKey(blankToNull(request.storageKey()));
            }
            if (request.storageUrl() != null) {
                asset.setStorageUrl(blankToNull(request.storageUrl()));
            }
            if (request.mimeType() != null) {
                asset.setMimeType(blankToNull(request.mimeType()));
            }
            if (request.byteSize() != null) {
                asset.setByteSize(nonNegative(request.byteSize(), "byteSize"));
            }
            if (request.checksumSha256() != null) {
                asset.setChecksumSha256(blankToNull(request.checksumSha256()));
            }
            if (request.status() != null) {
                asset.setStatus(normalizedOrDefault(request.status(), DEFAULT_ASSET_STATUS));
            }
            if (request.metadata() != null) {
                Map<String, Object> mergedMetadata = copyMetadata(asset.getMetadata());
                mergedMetadata.putAll(copyMetadata(request.metadata()));
                asset.setMetadata(mergedMetadata);
            }
            asset.setUpdatedBy(blankToNull(userId));
            ContentAsset saved = contentAssetRepository.save(asset);
            receipts.complete(receipt, saved, version(saved));
            return saved;
        } catch (RuntimeException exception) {
            receipts.abandon(receipt);
            throw exception;
        }
    }

    ContentItemAsset attach(String tenantId, String contentItemId, String userId, AttachContentAssetRequest request) {
        if (request == null) {
            throw new BadRequestException("content asset attachment request is required");
        }
        String resolvedTenantId = required(tenantId, "tenantId");
        String resolvedContentItemId = required(contentItemId, "contentItemId");
        ContentItem item = contentItemRepository.findByIdAndTenantId(resolvedContentItemId, resolvedTenantId)
                .orElseThrow(() -> new ResourceNotFoundException("content item not found for tenant"));
        ContentAsset asset = contentAssetRepository.findByIdAndTenantId(
                        required(request.contentAssetId(), "contentAssetId"),
                        resolvedTenantId
                )
                .orElseThrow(() -> new ResourceNotFoundException("content asset not found for tenant"));

        ContentItemAsset link = new ContentItemAsset();
        link.setTenantId(resolvedTenantId);
        link.setContentItemId(item.getId());
        link.setContentAssetId(asset.getId());
        link.setAssetRole(normalizedOrDefault(request.assetRole(), DEFAULT_ASSET_ROLE));
        link.setSortOrder(request.sortOrder() == null
                ? contentItemAssetRepository.nextSortOrder(resolvedTenantId, resolvedContentItemId)
                : positive(request.sortOrder(), "sortOrder"));
        link.setMetadata(copyMetadata(request.metadata()));
        link.setCreatedBy(blankToNull(userId));
        return contentItemAssetRepository.save(link);
    }

    private long version(ContentAsset asset) {
        return asset.getRecordVersion() == null ? 0L : asset.getRecordVersion();
    }
}
