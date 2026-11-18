package com.erg.ergspringboot.lms.internal.classroom;

import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.lms.persistence.projection.ClassOptionProjection;
import com.erg.ergspringboot.platform.security.TeacherClassScopeProvider;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
class LmsTeacherClassScopeProvider implements TeacherClassScopeProvider {
    private final ClassroomRepository classrooms;

    LmsTeacherClassScopeProvider(ClassroomRepository classrooms) {
        this.classrooms = classrooms;
    }

    @Override
    public List<String> classIds(String tenantId, String userId) {
        return classrooms.findTeacherClassOptions(tenantId, userId).stream().map(ClassOptionProjection::id).toList();
    }
}
