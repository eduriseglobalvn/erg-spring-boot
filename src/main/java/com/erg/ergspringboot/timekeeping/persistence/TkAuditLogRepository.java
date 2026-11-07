package com.erg.ergspringboot.timekeeping.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TkAuditLogRepository extends JpaRepository<TkAuditLog, String> {
}
