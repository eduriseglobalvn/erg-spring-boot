package com.erg.ergspringboot.service.school;

import static com.erg.ergspringboot.common.request.school.SchoolRequests.*;
import static com.erg.ergspringboot.common.response.school.SchoolResponses.*;

import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ConflictException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.exception.ResourceNotFoundException;
import com.erg.ergspringboot.model.organization.EducationUnit;
import com.erg.ergspringboot.model.school.SchoolAcademicYear;
import com.erg.ergspringboot.model.school.SchoolGrade;
import com.erg.ergspringboot.model.school.SchoolOperationalProfile;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.lms.ClassroomRepository;
import com.erg.ergspringboot.repository.organization.EducationUnitRepository;
import com.erg.ergspringboot.repository.school.SchoolAcademicYearRepository;
import com.erg.ergspringboot.repository.school.SchoolGradeRepository;
import com.erg.ergspringboot.repository.school.SchoolOperationalProfileRepository;
import com.erg.ergspringboot.utils.IdGenerator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchoolCommandService {
    private static final Set<String> SCHOOL_TYPES = Set.of(
            "PRIMARY", "SECONDARY", "HIGH_SCHOOL", "UNIVERSITY", "TRAINING_CENTER");
    private static final Set<String> PROFILE_STATUSES = Set.of("DRAFT", "ONBOARDING", "ACTIVE", "PAUSED", "ARCHIVED");
    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");

    private final EducationUnitRepository unitRepository;
    private final SchoolOperationalProfileRepository profileRepository;
    private final SchoolAcademicYearRepository yearRepository;
    private final SchoolGradeRepository gradeRepository;
    private final ClassroomRepository classroomRepository;

    public SchoolCommandService(
            EducationUnitRepository unitRepository,
            SchoolOperationalProfileRepository profileRepository,
            SchoolAcademicYearRepository yearRepository,
            SchoolGradeRepository gradeRepository,
            ClassroomRepository classroomRepository
    ) {
        this.unitRepository = unitRepository;
        this.profileRepository = profileRepository;
        this.yearRepository = yearRepository;
        this.gradeRepository = gradeRepository;
        this.classroomRepository = classroomRepository;
    }

    @Transactional
    @CacheEvict(cacheNames = {"organizationEducationUnits", "organizationEducationUnitEntities", "lcmsSchoolDirectory", "lcmsSchoolOperations"}, allEntries = true)
    public SchoolResponse create(CreateSchoolRequest request, UserAccessContext access) {
        String type = schoolType(request.schoolType());
        String schoolId = IdGenerator.newId();
        String slug = uniqueSlug(access.tenantId(), request.name(), schoolId);

        EducationUnit unit = new EducationUnit();
        unit.setId(schoolId);
        unit.setTenantId(access.tenantId());
        unit.setName(request.name().trim());
        unit.setSlug(slug);
        unit.setType(type);
        unit.setStatus(profileStatus(request.status(), "ONBOARDING"));
        unit.setCreatedBy(access.userId());
        unit.setSource("LCMS");
        unitRepository.save(unit);

        SchoolAcademicYear year = newYear(access, schoolId, request.academicYearStart(), null, null, "CURRENT");
        yearRepository.save(year);

        SchoolOperationalProfile profile = new SchoolOperationalProfile();
        profile.setSchoolId(schoolId);
        profile.setTenantId(access.tenantId());
        profile.setSchoolType(type);
        profile.setAddress(trim(request.address()));
        profile.setDistrict(trim(request.district()));
        profile.setPrincipalName(trim(request.principalName()));
        profile.setContactPhone(trim(request.contactPhone()));
        profile.setContactEmail(trim(request.contactEmail()));
        profile.setOnboardingStatus(unit.getStatus());
        profile.setCurrentAcademicYearId(year.getId());
        profile.setCreatedBy(access.userId());
        profile.setUpdatedBy(access.userId());
        profileRepository.save(profile);

        List<SchoolGrade> grades = createMissingDefaultGrades(access.tenantId(), schoolId, year.getId(), type);
        return response(unit, profile, year, grades);
    }

    @Transactional
    @CacheEvict(cacheNames = {"organizationEducationUnits", "organizationEducationUnitEntities", "lcmsSchoolDirectory", "lcmsSchoolOperations"}, allEntries = true)
    public SchoolResponse update(String schoolId, UpdateSchoolRequest request, UserAccessContext access) {
        assertScope(access, schoolId);
        EducationUnit unit = unit(access, schoolId);
        SchoolOperationalProfile profile = profile(access, schoolId);
        if (profile.getVersion() != request.version()) throw new ConflictException("School profile has been updated by another request.");

        String previousType = profile.getSchoolType();
        String nextType = request.schoolType() == null ? previousType : schoolType(request.schoolType());
        if (!previousType.equals(nextType) && classroomRepository.countByTenantIdAndSchoolIdAndStatusIgnoreCase(access.tenantId(), schoolId, "active") > 0) {
            throw new ConflictException("School type cannot change while active classes exist.");
        }
        if (request.name() != null && !request.name().isBlank()) unit.setName(request.name().trim());
        unit.setType(nextType);
        profile.setSchoolType(nextType);
        if (request.address() != null) profile.setAddress(trim(request.address()));
        if (request.district() != null) profile.setDistrict(trim(request.district()));
        if (request.principalName() != null) profile.setPrincipalName(trim(request.principalName()));
        if (request.contactPhone() != null) profile.setContactPhone(trim(request.contactPhone()));
        if (request.contactEmail() != null) profile.setContactEmail(trim(request.contactEmail()));
        if (request.status() != null) {
            String status = profileStatus(request.status(), null);
            profile.setOnboardingStatus(status);
            unit.setStatus(status);
        }
        profile.setUpdatedBy(access.userId());
        unitRepository.save(unit);
        profileRepository.save(profile);

        SchoolAcademicYear year = currentYear(access, profile);
        List<SchoolGrade> grades = createMissingDefaultGrades(access.tenantId(), schoolId, year.getId(), nextType);
        return response(unit, profile, year, grades);
    }

    @Transactional
    @CacheEvict(cacheNames = {"organizationEducationUnits", "organizationEducationUnitEntities", "lcmsSchoolDirectory", "lcmsSchoolOperations"}, allEntries = true)
    public CommandResult archive(String schoolId, UserAccessContext access) {
        assertScope(access, schoolId);
        EducationUnit unit = unit(access, schoolId);
        SchoolOperationalProfile profile = profile(access, schoolId);
        if (classroomRepository.countByTenantIdAndSchoolIdAndStatusIgnoreCase(access.tenantId(), schoolId, "active") > 0) {
            throw new ConflictException("Archive active classes before archiving the school.");
        }
        unit.setStatus("ARCHIVED");
        profile.setOnboardingStatus("ARCHIVED");
        profile.setUpdatedBy(access.userId());
        unitRepository.save(unit);
        profileRepository.save(profile);
        return new CommandResult(schoolId, "ARCHIVED", profile.getVersion());
    }

    @Transactional
    @CacheEvict(cacheNames = {"organizationEducationUnits", "organizationEducationUnitEntities", "lcmsSchoolDirectory", "lcmsSchoolOperations"}, allEntries = true)
    public CommandResult restore(String schoolId, UserAccessContext access) {
        assertScope(access, schoolId);
        EducationUnit unit = unit(access, schoolId);
        SchoolOperationalProfile profile = profile(access, schoolId);
        unit.setStatus("PAUSED");
        profile.setOnboardingStatus("PAUSED");
        profile.setUpdatedBy(access.userId());
        unitRepository.save(unit);
        profileRepository.save(profile);
        return new CommandResult(schoolId, "PAUSED", profile.getVersion());
    }

    @Transactional
    public AcademicYearResponse createAcademicYear(String schoolId, CreateAcademicYearRequest request, UserAccessContext access) {
        assertScope(access, schoolId);
        unit(access, schoolId);
        if (request.endsOn() != null && request.startsOn() != null && request.endsOn().isBefore(request.startsOn())) {
            throw new BadRequestException("Academic year end date must not be before its start date.");
        }
        if (yearRepository.existsByTenantIdAndSchoolIdAndStartYear(access.tenantId(), schoolId, request.startYear())) {
            throw new ConflictException("Academic year already exists.");
        }
        String status = Boolean.TRUE.equals(request.activate()) ? "CURRENT" : "PLANNED";
        if ("CURRENT".equals(status)) closeCurrent(access, schoolId);
        SchoolAcademicYear year = newYear(access, schoolId, request.startYear(), request.startsOn(), request.endsOn(), status);
        yearRepository.save(year);
        if ("CURRENT".equals(status)) setCurrentYear(access, schoolId, year);
        createMissingDefaultGrades(access.tenantId(), schoolId, year.getId(), profile(access, schoolId).getSchoolType());
        return yearResponse(year);
    }

    @Transactional
    public AcademicYearResponse activateAcademicYear(String schoolId, String yearId, VersionRequest request, UserAccessContext access) {
        assertScope(access, schoolId);
        SchoolAcademicYear year = year(access, schoolId, yearId);
        if (year.getVersion() != request.version()) throw new ConflictException("Academic year has been updated by another request.");
        closeCurrent(access, schoolId);
        year.setStatus("CURRENT");
        year.setUpdatedBy(access.userId());
        yearRepository.save(year);
        setCurrentYear(access, schoolId, year);
        return yearResponse(year);
    }

    @Transactional
    public AcademicYearResponse closeAcademicYear(String schoolId, String yearId, VersionRequest request, UserAccessContext access) {
        assertScope(access, schoolId);
        SchoolAcademicYear year = year(access, schoolId, yearId);
        if (year.getVersion() != request.version()) throw new ConflictException("Academic year has been updated by another request.");
        year.setStatus("CLOSED");
        year.setUpdatedBy(access.userId());
        yearRepository.save(year);
        SchoolOperationalProfile profile = profile(access, schoolId);
        if (yearId.equals(profile.getCurrentAcademicYearId())) {
            profile.setCurrentAcademicYearId(null);
            profile.setUpdatedBy(access.userId());
            profileRepository.save(profile);
        }
        return yearResponse(year);
    }

    @Transactional
    public GradeSyncResponse syncDefaultGrades(String schoolId, UserAccessContext access) {
        assertScope(access, schoolId);
        SchoolOperationalProfile profile = profile(access, schoolId);
        SchoolAcademicYear year = currentYear(access, profile);
        int before = gradeRepository.findByTenantIdAndSchoolIdAndAcademicYearIdOrderBySortOrderAsc(access.tenantId(), schoolId, year.getId()).size();
        List<SchoolGrade> grades = createMissingDefaultGrades(access.tenantId(), schoolId, year.getId(), profile.getSchoolType());
        int created = Math.max(0, grades.size() - before);
        return new GradeSyncResponse(year.getId(), created, before, grades.stream().map(this::gradeResponse).toList());
    }

    @Transactional
    public GradeResponse createGrade(String schoolId, CreateGradeRequest request, UserAccessContext access) {
        assertScope(access, schoolId);
        SchoolOperationalProfile profile = profile(access, schoolId);
        SchoolAcademicYear year = currentYear(access, profile);
        String code = request.gradeCode().trim().toUpperCase(Locale.ROOT);
        if (gradeRepository.findByTenantIdAndSchoolIdAndAcademicYearIdAndGradeCode(access.tenantId(), schoolId, year.getId(), code).isPresent()) {
            throw new ConflictException("Grade code already exists in the current academic year.");
        }
        SchoolGrade grade = grade(access.tenantId(), schoolId, year.getId(), code, request.label().trim(), request.sortOrder(), "MANUAL");
        grade.setStatus(gradeStatus(request.status()));
        return gradeResponse(gradeRepository.save(grade));
    }

    @Transactional
    public GradeResponse updateGrade(String schoolId, String gradeId, UpdateGradeRequest request, UserAccessContext access) {
        assertScope(access, schoolId);
        SchoolOperationalProfile profile = profile(access, schoolId);
        SchoolGrade grade = gradeRepository.findById(gradeId)
                .filter(value -> value.getTenantId().equals(access.tenantId()) && value.getSchoolId().equals(schoolId))
                .orElseThrow(() -> new ResourceNotFoundException("School grade not found."));
        if (request.label() != null && !request.label().isBlank()) grade.setLabel(request.label().trim());
        if (request.sortOrder() != null) grade.setSortOrder(request.sortOrder());
        if (request.status() != null) grade.setStatus(gradeStatus(request.status()));
        profile.setUpdatedBy(access.userId());
        return gradeResponse(gradeRepository.save(grade));
    }

    private List<SchoolGrade> createMissingDefaultGrades(String tenantId, String schoolId, String yearId, String type) {
        List<Integer> defaults = switch (type) {
            case "PRIMARY" -> List.of(1, 2, 3, 4, 5);
            case "SECONDARY" -> List.of(6, 7, 8, 9);
            case "HIGH_SCHOOL" -> List.of(10, 11, 12);
            default -> List.of();
        };
        List<SchoolGrade> existing = new ArrayList<>(gradeRepository
                .findByTenantIdAndSchoolIdAndAcademicYearIdOrderBySortOrderAsc(tenantId, schoolId, yearId));
        Set<String> codes = existing.stream().map(SchoolGrade::getGradeCode).collect(java.util.stream.Collectors.toSet());
        for (Integer number : defaults) {
            String code = String.valueOf(number);
            if (!codes.contains(code)) existing.add(gradeRepository.save(grade(tenantId, schoolId, yearId, code, "Khối " + number, number, "DEFAULT")));
        }
        return existing.stream().sorted(java.util.Comparator.comparingInt(SchoolGrade::getSortOrder)).toList();
    }

    private SchoolGrade grade(String tenantId, String schoolId, String yearId, String code, String label, int order, String source) {
        SchoolGrade grade = new SchoolGrade();
        grade.setTenantId(tenantId); grade.setSchoolId(schoolId); grade.setAcademicYearId(yearId);
        grade.setGradeCode(code); grade.setLabel(label); grade.setSortOrder(order);
        grade.setStatus("ACTIVE"); grade.setSource(source);
        return grade;
    }

    private SchoolAcademicYear newYear(UserAccessContext access, String schoolId, int startYear,
                                       java.time.LocalDate startsOn, java.time.LocalDate endsOn, String status) {
        SchoolAcademicYear year = new SchoolAcademicYear();
        year.setId(IdGenerator.newId()); year.setTenantId(access.tenantId()); year.setSchoolId(schoolId);
        year.setStartYear(startYear); year.setEndYear(startYear + 1); year.setStartsOn(startsOn); year.setEndsOn(endsOn);
        year.setStatus(status); year.setCreatedBy(access.userId()); year.setUpdatedBy(access.userId());
        return year;
    }

    private void closeCurrent(UserAccessContext access, String schoolId) {
        yearRepository.findFirstByTenantIdAndSchoolIdAndStatus(access.tenantId(), schoolId, "CURRENT").ifPresent(current -> {
            current.setStatus("CLOSED"); current.setUpdatedBy(access.userId()); yearRepository.save(current);
        });
    }

    private void setCurrentYear(UserAccessContext access, String schoolId, SchoolAcademicYear year) {
        SchoolOperationalProfile profile = profile(access, schoolId);
        profile.setCurrentAcademicYearId(year.getId()); profile.setUpdatedBy(access.userId()); profileRepository.save(profile);
    }

    private SchoolAcademicYear currentYear(UserAccessContext access, SchoolOperationalProfile profile) {
        if (profile.getCurrentAcademicYearId() == null) throw new ConflictException("School has no current academic year.");
        return year(access, profile.getSchoolId(), profile.getCurrentAcademicYearId());
    }

    private SchoolAcademicYear year(UserAccessContext access, String schoolId, String yearId) {
        return yearRepository.findByIdAndTenantIdAndSchoolId(yearId, access.tenantId(), schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Academic year not found."));
    }

    private EducationUnit unit(UserAccessContext access, String schoolId) {
        return unitRepository.findByIdAndTenantId(schoolId, access.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("School not found."));
    }

    private SchoolOperationalProfile profile(UserAccessContext access, String schoolId) {
        return profileRepository.findBySchoolIdAndTenantId(schoolId, access.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("School operational profile not found."));
    }

    private void assertScope(UserAccessContext access, String schoolId) {
        if (!access.canAccessEducationUnit(schoolId)) throw new ForbiddenException("School scope is not allowed for the authenticated principal.");
    }

    private SchoolResponse response(EducationUnit unit, SchoolOperationalProfile profile, SchoolAcademicYear year, List<SchoolGrade> grades) {
        return new SchoolResponse(unit.getId(), unit.getName(), unit.getSlug(), profile.getSchoolType(), profile.getOnboardingStatus(),
                profile.getAddress(), profile.getDistrict(), profile.getPrincipalName(), profile.getContactPhone(), profile.getContactEmail(),
                profile.getVersion(), yearResponse(year), grades.stream().map(this::gradeResponse).toList());
    }

    private AcademicYearResponse yearResponse(SchoolAcademicYear year) {
        return new AcademicYearResponse(year.getId(), year.getStartYear(), year.getEndYear(),
                year.getStartYear() + "–" + year.getEndYear(), year.getStatus(), year.getStartsOn(), year.getEndsOn(), year.getVersion());
    }

    private GradeResponse gradeResponse(SchoolGrade grade) {
        return new GradeResponse(grade.getId(), grade.getAcademicYearId(), grade.getGradeCode(), grade.getLabel(),
                grade.getSortOrder(), grade.getStatus(), grade.getSource());
    }

    private String uniqueSlug(String tenantId, String name, String id) {
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String base = NON_SLUG.matcher(ascii.toLowerCase(Locale.ROOT)).replaceAll("-").replaceAll("(^-|-$)", "");
        if (base.isBlank()) base = "school";
        return unitRepository.existsByTenantIdAndSlugIgnoreCase(tenantId, base) ? base + "-" + id.substring(Math.max(0, id.length() - 6)) : base;
    }

    private String schoolType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!SCHOOL_TYPES.contains(normalized)) throw new BadRequestException("Unsupported school type.");
        return normalized;
    }

    private String profileStatus(String value, String fallback) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
        if (normalized == null || !PROFILE_STATUSES.contains(normalized)) throw new BadRequestException("Unsupported school status.");
        return normalized;
    }

    private String gradeStatus(String value) {
        String normalized = value == null || value.isBlank() ? "ACTIVE" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "INACTIVE", "ARCHIVED").contains(normalized)) throw new BadRequestException("Unsupported grade status.");
        return normalized;
    }

    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
