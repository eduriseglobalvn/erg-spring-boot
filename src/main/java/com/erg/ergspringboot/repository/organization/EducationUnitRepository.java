package com.erg.ergspringboot.repository.organization;

import com.erg.ergspringboot.model.organization.EducationUnit;
import java.util.Collection;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EducationUnitRepository extends JpaRepository<EducationUnit, String> {

    @Cacheable(cacheNames = "organizationEducationUnitEntities", key = "'all'")
    List<EducationUnit> findAllByOrderByNameAsc();

    List<EducationUnit> findByIdInOrderByNameAsc(Collection<String> ids);

    List<EducationUnit> findByParentIdInOrderByNameAsc(Collection<String> parentIds);
}
