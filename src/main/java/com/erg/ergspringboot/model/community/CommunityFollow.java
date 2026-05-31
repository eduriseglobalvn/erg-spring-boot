package com.erg.ergspringboot.model.community;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.erg.ergspringboot.model.users.User;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go relational core models.
 */
@Entity
@Table(name = "community_follows")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommunityFollow {
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    private String tenantId;

    private String targetType;

    private String targetId;


    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false, insertable = false, updatable = false)
    private User user;

    private OffsetDateTime createdAt;
}
