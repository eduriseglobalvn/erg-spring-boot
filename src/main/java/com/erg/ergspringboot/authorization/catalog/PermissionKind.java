package com.erg.ergspringboot.authorization.catalog;

/** XEM/SUA are classified by the role rules; WORKSPACE (gates and self-service) is granted to roles explicitly. */
public enum PermissionKind {
    XEM,
    SUA,
    WORKSPACE
}
