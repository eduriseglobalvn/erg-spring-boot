package com.erg.ergspringboot.model.courses;

import com.erg.ergspringboot.model.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/courses/domain/entity/course.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Course extends BaseEntity {

    private String tenantId;

    private String title;

    private String slug;

    private String subdomain;

    private String description;

    private String thumbnailUrl;

    private String instructorId;

    private String status;

    private Integer enrollmentCount;

    private Double ratingAvg;

    private String schemaType;

    private String schemaDataJson;

    private String themeConfig;
}
