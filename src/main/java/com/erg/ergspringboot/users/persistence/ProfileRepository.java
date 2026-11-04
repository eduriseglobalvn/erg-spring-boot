package com.erg.ergspringboot.users.persistence;

import com.erg.ergspringboot.users.persistence.Profile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<Profile, String> {

    Optional<Profile> findByUserId(String userId);

    void deleteByUserId(String userId);
}
