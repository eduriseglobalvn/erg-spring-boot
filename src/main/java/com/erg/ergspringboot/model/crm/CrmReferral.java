package com.erg.ergspringboot.model.crm;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "crm_referrals",
        indexes = {
                @Index(name = "idx_crm_referrals_school", columnList = "school_id"),
                @Index(name = "idx_crm_referrals_user", columnList = "referrer_user_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CrmReferral extends BaseEntity {

    @Column(name = "school_id", length = 36, nullable = false)
    private String schoolId;

    @Column(name = "referrer_user_id")
    private String referrerUserId;

    @Column(name = "referrer_name")
    private String referrerName;

    @Column(name = "referrer_phone")
    private String referrerPhone;

    @Column(name = "referral_type")
    private String referralType;

    @Column(name = "reward_amount")
    private BigDecimal rewardAmount;

    @Column(name = "status")
    private String status;

    @Column(name = "note", length = 1000)
    private String note;
}
