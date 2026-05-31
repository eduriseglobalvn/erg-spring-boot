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

    @Column(name = "level_id")
    private String levelId;

    @Column(name = "topic_id")
    private String topicId;

    private String type;

    private String stem;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "choices", columnDefinition = "jsonb")
    private List<QuestionChoice> choices;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer", columnDefinition = "jsonb")
    private Object answer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    private String status;

    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;
}
