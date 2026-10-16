package com.erg.ergspringboot.service.quiz.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.erg.ergspringboot.common.request.quiz.MediaRequests.MediaPresignRequest;
import com.erg.ergspringboot.common.request.quiz.MediaRequests.MediaCommitRequest;
import com.erg.ergspringboot.common.response.quiz.MediaResponses.MediaPresignResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.model.quiz.media.MediaAsset;
import com.erg.ergspringboot.repository.quiz.media.MediaAssetRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the media presign validation + dedup branch, backed by an in-memory fake repository so
 * they run without Docker. Covers the security-relevant rules (image allowlist, size cap, sha format)
 * and the dedup short-circuit for already-committed bytes.
 */
class MediaServiceValidationTest {

    private FakeMediaAssetRepository repository;
    private VerifyingMediaStorageAdapter storage;
    private MediaService service;

    private static final String VALID_SHA = "a".repeat(64);

    @BeforeEach
    void setUp() {
        repository = new FakeMediaAssetRepository();
        storage = new VerifyingMediaStorageAdapter();
        service = new MediaService(repository, storage);
    }

    @Test
    void provisionsPendingAssetForFreshBytes() {
        MediaPresignResponse response = service.presign("erg", "user-1",
                new MediaPresignRequest(VALID_SHA, "image/png", 2048L, "diagram.png"));

        assertThat(response.deduped()).isFalse();
        assertThat(response.mediaId()).isNotBlank();
        assertThat(response.uploadUrl()).contains("/upload/");
        assertThat(response.storageKey()).contains("quiz-media/erg/");
        assertThat(repository.saved).hasSize(1);
        assertThat(repository.saved.getFirst().getStatus()).isEqualTo("pending");
    }

    @Test
    void dedupesWhenBytesAlreadyCommitted() {
        MediaAsset existing = new MediaAsset();
        existing.setId("media-existing");
        existing.setTenantId("erg");
        existing.setSha256(VALID_SHA);
        existing.setStatus("committed");
        existing.setUrl("/blob/quiz-media/erg/aa/" + VALID_SHA + ".png");
        existing.setRefCount(1);
        repository.store.put("media-existing", existing);

        MediaPresignResponse response = service.presign("erg", "user-1",
                new MediaPresignRequest(VALID_SHA, "image/png", 2048L, "diagram.png"));

        assertThat(response.deduped()).isTrue();
        assertThat(response.mediaId()).isEqualTo("media-existing");
        assertThat(response.uploadUrl()).isNull();
    }

