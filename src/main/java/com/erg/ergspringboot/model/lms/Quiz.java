package com.erg.ergspringboot.model.lms;

import com.erg.ergspringboot.model.BaseEntity;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.model.curriculum.CurriculumLevel;
import com.erg.ergspringboot.model.curriculum.CurriculumSubject;
import com.erg.ergspringboot.model.curriculum.CurriculumTopic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Migrated from erg-go internal/modules/lms/application/service/entities.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
        name = "lms_quizzes",
        indexes = {
                @Index(name = "idx_lms_quizzes_tenant_status", columnList = "tenant_id, status"),
                @Index(name = "idx_lms_quizzes_tenant_subject", columnList = "tenant_id, subject_id")
        }
)
public class Quiz extends BaseEntity {

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "portal")
    private String portal;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scope", columnDefinition = "jsonb")
    private ContentScope scope;

    private String title;

    private String subtitle;

    private String kind;

    @Column(name = "subject_id")
    private String subjectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", referencedColumnName = "id", insertable = false, updatable = false)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private CurriculumSubject subject;

    @Column(name = "level_id")
    private String levelId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "level_id", referencedColumnName = "id", insertable = false, updatable = false)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private CurriculumLevel level;

    @Column(name = "category_id")
    private String categoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", referencedColumnName = "id", insertable = false, updatable = false)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private CurriculumTopic category;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "topic_ids", columnDefinition = "jsonb")
    private List<String> topicIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_ids", columnDefinition = "jsonb")
    private List<String> questionIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "slides", columnDefinition = "jsonb")
    private List<Object> slides;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings", columnDefinition = "jsonb")
    private Map<String, Object> settings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", columnDefinition = "jsonb")
    private Map<String, Object> result;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "theme", columnDefinition = "jsonb")
    private Map<String, Object> theme;

    @Column(name = "theme_id")
    private String themeId;

    private String status;

    private Integer version;

    @Column(name = "package_hash")
    private String packageHash;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "current_version_id")
    private String currentVersionId;

    @Column(name = "latest_draft_id")
    private String latestDraftId;

    @Column(name = "owner_user_id")
    private String ownerUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", referencedColumnName = "id", insertable = false, updatable = false)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private User owner;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_scope", columnDefinition = "jsonb")
    private Map<String, Object> contentScope;

    @Column(name = "schema_version")
    private Integer schemaVersion;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
