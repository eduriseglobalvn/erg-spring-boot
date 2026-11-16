package com.erg.ergspringboot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.request.profiles.ProfileRequests.CreateProfileRequest;
import com.erg.ergspringboot.controller.profiles.ProfileController;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.platform.security.ErgPolicyService;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.service.profiles.ProfileService;
import com.erg.ergspringboot.users.persistence.Profile;
import com.erg.ergspringboot.users.persistence.ProfileRepository;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ProfileControllerAccessTest {
    private final ProfileService service = mock(ProfileService.class);
    private final MockHttpServletRequest http = new MockHttpServletRequest();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void studentCannotCreateProfileForOtherUser() {
        authenticate(principal("student-1", List.of("student"), List.of("lms.learner.read")));
        CreateProfileRequest body = new CreateProfileRequest("victim-1", "X", null, null, null, null, null, null,
                null, null, null, null, null, "note");

        assertThatThrownBy(() -> controller(service).createProfile(body, http)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(service);
    }

    @Test
    void selfReadOmitsInternalNote() {
        UserRepository users = mock(UserRepository.class);
        ProfileRepository profiles = mock(ProfileRepository.class);
        when(users.findByIdAndTenantId("u1", "tenant-a")).thenReturn(Optional.of(new User()));
        Profile profile = new Profile();
        profile.setUserId("u1");
        profile.setInternalNote("secret");
        when(profiles.findByUserId("u1")).thenReturn(Optional.of(profile));
        ProfileController controller = controller(new ProfileService(profiles, users));

        // Even a manager reading his own profile does not get the note back.
        authenticate(principal("u1", List.of("pdt_operator"), List.of("users.manage")));
        assertThat(controller.getProfile("u1", http).data().internalNote()).isNull();

        authenticate(principal("manager-1", List.of("pdt_operator"), List.of("users.manage")));
        when(users.findByIdAndTenantId("u1", "tenant-a")).thenReturn(Optional.of(new User()));
        assertThat(controller.getProfile("u1", http).data().internalNote()).isEqualTo("secret");
    }

    @Test
    void manageCheckHonoursWildcard() {
        authenticate(principal("admin-1", List.of("system.super_admin"), List.of("*")));
        controller(service).getProfile("other-1", http);
        verify(service).getProfile("other-1", "tenant-a", true);
    }

    @Test
    void userOutsideTheCallersTenantIsNotFound() {
        UserRepository users = mock(UserRepository.class);
        when(users.findByIdAndTenantId(any(), any())).thenReturn(Optional.empty());
        authenticate(principal("admin-1", List.of("system.super_admin"), List.of("*")));

        assertThatThrownBy(() -> controller(new ProfileService(mock(ProfileRepository.class), users))
                .getProfile("other-tenant-user", http))
                .isInstanceOf(com.erg.ergspringboot.exception.ResourceNotFoundException.class);
    }

    private ProfileController controller(ProfileService profileService) {
        return new ProfileController(profileService, new ErgPolicyService());
    }

    private void authenticate(ErgPrincipal principal) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private ErgPrincipal principal(String userId, List<String> roles, List<String> permissions) {
        return new ErgPrincipal(userId, "session-1", "tenant-a", roles, permissions, List.of(),
                List.of("admin"), "staff", "user", Map.of());
    }
}
