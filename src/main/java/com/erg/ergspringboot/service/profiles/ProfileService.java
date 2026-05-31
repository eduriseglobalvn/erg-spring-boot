package com.erg.ergspringboot.service.profiles;

import com.erg.ergspringboot.common.request.profiles.ProfileRequests.CreateProfileRequest;
import com.erg.ergspringboot.common.request.profiles.ProfileRequests.UpdateProfileRequest;
import com.erg.ergspringboot.common.response.profiles.ProfileResponses.ProfileResponse;
import com.erg.ergspringboot.common.response.profiles.ProfileResponses.SocialLinksResponse;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.users.Profile;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.users.ProfileRepository;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;

    public ProfileService(ProfileRepository profileRepository, UserRepository userRepository) {
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(String userId) {
        return toResponse(findByUserId(userId));
    }

    @Transactional(readOnly = true)
    public ProfileResponse getCurrentProfile(String userId) {
        return getProfile(userId);
    }

    @Transactional
    public ProfileResponse createProfile(CreateProfileRequest request) {
        userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("user not found"));
        if (profileRepository.findByUserId(request.userId()).isPresent()) {
            throw new ConflictException("profile already exists");
        }
        Profile profile = new Profile();
        profile.setId(IdGenerator.newId());
        profile.setUserId(request.userId());
        apply(profile, request);
        profile.setIsProfileCompleted(isCompleted(profile));
        profileRepository.save(profile);
        return toResponse(profile);
    }

    @Transactional
    public ProfileResponse updateProfile(String userId, UpdateProfileRequest request) {
        Profile profile = findByUserId(userId);
        apply(profile, request);
        profile.setIsProfileCompleted(isCompleted(profile));
        profileRepository.save(profile);
        return toResponse(profile);
    }

    @Transactional
    public void deleteProfile(String userId) {
        Profile profile = findByUserId(userId);
        profileRepository.delete(profile);
    }

    private Profile findByUserId(String userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("profile not found"));
    }

    private void apply(Profile profile, CreateProfileRequest request) {
        profile.setFullName(trimToNull(request.fullName()));
        profile.setBio(trimToNull(request.bio()));
        profile.setPhone(trimToNull(request.phone()));
        profile.setDateOfBirth(parseDate(request.dateOfBirth()));
        profile.setGender(trimToNull(request.gender()));
        profile.setAddress(trimToNull(request.address()));
        profile.setCity(trimToNull(request.city()));
        profile.setDistrict(trimToNull(request.district()));
        profile.setSocialLinksJson(toKeyValueString(request.socialLinks()));
        profile.setAvatarUrl(trimToNull(request.avatarUrl()));
        profile.setTeachingPhilosophy(trimToNull(request.teachingPhilosophy()));
        profile.setSpecialtiesJson(toKeyValueString(request.specialties()));
        profile.setInternalNote(trimToNull(request.internalNote()));
    }

    private void apply(Profile profile, UpdateProfileRequest request) {
        if (request.fullName() != null) profile.setFullName(trimToNull(request.fullName()));
        if (request.bio() != null) profile.setBio(trimToNull(request.bio()));
        if (request.phone() != null) profile.setPhone(trimToNull(request.phone()));
        if (request.dateOfBirth() != null) profile.setDateOfBirth(parseDate(request.dateOfBirth()));
        if (request.gender() != null) profile.setGender(trimToNull(request.gender()));
        if (request.address() != null) profile.setAddress(trimToNull(request.address()));
        if (request.city() != null) profile.setCity(trimToNull(request.city()));
        if (request.district() != null) profile.setDistrict(trimToNull(request.district()));
        if (request.socialLinks() != null) profile.setSocialLinksJson(toKeyValueString(request.socialLinks()));
        if (request.avatarUrl() != null) profile.setAvatarUrl(trimToNull(request.avatarUrl()));
        if (request.teachingPhilosophy() != null) profile.setTeachingPhilosophy(trimToNull(request.teachingPhilosophy()));
        if (request.specialties() != null) profile.setSpecialtiesJson(toKeyValueString(request.specialties()));
        if (request.internalNote() != null) profile.setInternalNote(trimToNull(request.internalNote()));
    }

    private ProfileResponse toResponse(Profile profile) {
        return new ProfileResponse(
                profile.getId(),
                profile.getUserId(),
                profile.getFullName(),
                profile.getBio(),
                profile.getPhone(),
                iso(profile.getDateOfBirth()),
                profile.getGender(),
                profile.getAddress(),
                profile.getCity(),
                profile.getDistrict(),
                new SocialLinksResponse(Map.of()),
                profile.getAvatarUrl(),
                Boolean.TRUE.equals(profile.getIsProfileCompleted()),
                profile.getTeachingPhilosophy(),
                Map.of(),
                profile.getRating(),
                profile.getInternalNote(),
                iso(profile.getCreatedAt()),
                iso(profile.getUpdatedAt())
        );
    }

    private boolean isCompleted(Profile profile) {
        return profile.getFullName() != null && !profile.getFullName().isBlank()
                && profile.getPhone() != null && !profile.getPhone().isBlank();
    }

    private OffsetDateTime parseDate(String value) {
        return value == null || value.isBlank() ? null : OffsetDateTime.parse(value);
    }

    private String toKeyValueString(Map<String, String> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return value.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(";"));
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String iso(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }
}
