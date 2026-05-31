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
public class Seo404Log {

    private String id;

    private String url;

    private String referrer;

    private String userAgent;

    private Integer hitCount;

    private OffsetDateTime lastSeen;

    private OffsetDateTime firstSeen;
}
