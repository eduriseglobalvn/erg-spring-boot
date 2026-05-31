package com.erg.ergspringboot.model.lms;

import com.erg.ergspringboot.model.BaseEntity;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/lms/application/service/entities.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Announcement extends BaseEntity {

    private String tenantId;

    private String targetType;

    private List<String> academicClassIds;

    private List<String> studentIds;

    private String title;

    private String content;

    private Boolean pinned;

    private String authorId;
}
