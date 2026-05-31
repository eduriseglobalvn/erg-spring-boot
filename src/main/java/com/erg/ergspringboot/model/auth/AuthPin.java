package com.erg.ergspringboot.model.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go relational core models.
 */
@Entity
@Table(name = "auth_pins")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthPin {
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    private String email;

    private String code;

    private String purpose;

    private OffsetDateTime expiresAt;

    private OffsetDateTime usedAt;

    private OffsetDateTime createdAt;
}
