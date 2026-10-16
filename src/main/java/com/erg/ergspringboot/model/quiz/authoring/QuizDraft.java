package com.erg.ergspringboot.model.quiz.authoring;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Mutable authoring working document (spec §1.2). Mirrors the FE editor tree 1:1 as JSONB blobs
 * ({@code settings}, {@code playerTemplate}, {@code tree}) plus optimistic-lock/version routing
 * columns. Slide fields are intentionally NOT normalized here — the whole document is one blob.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Entity
@Table(
        name = "quiz_drafts",
        indexes = {
                @Index(name = "idx_quiz_drafts_tenant_quiz", columnList = "tenant_id, quiz_id")
        }
)
public class QuizDraft extends BaseEntity {

    @Column(name = "quiz_id", nullable = false)
    private String quizId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings", columnDefinition = "jsonb")
    private Map<String, Object> settings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "player_template", columnDefinition = "jsonb")
    private Map<String, Object> playerTemplate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tree", columnDefinition = "jsonb")
    private Object tree;

    @Column(name = "schema_version", nullable = false)
    private Integer schemaVersion;

    @Column(name = "doc_version", nullable = false)
    private Long docVersion;

    @Column(name = "base_version_id")
    private String baseVersionId;

    @Column(name = "updated_by")
    private String updatedBy;
}
