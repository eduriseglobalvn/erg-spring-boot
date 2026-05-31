package com.erg.ergspringboot.model.lms;

import com.erg.ergspringboot.model.BaseEntity;
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
public class InternalDocument extends BaseEntity {

    private String tenantId;

    private String type;

    private String title;

    private String subjectId;

    private String fileId;

    private String content;

    private String authorId;
}
