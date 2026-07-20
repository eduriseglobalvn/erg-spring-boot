package com.erg.ergspringboot.service.lms;

import com.erg.ergspringboot.model.lms.Student;
import com.erg.ergspringboot.repository.lms.StudentRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(value = "postgresTransactionManager", readOnly = true)
public class LmsStudentService {

    private final StudentRepository repository;

    public LmsStudentService(StudentRepository repository) {
        this.repository = repository;
    }

    public Optional<Student> findFirstByTenantIdAndAuthUserId(String tenantId, String authUserId) {
        return repository.findFirstByTenantIdAndAuthUserId(tenantId, authUserId);
    }

    public Optional<Student> findByTenantIdAndId(String tenantId, String id) {
        return repository.findByTenantIdAndId(tenantId, id);
    }
}
