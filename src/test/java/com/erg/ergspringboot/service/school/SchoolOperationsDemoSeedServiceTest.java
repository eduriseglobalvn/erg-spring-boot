package com.erg.ergspringboot.service.school;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.repository.lms.ClassroomRepository;
import com.erg.ergspringboot.repository.lms.StudentRepository;
import com.erg.ergspringboot.repository.lms.SubjectRepository;
import com.erg.ergspringboot.repository.school.SchoolOperationalProfileRepository;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.model.users.User;
import com.erg.ergspringboot.model.school.SchoolOperationalProfile;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchoolOperationsDemoSeedServiceTest {
    @Mock SchoolOperationalProfileRepository profileRepository;
    @Mock SchoolCommandService commandService;
    @Mock UserRepository userRepository;
    @Mock ClassroomRepository classroomRepository;
    @Mock StudentRepository studentRepository;
    @Mock SubjectRepository subjectRepository;

    @BeforeEach
    void returnSavedClassroom() {
        when(classroomRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsFiveRepresentativeSchoolsWhenTenantHasNoOperationalData() {
        when(profileRepository.countByTenantId("erg")).thenReturn(0L);
        User actor = new User();
        actor.setId("existing-user");
        when(userRepository.findFirstByTenantIdOrderByCreatedAtAsc("erg")).thenReturn(Optional.of(actor));
        when(profileRepository.findByTenantIdOrderBySchoolIdAsc("erg")).thenReturn(List.of(profile("school-primary", "PRIMARY")));
        SchoolOperationsDemoSeedService seedService = new SchoolOperationsDemoSeedService(profileRepository, commandService, userRepository, classroomRepository, studentRepository, subjectRepository);

        seedService.seedIfEmpty("erg");

        verify(commandService, times(5)).create(any(), any());
    }

    @Test
    void enrichesExistingSchoolsInsteadOfSkippingTheSeed() {
        when(profileRepository.countByTenantId("erg")).thenReturn(1L);
        when(profileRepository.findByTenantIdOrderBySchoolIdAsc("erg")).thenReturn(List.of(profile("school-secondary", "SECONDARY")));
        SchoolOperationsDemoSeedService seedService = new SchoolOperationsDemoSeedService(profileRepository, commandService, userRepository, classroomRepository, studentRepository, subjectRepository);

        seedService.seedIfEmpty("erg");

        verify(commandService, never()).create(any(), any());
        verify(classroomRepository, times(8)).save(any());
        verify(studentRepository, times(8)).saveAll(any());
        verify(subjectRepository, times(4)).save(any());
    }

    private SchoolOperationalProfile profile(String schoolId, String schoolType) {
        SchoolOperationalProfile profile = new SchoolOperationalProfile();
        profile.setSchoolId(schoolId);
        profile.setTenantId("erg");
        profile.setSchoolType(schoolType);
        profile.setOnboardingStatus("ACTIVE");
        return profile;
    }
}
