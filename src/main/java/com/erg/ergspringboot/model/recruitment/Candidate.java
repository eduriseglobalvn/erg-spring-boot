package com.erg.ergspringboot.model.recruitment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/recruitment/domain/entity/candidate.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"createdAt", "updatedAt"})
public class Candidate {

    private String id;

    private java.time.OffsetDateTime createdAt;

    private java.time.OffsetDateTime updatedAt;

    private String jobId;

    private String jobTitle;

    private String tenantId;

    private String fullName;

    private String email;

    private String phone;

    private String cvUrl;

    private String coverLetter;

    private String note;

    private String publicNote;

    private String applyType;

    private String status;

    private String trackingCode;
}
