package com.erg.ergspringboot.model.recruitment;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

import com.erg.ergspringboot.model.BaseEntity;
import com.erg.ergspringboot.model.users.User;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Migrated from erg-go relational core models.
 */
@Entity
@Table(name = "jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RecruitmentJob extends BaseEntity {

    private String tenantId;

    private String slug;

    private String title;

    private String status;

    private Boolean isHot;

    private Boolean isNew;

    private Boolean isUrgent;

    private String salary;

    private Double salaryMin;

    private Double salaryMax;

    private String salaryCurrency;

    private Integer quantity;

    private Integer viewCount;

    private String workType;

    private String workSchedule;

    private String postDate;

    private String deadline;

    private OffsetDateTime deadlineDate;

    private String location;

    private String streetAddr;

    private String city;

    private String country;

    private String employmentType;

    private String summary;

    private String descriptionJson;

    private String requirementsJson;

    private String benefitsJson;

    private Boolean isActive;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", referencedColumnName = "id", insertable = false, updatable = false)
    private User createdByUser;

    @OneToMany(mappedBy = "job", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<RecruitmentCandidate> candidates = new LinkedHashSet<>();
}
