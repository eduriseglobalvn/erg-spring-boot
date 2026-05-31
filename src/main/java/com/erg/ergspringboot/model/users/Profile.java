package com.erg.ergspringboot.model.users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import com.erg.ergspringboot.model.BaseEntity;
import com.erg.ergspringboot.model.users.User;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go relational core models.
 */
@Entity
@Table(name = "profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Profile extends BaseEntity {

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false, insertable = false, updatable = false)
    private User user;

    private String fullName;

    private String bio;

    private String phone;

    private OffsetDateTime dateOfBirth;

    private String gender;

    private String address;

    private String city;

    private String district;

    private String socialLinksJson;

    private String avatarUrl;

    private Boolean isProfileCompleted;

    private String teachingPhilosophy;

    private String specialtiesJson;

    private Double rating;

    private String internalNote;
}
