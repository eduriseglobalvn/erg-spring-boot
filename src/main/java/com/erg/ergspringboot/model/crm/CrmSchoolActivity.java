package com.erg.ergspringboot.model.crm;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "crm_school_activities",
        indexes = {
                @Index(name = "idx_crm_school_activities_school", columnList = "school_id"),
                @Index(name = "idx_crm_school_activities_deal", columnList = "deal_id"),
                @Index(name = "idx_crm_school_activities_actor", columnList = "actor_user_id"),
                @Index(name = "idx_crm_school_activities_follow_up", columnList = "next_follow_up_at")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class CrmSchoolActivity extends BaseEntity {

    @Column(name = "school_id", length = 36, nullable = false)
    private String schoolId;

    @Column(name = "deal_id", length = 36)
    private String dealId;

    @Column(name = "actor_user_id", length = 36)
    private String actorUserId;

    @Column(name = "type")
    private String type;

    @Column(name = "content", length = 4000)
    private String content;

    @Column(name = "next_follow_up_at")
    private OffsetDateTime nextFollowUpAt;

    @Column(length = 500)
    private String title;

    @Column(name = "due_at")
    private LocalDate dueAt;

    @Column(nullable = false)
    private Boolean done = false;

    @Column(name = "idempotency_key", length = 192)
    private String idempotencyKey;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
