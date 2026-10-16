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
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Entity
@Table(
        name = "lms_questions",
        indexes = {
                @Index(name = "idx_lms_questions_tenant_status", columnList = "tenant_id, status"),
                @Index(name = "idx_lms_questions_tenant_subject", columnList = "tenant_id, subject_id"),
                @Index(name = "idx_lms_questions_tenant_topic", columnList = "tenant_id, topic_id")
        }
)
public class Question extends BaseEntity {

    @Column(name = "tenant_id")
    private String tenantId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scope", columnDefinition = "jsonb")
    private ContentScope scope;

    @Column(name = "subject_id")
    private String subjectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", referencedColumnName = "id", insertable = false, updatable = false)
    @ToString.Exclude
    private CurriculumSubject subject;

    @Column(name = "level_id")
    private String levelId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "level_id", referencedColumnName = "id", insertable = false, updatable = false)
    @ToString.Exclude
    private CurriculumLevel level;

    @Column(name = "topic_id")
    private String topicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", referencedColumnName = "id", insertable = false, updatable = false)
    @ToString.Exclude
    private CurriculumTopic topic;

    @Column(name = "category_id")
    private String categoryId;

    private String type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type", referencedColumnName = "id", insertable = false, updatable = false)
    @ToString.Exclude
    private QuestionType questionType;

    private String stem;

    private String objective;

    private String difficulty;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", columnDefinition = "jsonb")
    private List<String> tags;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "choices", columnDefinition = "jsonb")
    private List<QuestionChoice> choices;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer", columnDefinition = "jsonb")
    private Object answer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", columnDefinition = "jsonb")
    private Map<String, Object> content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings", columnDefinition = "jsonb")
    private Map<String, Object> settings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "mastery_rate")
    private Double masteryRate;

    @Column(name = "usage_count")
    private Integer usageCount;

    @Column(name = "schools_using")
    private Integer schoolsUsing;

    @Column(name = "owner_user_id")
    private String ownerUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", referencedColumnName = "id", insertable = false, updatable = false)
    @ToString.Exclude
    private User owner;

    @Column(name = "schema_version")
    private Integer schemaVersion;

    private String status;

    @Column(name = "lifecycle_state", nullable = false, length = 32)
    private String lifecycleState = "DRAFT";

    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @jakarta.persistence.Version
    @Column(name = "record_version", nullable = false)
    private Long recordVersion = 0L;
}
