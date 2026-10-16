package com.erg.ergspringboot.model.publicdisclosure;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/public_disclosure/domain/entity/disclosure.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"createdAt", "updatedAt"})
public class DisclosureDocument {

    private String id;

    private java.time.OffsetDateTime createdAt;

    private java.time.OffsetDateTime updatedAt;

    private String tenantId;

    private String sectionSlug;

    private String slug;

    private String title;

    private String menuLabel;

    private String shortDescription;

    private String description;

    private String publishedAt;

    private String effectiveDate;

    private String referenceCode;

    private String issuingAuthority;

    private String reviewCycle;

    private String accessScope;

    private String heroKicker;

    private List<String> highlights;

    private List<DetailBlock> detailBlocks;

    private CoverConfig cover;

    private String documentId;

    private String thumbnailUrl;

    private String schoolYear;

    private Object watermarkConfig;
}
