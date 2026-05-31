package com.erg.ergspringboot.model.configuration;

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
@Table(name = "system_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfig {
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "\"key\"")
    private String key;

    private String valueJson;

    private String description;

    private String updatedBy;

    private OffsetDateTime updatedAt;
}
