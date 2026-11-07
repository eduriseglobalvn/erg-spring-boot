package com.erg.ergspringboot.timekeeping.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TkPolicyRepository extends JpaRepository<TkPolicy, String> {
}
