package com.erg.ergspringboot.model.reviews;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/reviews/domain/entity/review.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"createdAt", "updatedAt"})
public class Review {

    private String id;

    private java.time.OffsetDateTime createdAt;

    private java.time.OffsetDateTime updatedAt;

    private String targetId;

    private String targetType;

    private String userId;

    private String userName;

    private String userEmail;

    private String userAvatar;

    private Integer rating;

    private String comment;

    private Boolean isVerifiedPurchase;

    private String status;

    private String adminReply;

    private String replyBy;

    private OffsetDateTime replyAt;

    private String adminNote;

    private String reviewedBy;

    private OffsetDateTime reviewedAt;

    private Boolean isFeatured;

    private Integer helpfulCount;

    private String ipAddress;

    private String userAgent;
}
