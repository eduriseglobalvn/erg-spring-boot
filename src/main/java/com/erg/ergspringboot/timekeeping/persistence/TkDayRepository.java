package com.erg.ergspringboot.timekeeping.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TkDayRepository extends JpaRepository<TkDay, String> {

    Optional<TkDay> findByTenantIdAndUserIdAndWorkDate(String tenantId, String userId, LocalDate workDate);

    List<TkDay> findByTenantIdAndUserIdAndWorkDateBetweenOrderByWorkDateAsc(
            String tenantId, String userId, LocalDate from, LocalDate to);
}
