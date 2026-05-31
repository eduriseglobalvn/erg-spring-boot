package com.erg.ergspringboot.model.crm;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
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
                @Index(name = "idx_crm_school_activities_actor", columnList = "actor_user_id"),
                @Index(name = "idx_crm_school_activities_follow_up", columnList = "next_follow_up_at")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CrmSchoolActivity extends BaseEntity {

    @Column(name = "school_id", length = 36, nullable = false)
    private String schoolId;

    @Column(name = "deal_id", length = 36)
    private String dealId;

    @Column(name = "actor_user_id")
    private String actorUserId;

    @Column(name = "type")
    private String type;

    @Column(name = "content", length = 4000)
    private String content;

    @Column(name = "next_follow_up_at")
    private OffsetDateTime nextFollowUpAt;
}