    @Test
    void rejectsNonImageMime() {
        assertThatThrownBy(() -> service.presign("erg", "user-1",
                new MediaPresignRequest(VALID_SHA, "application/pdf", 2048L, "doc.pdf")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("image");
    }

    @Test
    void rejectsOversizeMedia() {
        assertThatThrownBy(() -> service.presign("erg", "user-1",
                new MediaPresignRequest(VALID_SHA, "image/png", MediaService.MAX_IMAGE_BYTES + 1, "big.png")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("size");
    }

    @Test
    void rejectsMalformedSha() {
        assertThatThrownBy(() -> service.presign("erg", "user-1",
                new MediaPresignRequest("not-a-hash", "image/png", 2048L, "x.png")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("sha256");
    }

    @Test
    void commitRejectsWhenObjectIsMissing() {
        MediaPresignResponse presigned = service.presign("erg", "user-1",
                new MediaPresignRequest(VALID_SHA, "image/png", 2048L, "diagram.png"));

        assertThatThrownBy(() -> service.commit("erg", new MediaCommitRequest(presigned.mediaId(), 100, 80)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("uploaded object");
    }

    @Test
    void commitRejectsSizeOrChecksumMismatch() {
        MediaPresignResponse presigned = service.presign("erg", "user-1",
                new MediaPresignRequest(VALID_SHA, "image/png", 2048L, "diagram.png"));
        storage.object = new MediaStorageAdapter.StoredObject(2049L, "b".repeat(64), "image/png");

        assertThatThrownBy(() -> service.commit("erg", new MediaCommitRequest(presigned.mediaId(), 100, 80)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void commitOnlyAfterStorageHeadMatchesContract() {
        MediaPresignResponse presigned = service.presign("erg", "user-1",
                new MediaPresignRequest(VALID_SHA, "image/png", 2048L, "diagram.png"));
        storage.object = new MediaStorageAdapter.StoredObject(2048L, VALID_SHA, "image/png");

        var committed = service.commit("erg", new MediaCommitRequest(presigned.mediaId(), 100, 80));

        assertThat(committed.mediaId()).isEqualTo(presigned.mediaId());
        assertThat(repository.store.get(presigned.mediaId()).getStatus()).isEqualTo("committed");
    }

    private static final class VerifyingMediaStorageAdapter implements MediaStorageAdapter {
        private StoredObject object;

        @Override public String storageKey(String tenantId, String sha256, String mimeType) {
            return "quiz-media/" + tenantId + "/" + sha256 + ".png";
        }
        @Override public String presignUpload(String storageKey, String mimeType, long byteSize) { return "/upload/" + storageKey; }
        @Override public boolean isAuthorizedUpload(String uploadUrl, String tenantId, String mimeType, long byteSize) { return true; }
        @Override public String publicUrl(String storageKey) { return "/blob/" + storageKey; }
        @Override public Optional<StoredObject> headObject(String storageKey) { return Optional.ofNullable(object); }
    }

    private static final class FakeMediaAssetRepository implements MediaAssetRepository {
        private final java.util.Map<String, MediaAsset> store = new java.util.LinkedHashMap<>();
        private final java.util.List<MediaAsset> saved = new java.util.ArrayList<>();

        @Override
        public Optional<MediaAsset> findByTenantIdAndSha256(String tenantId, String sha256) {
            return store.values().stream()
                    .filter(asset -> tenantId.equals(asset.getTenantId()) && sha256.equals(asset.getSha256()))
                    .findFirst();
        }

        @Override
        public Optional<MediaAsset> findByIdAndTenantId(String id, String tenantId) {
            return Optional.ofNullable(store.get(id))
                    .filter(asset -> tenantId.equals(asset.getTenantId()));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <S extends MediaAsset> S save(S entity) {
            if (entity.getId() == null || entity.getId().isBlank()) {
                entity.setId("media-" + (store.size() + 1));
            }
            store.put(entity.getId(), entity);
            saved.add(entity);
            return entity;
        }

        // --- unused JpaRepository surface -------------------------------------------------------
        @Override public java.util.List<MediaAsset> findAll() { return java.util.List.copyOf(store.values()); }
        @Override public java.util.List<MediaAsset> findAll(org.springframework.data.domain.Sort sort) { return findAll(); }
        @Override public org.springframework.data.domain.Page<MediaAsset> findAll(org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public java.util.List<MediaAsset> findAllById(Iterable<String> ids) { return java.util.List.of(); }
        @Override public <S extends MediaAsset> java.util.List<S> saveAll(Iterable<S> entities) { java.util.List<S> out = new java.util.ArrayList<>(); entities.forEach(e -> out.add(save(e))); return out; }
        @Override public Optional<MediaAsset> findById(String id) { return Optional.ofNullable(store.get(id)); }
        @Override public boolean existsById(String id) { return store.containsKey(id); }
        @Override public long count() { return store.size(); }
        @Override public void deleteById(String id) { store.remove(id); }
        @Override public void delete(MediaAsset entity) { store.remove(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends String> ids) { ids.forEach(store::remove); }
        @Override public void deleteAll(Iterable<? extends MediaAsset> entities) { entities.forEach(this::delete); }
        @Override public void deleteAll() { store.clear(); }
        @Override public void flush() { }
        @Override public <S extends MediaAsset> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends MediaAsset> java.util.List<S> saveAllAndFlush(Iterable<S> entities) { return saveAll(entities); }
        @Override public void deleteAllInBatch(Iterable<MediaAsset> entities) { deleteAll(entities); }
        @Override public void deleteAllByIdInBatch(Iterable<String> ids) { deleteAllById(ids); }
        @Override public void deleteAllInBatch() { deleteAll(); }
        @Override public MediaAsset getOne(String id) { return store.get(id); }
        @Override public MediaAsset getById(String id) { return store.get(id); }
        @Override public MediaAsset getReferenceById(String id) { return store.get(id); }
        @Override public <S extends MediaAsset> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends MediaAsset> java.util.List<S> findAll(org.springframework.data.domain.Example<S> example) { return java.util.List.of(); }
        @Override public <S extends MediaAsset> java.util.List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return java.util.List.of(); }
        @Override public <S extends MediaAsset> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends MediaAsset> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends MediaAsset> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends MediaAsset, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
    }
}
