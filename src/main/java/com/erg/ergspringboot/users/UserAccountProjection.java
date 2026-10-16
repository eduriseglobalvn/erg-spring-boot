package com.erg.ergspringboot.users;

/**
 * Immutable, persistence-neutral account view shared by modules that only need
 * directory information.  It deliberately excludes credentials, roles and
 * JPA associations so a caller cannot accidentally couple itself to the
 * users aggregate.
 */
public record UserAccountProjection(
        String id,
        String tenantId,
        String fullName,
        String email,
        String status) {

    public UserAccountProjection {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("user account id must not be blank");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("user account tenantId must not be blank");
        }
    }

    public String displayName() {
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        if (email != null && !email.isBlank()) {
            return email;
        }
        return id;
    }
}
