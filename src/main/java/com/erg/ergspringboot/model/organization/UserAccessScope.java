package com.erg.ergspringboot.model.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.erg.ergspringboot.model.BaseEntity;
import com.erg.ergspringboot.model.users.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go relational core models.
 */
@Entity
@Table(
        name = "user_access_scopes",
        indexes = {
                @Index(name = "idx_user_access_scopes_user", columnList = "user_id"),
                @Index(name = "idx_user_access_scopes_education_unit", columnList = "education_unit_id"),
                @Index(name = "idx_user_access_scopes_user_education_unit", columnList = "user_id, education_unit_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserAccessScope extends BaseEntity {

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false, insertable = false, updatable = false)
    private User user;


    @Column(name = "education_unit_id", length = 36, nullable = false)
    private String educationUnitId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "education_unit_id", referencedColumnName = "id", nullable = false, insertable = false, updatable = false)
    private EducationUnit educationUnit;

    private String modules;

    private String role;
}
