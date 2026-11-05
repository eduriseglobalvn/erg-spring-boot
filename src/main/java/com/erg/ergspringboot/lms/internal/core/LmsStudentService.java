package com.erg.ergspringboot.lms.internal.core;

import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.lms.persistence.StudentRepository;
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

    public Optional<Student> findByTenantIdAndStudentCode(String tenantId, String studentCode) {
        return repository.findByTenantIdAndStudentCodeIgnoreCase(tenantId, studentCode);
    }

    public Optional<Student> findByTenantIdAndId(String tenantId, String id) {
        return repository.findByTenantIdAndId(tenantId, id);
    }
}
