package com.erg.ergspringboot.model.reviews;

import com.erg.ergspringboot.model.BaseEntity;
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
@EqualsAndHashCode(callSuper = true)
public class Review extends BaseEntity {

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
