package com.erg.ergspringboot.model.publicdisclosure;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/public_disclosure/domain/entity/disclosure.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetailBlock {

    private String heading;

    private String body;
}
