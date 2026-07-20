package com.erg.ergspringboot.service.school;

import com.erg.ergspringboot.common.request.school.SchoolRequests.CreateSchoolRequest;
import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.model.lms.ContentScope;
import com.erg.ergspringboot.model.lms.Student;
import com.erg.ergspringboot.model.lms.StudentMetrics;
import com.erg.ergspringboot.model.lms.Subject;
import com.erg.ergspringboot.model.school.SchoolOperationalProfile;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.repository.auth.user.UserRepository;
import com.erg.ergspringboot.repository.lms.ClassroomRepository;
import com.erg.ergspringboot.repository.lms.StudentRepository;
import com.erg.ergspringboot.repository.lms.SubjectRepository;
import com.erg.ergspringboot.repository.school.SchoolOperationalProfileRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SchoolOperationsDemoSeedService {
    private static final Logger log = LoggerFactory.getLogger(SchoolOperationsDemoSeedService.class);
    private static final int ACADEMIC_YEAR_START = 2026;

    private final SchoolOperationalProfileRepository profileRepository;
    private final SchoolCommandService commandService;
    private final UserRepository userRepository;
    private final ClassroomRepository classroomRepository;
    private final StudentRepository studentRepository;
    private final SubjectRepository subjectRepository;

    public SchoolOperationsDemoSeedService(
            SchoolOperationalProfileRepository profileRepository,
            SchoolCommandService commandService,
            UserRepository userRepository,
            ClassroomRepository classroomRepository,
            StudentRepository studentRepository,
            SubjectRepository subjectRepository
    ) {
        this.profileRepository = profileRepository;
        this.commandService = commandService;
        this.userRepository = userRepository;
        this.classroomRepository = classroomRepository;
        this.studentRepository = studentRepository;
        this.subjectRepository = subjectRepository;
    }

    public int seedIfEmpty(String tenantId) {
        int createdSchools = createSchoolsWhenEmpty(tenantId);
        List<SchoolOperationalProfile> profiles = profileRepository.findByTenantIdOrderBySchoolIdAsc(tenantId);
        for (int index = 0; index < profiles.size(); index++) {
            seedSchoolDataset(profiles.get(index), index);
        }
        log.info("School operations demo seed ready: {} new schools, {} enriched schools for tenant {}",
                createdSchools, profiles.size(), tenantId);
        return createdSchools;
    }

    private int createSchoolsWhenEmpty(String tenantId) {
        if (profileRepository.countByTenantId(tenantId) > 0) return 0;
        String actorUserId = userRepository.findFirstByTenantIdOrderByCreatedAtAsc(tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "School operations demo seed requires an existing user in tenant " + tenantId))
                .getId();
        UserAccessContext access = new UserAccessContext(
                actorUserId, tenantId, List.of("*"), List.of("*"), null, null);
        List<CreateSchoolRequest> schools = List.of(
                school("Tiểu học Nguyễn Bỉnh Khiêm", "PRIMARY", "Quận 1"),
                school("THCS Bình An", "SECONDARY", "Thành phố Thủ Đức"),
                school("THPT Lê Quý Đôn", "HIGH_SCHOOL", "Quận 3"),
                school("Đại học Công nghệ ERG", "UNIVERSITY", "Thành phố Thủ Đức"),
                school("Trung tâm Đào tạo ERG", "TRAINING_CENTER", "Quận Bình Thạnh")
        );
        schools.forEach(request -> commandService.create(request, access));
        return schools.size();
    }

    private void seedSchoolDataset(SchoolOperationalProfile profile, int schoolIndex) {
        enrichProfile(profile, schoolIndex);
        List<String> grades = gradesFor(profile.getSchoolType());
        if (grades.isEmpty()) grades = List.of("A", "B");
        for (String grade : grades) {
            for (int section = 1; section <= 2; section++) {
                Classroom classroom = seedClassroom(profile, grade, section, schoolIndex);
                seedStudents(profile, classroom, schoolIndex);
            }
        }
        seedSubjects(profile);
    }

    private void enrichProfile(SchoolOperationalProfile profile, int index) {
        if (blank(profile.getAddress())) profile.setAddress((25 + index * 18) + " Nguyễn Huệ, TP. Hồ Chí Minh");
        if (blank(profile.getPrincipalName())) profile.setPrincipalName(TEACHERS[index % TEACHERS.length]);
        if (blank(profile.getContactPhone())) profile.setContactPhone("0287300" + String.format("%04d", 1200 + index));
        if (blank(profile.getContactEmail())) profile.setContactEmail("school" + (index + 1) + "@demo.erg.edu.vn");
        profileRepository.save(profile);
    }

    private Classroom seedClassroom(SchoolOperationalProfile profile, String grade, int section, int schoolIndex) {
        String id = seedId("cls", profile.getSchoolId(), grade + section);
        Classroom existing = classroomRepository.findById(id).orElse(null);
        if (existing != null) return existing;

        Classroom classroom = new Classroom();
        classroom.setId(id);
        classroom.setTenantId(profile.getTenantId());
        classroom.setSchoolId(profile.getSchoolId());
        classroom.setName(className(profile.getSchoolType(), grade, section));
        classroom.setGrade(List.of("UNIVERSITY", "TRAINING_CENTER").contains(profile.getSchoolType()) ? "" : grade);
        classroom.setAcademicYear("2026-2027");
        classroom.setStatus("ACTIVE");
        classroom.setHomeroomTeacherId(null);
        return classroomRepository.save(classroom);
    }

    private void seedStudents(SchoolOperationalProfile profile, Classroom classroom, int schoolIndex) {
        List<String> expectedIds = new ArrayList<>(24);
        for (int studentNumber = 1; studentNumber <= 24; studentNumber++) {
            expectedIds.add(seedId("stu", classroom.getId(), String.valueOf(studentNumber)));
        }
        Set<String> existingIds = new HashSet<>();
        studentRepository.findAllById(expectedIds).forEach(student -> existingIds.add(student.getId()));

        List<Student> missingStudents = new ArrayList<>();
        for (int studentNumber = 1; studentNumber <= 24; studentNumber++) {
            String id = expectedIds.get(studentNumber - 1);
            if (!existingIds.contains(id)) {
                missingStudents.add(buildStudent(profile, classroom, studentNumber, schoolIndex, id));
            }
        }
        if (!missingStudents.isEmpty()) studentRepository.saveAll(missingStudents);
    }

    private Student buildStudent(
            SchoolOperationalProfile profile,
            Classroom classroom,
            int studentNumber,
            int schoolIndex,
            String id
    ) {
        Student student = new Student();
        student.setId(id);
        student.setTenantId(profile.getTenantId());
        student.setSchoolId(profile.getSchoolId());
        student.setAcademicClassId(classroom.getId());
        student.setStudentCode("ERG" + Integer.toUnsignedString(profile.getSchoolId().hashCode(), 36).toUpperCase() + String.format("%03d", studentNumber));
        student.setFullName(STUDENT_FAMILIES[(studentNumber + schoolIndex) % STUDENT_FAMILIES.length] + " " + STUDENT_GIVEN_NAMES[(studentNumber * 3 + schoolIndex) % STUDENT_GIVEN_NAMES.length]);
        student.setUsername(id);
        student.setEmail(id + "@student.demo.erg.edu.vn");
        student.setGender(studentNumber % 2 == 0 ? "FEMALE" : "MALE");
        student.setBirthday(OffsetDateTime.of(2012 - schoolIndex, (studentNumber % 12) + 1, (studentNumber % 27) + 1, 0, 0, 0, 0, ZoneOffset.UTC));
        student.setParentName("Phụ huynh " + student.getFullName());
        student.setParentPhone("09" + String.format("%08d", Math.abs((profile.getSchoolId() + studentNumber).hashCode()) % 100_000_000));
        student.setEnrollmentDate(OffsetDateTime.now().minusMonths(6 + studentNumber % 9));
        student.setStatus(studentNumber % 17 == 0 ? "AT_RISK" : "ACTIVE");
        student.setMetrics(new StudentMetrics(6.2 + (studentNumber % 28) / 10.0, 8 + studentNumber % 16, OffsetDateTime.now().minusDays(studentNumber % 8)));
        return student;
    }

    private void seedSubjects(SchoolOperationalProfile profile) {
        for (int index = 0; index < SUBJECTS.length; index++) {
            String id = seedId("sub", profile.getSchoolId(), String.valueOf(index));
            if (subjectRepository.existsById(id)) continue;

            Subject subject = new Subject();
            subject.setId(id);
            subject.setTenantId(profile.getTenantId());
            subject.setScope(new ContentScope("SCHOOL", profile.getSchoolId()));
            subject.setName(SUBJECTS[index][0]);
            subject.setCode(SUBJECTS[index][1]);
            subject.setStatus("ACTIVE");
            subjectRepository.save(subject);
        }
    }

    private List<String> gradesFor(String schoolType) {
        return switch (schoolType) {
            case "PRIMARY" -> List.of("1", "2", "3", "4", "5");
            case "SECONDARY" -> List.of("6", "7", "8", "9");
            case "HIGH_SCHOOL" -> List.of("10", "11", "12");
            default -> List.of();
        };
    }

    private String className(String schoolType, String grade, int section) {
        if ("UNIVERSITY".equals(schoolType)) return "K26-CN" + section + ("A".equals(grade) ? "A" : "B");
        if ("TRAINING_CENTER".equals(schoolType)) return "ERG-" + grade + section;
        return grade + "A" + section;
    }

    private String seedId(String prefix, String parent, String suffix) {
        return prefix + "_demo_" + Integer.toUnsignedString(parent.hashCode(), 36) + "_" + suffix.toLowerCase();
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    private CreateSchoolRequest school(String name, String type, String district) {
        return new CreateSchoolRequest(name, type, ACADEMIC_YEAR_START, "Thành phố Hồ Chí Minh", district,
                null, null, null, "ACTIVE");
    }

    private static final String[] TEACHERS = { "Nguyễn Minh Anh", "Trần Thu Hà", "Lê Mạnh Hùng", "Vũ Thanh Mai", "Phạm Quốc Bảo" };
    private static final String[] STUDENT_FAMILIES = { "Nguyễn", "Trần", "Lê", "Phạm", "Võ", "Đặng", "Bùi", "Đỗ" };
    private static final String[] STUDENT_GIVEN_NAMES = { "Gia Hân", "Minh Khang", "Bảo Ngọc", "Đức Anh", "Khánh Linh", "Tuấn Kiệt", "Hoàng Nam", "Thảo Vy", "Quang Huy", "Ngọc Anh" };
    private static final String[][] SUBJECTS = { { "Tiếng Anh", "ENG" }, { "Toán học", "MATH" }, { "Khoa học", "SCI" }, { "Tin học", "ICT" } };
}
