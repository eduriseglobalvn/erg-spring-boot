package com.erg.ergspringboot.model.content;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.erg.ergspringboot.model.BaseEntity;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go relational core models.
 */
@Entity
@Table(name = "pages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Page extends BaseEntity {

    private String tenantId;

    private String domain;

    private String slug;

    private String title;

    private String content;

    private String metaTitle;

    private String metaDescription;

    private String faqJson;

    private String status;

    private OffsetDateTime publishedAt;
}
