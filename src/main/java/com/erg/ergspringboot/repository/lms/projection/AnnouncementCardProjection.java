package com.erg.ergspringboot.repository.lms.projection;

import java.time.OffsetDateTime;

public record AnnouncementCardProjection(
        String id,
        String title,
        String content,
        Boolean pinned,
        OffsetDateTime publishedAt,
        OffsetDateTime updatedAt
) {
}
