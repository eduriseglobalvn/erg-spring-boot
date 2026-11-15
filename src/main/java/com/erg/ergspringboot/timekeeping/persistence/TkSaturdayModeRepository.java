package com.erg.ergspringboot.timekeeping.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TkSaturdayModeRepository extends JpaRepository<TkSaturdayMode, String> {

    Optional<TkSaturdayMode> findByTenantIdAndWorkDate(String tenantId, LocalDate workDate);

    List<TkSaturdayMode> findByTenantIdAndWorkDateBetween(String tenantId, LocalDate from, LocalDate to);
}
