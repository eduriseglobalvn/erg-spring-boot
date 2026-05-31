package com.erg.ergspringboot.repository.users;

import com.erg.ergspringboot.model.users.SocialAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, String> {

    List<SocialAccount> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<SocialAccount> findByProviderAndProviderId(String provider, String providerId);
}
