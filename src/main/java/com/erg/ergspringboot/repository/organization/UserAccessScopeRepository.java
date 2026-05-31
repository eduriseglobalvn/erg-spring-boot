package com.erg.ergspringboot.repository.organization;

import com.erg.ergspringboot.model.organization.UserAccessScope;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccessScopeRepository extends JpaRepository<UserAccessScope, String> {

    List<UserAccessScope> findByUserIdOrderByCreatedAtAsc(String userId);
}
