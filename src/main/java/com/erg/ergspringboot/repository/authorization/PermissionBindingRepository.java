package com.erg.ergspringboot.repository.authorization;

import com.erg.ergspringboot.model.authorization.PermissionBinding;
import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionBindingRepository extends JpaRepository<PermissionBinding, String> {

    List<PermissionBinding> findByPermissionIdOrderBySurfaceTypeAscSurfaceIdentifierAsc(String permissionId);

    List<PermissionBinding> findByPermissionIdAndEnforcedTrueOrderBySurfaceTypeAscSurfaceIdentifierAsc(String permissionId);

    List<PermissionBinding> findByPermissionIdInOrderByPermissionIdAscSurfaceTypeAscSurfaceIdentifierAsc(
            Collection<String> permissionIds);
}
