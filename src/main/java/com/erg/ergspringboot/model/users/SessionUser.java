package com.erg.ergspringboot.model.users;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Migrated from erg-go internal/modules/sessions/domain/entity/session.go.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionUser {

    private String id;

    private String tenantId;

    private String email;

    private String fullName;

    private String avatarUrl;

    private String provider;

    private String accountType;

    private String lastLoginProvider;

    private String status;

    private List<String> roles;

    private Boolean isProfileCompleted;
}
