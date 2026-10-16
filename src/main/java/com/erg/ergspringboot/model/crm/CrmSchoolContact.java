package com.erg.ergspringboot.model.crm;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "crm_school_contacts",
        indexes = {
                @Index(name = "idx_crm_school_contacts_school", columnList = "school_id"),
                @Index(name = "idx_crm_school_contacts_type", columnList = "school_id, contact_type"),
                @Index(name = "idx_crm_school_contacts_phone", columnList = "phone")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class CrmSchoolContact extends BaseEntity {

    @Column(name = "school_id", length = 36, nullable = false)
    private String schoolId;

    @Column(name = "contact_type")
    private String contactType;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "position")
    private String position;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "zalo")
    private String zalo;

    @Column(name = "is_primary")
    private Boolean isPrimary;

    @Column(name = "note", length = 1000)
    private String note;
}
