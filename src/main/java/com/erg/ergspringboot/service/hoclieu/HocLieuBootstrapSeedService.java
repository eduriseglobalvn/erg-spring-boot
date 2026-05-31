package com.erg.ergspringboot.service.hoclieu;

import com.erg.ergspringboot.model.hoclieu.AssetRecord;
import com.erg.ergspringboot.model.hoclieu.ResourceItemRecord;
import com.erg.ergspringboot.model.hoclieu.ResourceRecord;
import com.erg.ergspringboot.model.hoclieu.TaxonomyNode;
import com.erg.ergspringboot.repository.hoclieu.AssetRecordRepository;
import com.erg.ergspringboot.repository.hoclieu.ResourceItemRecordRepository;
import com.erg.ergspringboot.repository.hoclieu.ResourceRecordRepository;
import com.erg.ergspringboot.repository.hoclieu.TaxonomyNodeRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HocLieuBootstrapSeedService {

    private static final String TENANT_ID = "erg";
    private static final String ACTIVE = "active";
    private static final String PUBLISHED = "published";
    private static final String OPEN = "open";

    private final TaxonomyNodeRepository taxonomyNodeRepository;
    private final ResourceRecordRepository resourceRecordRepository;
    private final AssetRecordRepository assetRecordRepository;
    private final ResourceItemRecordRepository resourceItemRecordRepository;

    public HocLieuBootstrapSeedService(
            TaxonomyNodeRepository taxonomyNodeRepository,
            ResourceRecordRepository resourceRecordRepository,
            AssetRecordRepository assetRecordRepository,
            ResourceItemRecordRepository resourceItemRecordRepository
    ) {
        this.taxonomyNodeRepository = taxonomyNodeRepository;
        this.resourceRecordRepository = resourceRecordRepository;
        this.assetRecordRepository = assetRecordRepository;
        this.resourceItemRecordRepository = resourceItemRecordRepository;
    }

    @Transactional("postgresTransactionManager")
    public void seedDefaultLibrary() {
        // Seed mirrors the Go HocLieu IC3 GS6 structure so the new read model has a real tree to render.
        upsertTaxonomy("ic3-gs6", "subject", "IC3 GS6", "ic3-gs6", null, null, null, 1, "Môn học IC3 GS6.");
        upsertTaxonomy("ic3-gs6-level-1", "category", "Level 1", "level-1", null, "ic3-gs6", null, 1, "Nhóm học liệu Level 1.");
        upsertTaxonomy("ic3-gs6-level-2", "category", "Level 2", "level-2", null, "ic3-gs6", null, 2, "Nhóm học liệu Level 2.");
        upsertTaxonomy("ic3-gs6-level-3", "category", "Level 3", "level-3", null, "ic3-gs6", null, 3, "Nhóm học liệu Level 3.");

        upsertTaxonomy("ic3-gs6-level-1-lesson-1", "section", "Bài 01. Thiết bị số và thao tác cơ bản", "bai-01-thiet-bi-so-va-thao-tac-co-ban", "ic3-gs6-level-1", "ic3-gs6", "ic3-gs6-level-1", 1, "Bài học mở đầu cho Level 1.");
        upsertTaxonomy("ic3-gs6-level-1-lesson-2", "section", "Bài 02. Internet an toàn", "bai-02-internet-an-toan", "ic3-gs6-level-1", "ic3-gs6", "ic3-gs6-level-1", 2, "An toàn số và hành vi trực tuyến.");
        upsertTaxonomy("ic3-gs6-level-2-lesson-1", "section", "Bài 01. Excel nhập môn", "bai-01-excel-nhap-mon", "ic3-gs6-level-2", "ic3-gs6", "ic3-gs6-level-2", 1, "Bảng tính cơ bản.");
        upsertTaxonomy("ic3-gs6-level-3-lesson-1", "section", "Bài 01. Bảo mật tài khoản", "bai-01-bao-mat-tai-khoan", "ic3-gs6-level-3", "ic3-gs6", "ic3-gs6-level-3", 1, "Bảo mật và dữ liệu.");

        seedResource("res-ic3-gs6-l1-slide", "IC3 GS6 Level 1 - Slide bài giảng", "lecture", "PPTX", "ic3-gs6-level-1", "ic3-gs6-level-1-lesson-1");
        seedResource("res-ic3-gs6-l1-exercise", "IC3 GS6 Level 1 - Câu hỏi bài tập", "exercise", "QUIZ", "ic3-gs6-level-1", "ic3-gs6-level-1-lesson-1");
        seedResource("res-ic3-gs6-l2-slide", "IC3 GS6 Level 2 - Excel nhập môn", "lecture", "PPTX", "ic3-gs6-level-2", "ic3-gs6-level-2-lesson-1");
        seedResource("res-ic3-gs6-l3-quiz", "IC3 GS6 Level 3 - Kiểm tra mô phỏng", "exercise", "QUIZ", "ic3-gs6-level-3", "ic3-gs6-level-3-lesson-1");
    }

    private void upsertTaxonomy(
            String id,
            String kind,
            String label,
            String slug,
            String parentId,
            String subjectId,
            String categoryId,
            int sortOrder,
            String description
    ) {
        TaxonomyNode node = taxonomyNodeRepository.findById(id).orElseGet(() -> {
            TaxonomyNode created = new TaxonomyNode();
            created.setId(id);
            return created;
        });
        node.setTenantId(TENANT_ID);
        node.setKind(kind);
        node.setLabel(label);
        node.setSlug(slug);
        node.setParentId(parentId);
        node.setSubjectId(subjectId);
        node.setCategoryId(categoryId);
        node.setSortOrder(sortOrder);
        node.setStatus(ACTIVE);
        node.setDescription(description);
        taxonomyNodeRepository.save(node);
    }

    private void seedResource(String id, String title, String type, String fileType, String groupId, String lessonId) {
        ResourceRecord resource = resourceRecordRepository.findById(id).orElseGet(() -> {
            ResourceRecord created = new ResourceRecord();
            created.setId(id);
            return created;
        });
        resource.setTenantId(TENANT_ID);
        resource.setProgramSlug("ic3-digital-literacy");
        resource.setSubjectId("ic3-gs6");
        resource.setCategoryId(groupId);
        resource.setSectionId(lessonId);
        resource.setDocumentTypeId(type);
        resource.setTitle(title);
        resource.setSlug(id.replace("res-", ""));
        resource.setThumbnailUrl("/mock/hoclieu/" + id + ".png");
        resource.setTags(List.of("ic3-gs6", fileType.toLowerCase()));
        resource.setVisibility(OPEN);
        resource.setStatus(PUBLISHED);
        resource.setPublishedAt(OffsetDateTime.now(ZoneOffset.UTC));
        resourceRecordRepository.save(resource);

        // Each seeded resource receives one launchable asset so FE can open it directly from bootstrap data.
        String assetId = "asset-" + id.replace("res-", "");
        AssetRecord asset = assetRecordRepository.findById(assetId).orElseGet(() -> {
            AssetRecord created = new AssetRecord();
            created.setId(assetId);
            return created;
        });
        asset.setResourceId(id);
        asset.setTitle(title);
        asset.setSelectedFileType(fileType);
        asset.setLaunchMode(defaultLaunchMode(fileType));
        asset.setStorageProvider("seed");
        asset.setStorageUrl("/mock/hoclieu/" + assetId);
        asset.setCanDownload(false);
        asset.setStatus("ready");
        assetRecordRepository.save(asset);

        ResourceItemRecord item = resourceItemRecordRepository.findById("item-" + id.replace("res-", "")).orElseGet(() -> {
            ResourceItemRecord created = new ResourceItemRecord();
            created.setId("item-" + id.replace("res-", ""));
            return created;
        });
        item.setTenantId(TENANT_ID);
        item.setResourceId(id);
        item.setAssetId(assetId);
        item.setUnitTitle(groupId);
        item.setLessonTitle(lessonId);
        item.setSortOrder(1);
        resourceItemRecordRepository.save(item);
    }

    private String defaultLaunchMode(String fileType) {
        return switch (fileType) {
            case "PPTX" -> "slide_image_proxy";
            case "QUIZ" -> "quiz_runtime";
            case "PDF" -> "pdf_reader";
            case "VIDEO" -> "video_player";
            case "AUDIO" -> "audio_player";
            default -> "download_only";
        };
    }
}
