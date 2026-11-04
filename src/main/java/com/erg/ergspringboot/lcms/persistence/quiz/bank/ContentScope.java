package com.erg.ergspringboot.lcms.persistence.quiz.bank;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/lms/application/service/entities.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContentScope {

    private String type;

    private String schoolId;
}
