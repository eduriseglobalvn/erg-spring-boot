package com.erg.ergspringboot.platform.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorkspaceCapabilityMapperTest {
    @Test
    void mapsLegacyPortalAtTheCompatibilityBoundary() {
        assertEquals(WorkspaceCapability.ADMIN, WorkspaceCapabilityMapper.fromLegacyPortal("lcms").orElseThrow());
        assertEquals(WorkspaceCapability.TEACHER, WorkspaceCapabilityMapper.fromLegacyPortal("lms").orElseThrow());
        assertEquals(WorkspaceCapability.STUDENT, WorkspaceCapabilityMapper.fromLegacyPortal("ELEARNING").orElseThrow());
        assertEquals(WorkspaceCapability.CRM, WorkspaceCapabilityMapper.fromLegacyPortal("crm").orElseThrow());
    }

    @Test
    void rejectsUnknownOrBlankPortalNames() {
        assertTrue(WorkspaceCapabilityMapper.fromLegacyPortal("author").isEmpty());
        assertTrue(WorkspaceCapabilityMapper.fromLegacyPortal(" ").isEmpty());
        assertTrue(WorkspaceCapabilityMapper.fromLegacyPortal(null).isEmpty());
    }

    @Test
    void projectsCanonicalWorkspaceCapabilitiesWithoutChangingLegacyPortals() {
        assertEquals(java.util.List.of("ADMIN", "TEACHER"), WorkspaceCapabilityMapper.canonicalWorkspaces(java.util.List.of("lms", "lcms", "lms")));
    }

    @Test
    void projectsAllCapabilitiesForAnAuthenticatedMultiRoleAccount() {
        assertEquals(
                java.util.List.of("ADMIN", "CRM", "STUDENT", "TEACHER"),
                WorkspaceCapabilityMapper.canonicalWorkspaces(
                        java.util.List.of("lms"),
                        java.util.List.of("teacher", "student"),
                        java.util.List.of("crm.lead.read", "admin.content.read", "elearning.dashboard.read")));
    }

    @Test
    void wildcardIsExplicitlyProjectedToEveryCanonicalWorkspace() {
        assertEquals(
                java.util.List.of("ADMIN", "CRM", "STUDENT", "TEACHER"),
                WorkspaceCapabilityMapper.canonicalWorkspaces(
                        java.util.List.of("*"), java.util.List.of(), java.util.List.of()));
    }

    @Test
    void unknownRoleOrPermissionCannotGrantAWorkspace() {
        assertEquals(
                java.util.List.of(),
                WorkspaceCapabilityMapper.canonicalWorkspaces(
                        java.util.List.of(),
                        java.util.List.of("auditor"),
                        java.util.List.of("reports.read")));
    }
}
