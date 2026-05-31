package com.erg.ergspringboot.model.seo;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/seo/domain/entity/entity.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GSCData {

    private String id;

    private String postId;

    private String url;

    private Integer clicks;

    private Integer impressions;

    private Double cTR;

    private Double position;

    private OffsetDateTime date;
}
