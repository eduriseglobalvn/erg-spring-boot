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
        name = "crm_school_profiles",
        indexes = {
                @Index(name = "idx_crm_school_profiles_school", columnList = "school_id"),
                @Index(name = "idx_crm_school_profiles_owner", columnList = "owner_user_id"),
                @Index(name = "idx_crm_school_profiles_referrer", columnList = "introduced_by_user_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CrmSchoolProfile extends BaseEntity {

    @Column(name = "school_id", length = 36, nullable = false)
    private String schoolId;

    @Column(name = "owner_user_id", length = 36)
    private String ownerUserId;

    @Column(name = "lead_owner_user_id", length = 36)
    private String leadOwnerUserId;

    @Column(name = "manager_user_id", length = 36)
    private String managerUserId;

    @Column(name = "source")
    private String source;

    @Column(name = "campaign")
    private String campaign;

    @Column(name = "introduced_by_user_id", length = 36)
    private String introducedByUserId;

    @Column(name = "introduced_by_name")
    private String introducedByName;

    @Column(name = "introduced_by_phone")
    private String introducedByPhone;

    @Column(name = "introduced_by_note")
    private String introducedByNote;

    @Column(name = "province")
    private String province;

    @Column(name = "district")
    private String district;

    @Column(name = "address", length = 1000)
    private String address;

    @Column(name = "note", length = 2000)
    private String note;
}
