package com.erg.ergspringboot.service.profiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.request.profiles.ProfileRequests.CreateProfileRequest;
import com.erg.ergspringboot.users.persistence.ProfileRepository;
import com.erg.ergspringboot.users.persistence.User;
import com.erg.ergspringboot.users.persistence.UserRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProfileServiceTest {
    @Test
    void socialLinksAndSpecialtiesComeBackInTheResponse() {
        UserRepository users = mock(UserRepository.class);
        ProfileRepository profiles = mock(ProfileRepository.class);
        when(users.findByIdAndTenantId("u1", "t")).thenReturn(Optional.of(new User()));
        when(profiles.findByUserId("u1")).thenReturn(Optional.empty());
        CreateProfileRequest request = new CreateProfileRequest("u1", "Name", null, "0900", null, null, null, null, null,
                Map.of("linkedin", "https://x.example/a?b=c"), null, null, Map.of("math", "advanced"), null);

        var response = new ProfileService(profiles, users).createProfile(request, "t", false);

        assertThat(response.socialLinks().links()).containsEntry("linkedin", "https://x.example/a?b=c");
        assertThat(response.specialties()).containsEntry("math", "advanced");
    }
}
