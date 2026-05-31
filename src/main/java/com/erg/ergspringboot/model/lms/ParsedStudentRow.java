package com.erg.ergspringboot.model.lms;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/lms/application/service/entities.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParsedStudentRow {

    private String rowId;

    private Integer rowNumber;

    private String studentCode;

    private String fullName;

    private String className;

    private String email;

    private String gender;

    private OffsetDateTime birthday;

    private String phone;

    private String address;

    private String parentName;

    private String parentPhone;

    private String parentEmail;

    private String parentRelationship;

    private OffsetDateTime enrollmentDate;

    private String note;

    private String status;

    private List<String> messages;
}
