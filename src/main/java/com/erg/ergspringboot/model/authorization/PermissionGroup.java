package com.erg.ergspringboot.model.authorization;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Grouping metadata for authorization permissions.
 */
@Entity
@Table(name = "permission_groups")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PermissionGroup extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    private String label;

    @Column(name = "display_order")
    private Integer order;
}
