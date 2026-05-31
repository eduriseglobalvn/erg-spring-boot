package com.erg.ergspringboot.repository.users;

import com.erg.ergspringboot.model.users.Profile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<Profile, String> {

    Optional<Profile> findByUserId(String userId);

    void deleteByUserId(String userId);
}
