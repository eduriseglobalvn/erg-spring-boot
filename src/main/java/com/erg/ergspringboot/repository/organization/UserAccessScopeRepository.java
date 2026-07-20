package com.erg.ergspringboot.repository.organization;

import com.erg.ergspringboot.model.organization.UserAccessScope;
import com.erg.ergspringboot.repository.organization.projection.ScopeOptionProjection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface UserAccessScopeRepository extends JpaRepository<UserAccessScope, String> {

    List<UserAccessScope> findByUserIdOrderByCreatedAtAsc(String userId);

    @Query("""
            select distinct new com.erg.ergspringboot.repository.organization.projection.ScopeOptionProjection(
                e.id,
                e.name,
                e.type
            )
            from UserAccessScope s
            join s.user u
            join s.educationUnit e
            where u.tenantId = :tenantId
            order by lower(e.name) asc, e.id asc
            """)
    List<ScopeOptionProjection> findAccessManagementScopeOptions(@Param("tenantId") String tenantId);

    @Query("""
            select distinct new com.erg.ergspringboot.repository.organization.projection.ScopeOptionProjection(
                e.id, e.name, e.type
            )
            from UserAccessScope s
            join s.user u
            join s.educationUnit e
            where u.tenantId = :tenantId
            order by lower(e.name) asc, e.id asc
            """)
    List<ScopeOptionProjection> findAccessManagementScopeOptions(
            @Param("tenantId") String tenantId, Pageable pageable);
}
