package com.erg.ergspringboot.service.lms;

import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.repository.lms.ClassroomRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(value = "postgresTransactionManager", readOnly = true)
public class LmsClassroomService {

    private final ClassroomRepository repository;

    public LmsClassroomService(ClassroomRepository repository) {
        this.repository = repository;
    }

    public Optional<Classroom> findById(String id) {
        return repository.findById(id);
    }

    public List<Classroom> findByTenantIdOrderByNameAsc(String tenantId) {
        return repository.findByTenantIdOrderByNameAsc(tenantId);
    }

    public List<Classroom> findByTenantIdAndSchoolIdOrderByNameAsc(String tenantId, String schoolId) {
        return repository.findByTenantIdAndSchoolIdOrderByNameAsc(tenantId, schoolId);
    }
}
