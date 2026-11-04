package com.erg.ergspringboot.lms.internal.core;

import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lms.persistence.AssignmentRepository;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(value = "postgresTransactionManager", readOnly = true)
public class LmsAssignmentService {

    private final AssignmentRepository repository;

    public LmsAssignmentService(AssignmentRepository repository) {
        this.repository = repository;
    }

    public Optional<Assignment> findByTenantIdAndId(String tenantId, String id) {
        return repository.findByTenantIdAndId(tenantId, id);
    }

    public List<Assignment> findByTenantIdAndIdIn(String tenantId, Collection<String> ids) {
        return ids == null || ids.isEmpty() ? List.of() : repository.findByTenantIdAndIdIn(tenantId, ids);
    }

    public List<Assignment> findByTenantIdAndQuizId(String tenantId, String quizId) {
        return repository.findByTenantIdAndQuizId(tenantId, quizId);
    }

    public Page<Assignment> findStudentAssignments(
            String tenantId, String studentId, String academicClassId, Pageable pageable
    ) {
        return repository.findStudentAssignments(tenantId, studentId, academicClassId, pageable);
    }
}
