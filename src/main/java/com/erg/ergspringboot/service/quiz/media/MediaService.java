package com.erg.ergspringboot.service.quiz.media;

import com.erg.ergspringboot.common.request.quiz.MediaRequests.MediaCommitRequest;
import com.erg.ergspringboot.common.request.quiz.MediaRequests.MediaPresignRequest;
import com.erg.ergspringboot.common.response.quiz.MediaResponses.MediaCommitResponse;
import com.erg.ergspringboot.common.response.quiz.MediaResponses.MediaPresignResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.quiz.media.MediaAsset;
import com.erg.ergspringboot.repository.quiz.media.MediaAssetRepository;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hash-first, deduplicated media pipeline for the quiz editor (spec §5). Presign resolves an existing
 * asset by (tenant, sha256) or provisions a pending row + short-lived upload URL; commit verifies the
 * uploaded object and flips the asset to committed. Replaces the FE's base64 data URLs.
 */
@Service
@Transactional(value = "postgresTransactionManager")
public class MediaService {

    public static final long MAX_IMAGE_BYTES = 15L * 1024L * 1024L;
    // Slides embed images only — never documents/video (spec §5 security note). SVG is excluded:
    // it is XML that can carry <script>/event handlers, so an inline-served SVG is a stored-XSS
    // vector. Slides only need raster formats.
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp", "image/gif"
    );
    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-f]{64}$");
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_COMMITTED = "committed";

    private final MediaAssetRepository mediaAssetRepository;
    private final MediaStorageAdapter storageAdapter;

    public MediaService(MediaAssetRepository mediaAssetRepository, MediaStorageAdapter storageAdapter) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.storageAdapter = storageAdapter;
    }

    public MediaPresignResponse presign(String tenantId, String userId, MediaPresignRequest request) {
        String sha256 = normalizedSha(request == null ? null : request.sha256());
        String mimeType = validatedImageType(request == null ? null : request.mimeType());
        long byteSize = validatedSize(request == null ? null : request.byteSize());

        return mediaAssetRepository.findByTenantIdAndSha256(tenantId, sha256)
                .filter(asset -> STATUS_COMMITTED.equals(asset.getStatus()))
                .map(asset -> MediaPresignResponse.deduped(asset.getId(), asset.getUrl()))
                .orElseGet(() -> provision(tenantId, userId, sha256, mimeType, byteSize, request.originalName()));
    }

    private MediaPresignResponse provision(
            String tenantId,
            String userId,
            String sha256,
            String mimeType,
            long byteSize,
            String originalName
    ) {
        String storageKey = storageAdapter.storageKey(tenantId, sha256, mimeType);
        MediaAsset asset = mediaAssetRepository.findByTenantIdAndSha256(tenantId, sha256)
                .orElseGet(MediaAsset::new);
        asset.setTenantId(tenantId);
        asset.setSha256(sha256);
        asset.setMimeType(mimeType);
        asset.setByteSize(byteSize);
        asset.setOriginalName(originalName);
        asset.setStorageKey(storageKey);
        asset.setUrl(storageAdapter.publicUrl(storageKey));
        asset.setUploadedBy(userId);
        if (asset.getStatus() == null) {
            asset.setStatus(STATUS_PENDING);
        }
        if (asset.getRefCount() == null) {
            asset.setRefCount(0);
        }
        MediaAsset saved = mediaAssetRepository.save(asset);
        String uploadUrl = storageAdapter.presignUpload(storageKey, mimeType, byteSize);
        return MediaPresignResponse.upload(saved.getId(), uploadUrl, storageKey);
    }

    public boolean storeSignedUpload(String uploadUrl, String tenantId, String mimeType, byte[] bytes) {
        return storageAdapter.storeAuthorizedUpload(uploadUrl, tenantId, mimeType, bytes);
    }

    public MediaCommitResponse commit(String tenantId, MediaCommitRequest request) {
        String mediaId = request == null ? null : request.mediaId();
        if (mediaId == null || mediaId.isBlank()) {
            throw new BadRequestException("mediaId is required");
        }
        MediaAsset asset = mediaAssetRepository.findByIdAndTenantId(mediaId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("media asset not found"));

        MediaStorageAdapter.StoredObject stored = storageAdapter.headObject(asset.getStorageKey())
                .orElseThrow(() -> new BadRequestException("uploaded object is missing or not visible yet"));
        if (stored.byteSize() != asset.getByteSize()
                || !asset.getSha256().equalsIgnoreCase(stored.sha256())
                || !asset.getMimeType().equalsIgnoreCase(stored.mimeType())) {
            throw new BadRequestException("uploaded object does not match the presigned media contract");
        }
        asset.setStatus(STATUS_COMMITTED);
        if (request.width() != null) {
            asset.setWidth(request.width());
        }
        if (request.height() != null) {
            asset.setHeight(request.height());
        }
        MediaAsset saved = mediaAssetRepository.save(asset);
        return new MediaCommitResponse(saved.getId(), saved.getUrl(), saved.getWidth(), saved.getHeight());
    }

    private String normalizedSha(String sha256) {
        if (sha256 == null || sha256.isBlank()) {
            throw new BadRequestException("sha256 is required");
        }
        String normalized = sha256.trim().toLowerCase(java.util.Locale.ROOT);
        if (!SHA256_HEX.matcher(normalized).matches()) {
            throw new BadRequestException("sha256 must be 64 lowercase hex characters");
        }
        return normalized;
    }

    private String validatedImageType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            throw new BadRequestException("mimeType is required");
        }
        String normalized = mimeType.trim().toLowerCase(java.util.Locale.ROOT);
        if (!ALLOWED_IMAGE_TYPES.contains(normalized)) {
            throw new BadRequestException("only image media is allowed for slides");
        }
        return normalized;
    }

    private long validatedSize(Long byteSize) {
        if (byteSize == null || byteSize <= 0) {
            throw new BadRequestException("byteSize must be positive");
        }
        if (byteSize > MAX_IMAGE_BYTES) {
            throw new BadRequestException("media exceeds the maximum allowed size");
        }
        return byteSize;
    }
}
