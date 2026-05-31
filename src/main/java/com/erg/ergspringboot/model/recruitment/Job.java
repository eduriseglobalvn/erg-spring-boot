package com.erg.ergspringboot.model.recruitment;

import com.erg.ergspringboot.model.BaseEntity;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/recruitment/domain/entity/job.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Job extends BaseEntity {

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

    private String empType;

    private String summary;

    private List<String> description;

    private List<String> requirements;

    private List<String> benefits;

    private Boolean isActive;

    private String createdBy;
}
