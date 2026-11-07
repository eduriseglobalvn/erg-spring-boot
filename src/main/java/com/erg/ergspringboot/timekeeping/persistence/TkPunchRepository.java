package com.erg.ergspringboot.timekeeping.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TkPunchRepository extends JpaRepository<TkPunch, String> {
}
