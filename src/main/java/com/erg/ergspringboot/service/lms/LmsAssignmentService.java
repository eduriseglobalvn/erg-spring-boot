package com.erg.ergspringboot.service.lms;

import com.erg.ergspringboot.model.lms.Assignment;
import com.erg.ergspringboot.repository.lms.AssignmentRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(value = "postgresTransactionManager", readOnly = true)
public class LmsAssignmentService {

    private final AssignmentRepository repository;

    public LmsAssignmentService(AssignmentRepository repository) {
        this.repository = repository;
    }

    public Optional<Assignment> findById(String id) {
        return repository.findById(id);
    }

    public List<Assignment> findByTenantIdOrderByCreatedAtDesc(String tenantId) {
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }
}
