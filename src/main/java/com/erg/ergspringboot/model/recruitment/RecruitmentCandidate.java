package com.erg.ergspringboot.model.recruitment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.erg.ergspringboot.model.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go relational core models.
 */
@Entity
@Table(name = "candidates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class RecruitmentCandidate extends BaseEntity {

    @Column(name = "job_id", length = 36, nullable = false)
    private String jobId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", referencedColumnName = "id", nullable = false, insertable = false, updatable = false)
    private RecruitmentJob job;

    private String jobTitle;

    private String tenantId;

    private String fullName;

    private String email;

    private String phone;

    private String cvUrl;

    private String coverLetter;

    private String note;

    private String publicNote;

    private String applyType;

    private String status;

    private String trackingCode;
}
