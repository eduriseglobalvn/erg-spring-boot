package com.erg.ergspringboot.service.lms;

import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.repository.lms.ClassroomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LmsLearningBootstrapSeedService {

    private static final String TENANT_ID = "erg";
    private static final String ACTIVE = "ACTIVE";
    private static final String BINH_AN_SCHOOL_ID = "sch_binh_an";
    private static final String CAT_LAI_SCHOOL_ID = "sch_cat_lai";

    private final ClassroomRepository classroomRepository;

    public LmsLearningBootstrapSeedService(ClassroomRepository classroomRepository) {
        this.classroomRepository = classroomRepository;
    }

    @Transactional("postgresTransactionManager")
    public void seedLearningHierarchy() {
        seedClassroom("cls_ba_6a1_001", BINH_AN_SCHOOL_ID, "6A1", "6");
        seedClassroom("cls_ba_6a2_001", BINH_AN_SCHOOL_ID, "6A2", "6");
        seedClassroom("cls_ba_7a1_001", BINH_AN_SCHOOL_ID, "7A1", "7");
        seedClassroom("cls_ba_7a2_001", BINH_AN_SCHOOL_ID, "7A2", "7");
        seedClassroom("cls_ba_8a1_001", BINH_AN_SCHOOL_ID, "8A1", "8");
        seedClassroom("cls_ba_9a1_001", BINH_AN_SCHOOL_ID, "9A1", "9");

        seedClassroom("cls_cl_6a1_001", CAT_LAI_SCHOOL_ID, "6A1", "6");
        seedClassroom("cls_cl_6a2_001", CAT_LAI_SCHOOL_ID, "6A2", "6");
        seedClassroom("cls_cl_7a1_001", CAT_LAI_SCHOOL_ID, "7A1", "7");
        seedClassroom("cls_cl_7a2_001", CAT_LAI_SCHOOL_ID, "7A2", "7");
        seedClassroom("cls_cl_8a1_001", CAT_LAI_SCHOOL_ID, "8A1", "8");
        seedClassroom("cls_cl_9a1_001", CAT_LAI_SCHOOL_ID, "9A1", "9");
    }

    private void seedClassroom(String id, String schoolId, String name, String grade) {
        Classroom classroom = classroomRepository.findById(id).orElseGet(() -> {
            Classroom created = new Classroom();
            created.setId(id);
            return created;
        });
        classroom.setTenantId(TENANT_ID);
        classroom.setSchoolId(schoolId);
        classroom.setName(name);
        classroom.setGrade(grade);
        classroom.setAcademicYear("2026-2027");
        classroom.setStatus(ACTIVE);
        classroomRepository.save(classroom);
    }
}
