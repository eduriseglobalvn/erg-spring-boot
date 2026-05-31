package com.erg.ergspringboot.model.lms;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scope", columnDefinition = "jsonb")
    private ContentScope scope;

    private String title;

    private String kind;

    @Column(name = "subject_id")
    private String subjectId;

    @Column(name = "level_id")
    private String levelId;

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
}
