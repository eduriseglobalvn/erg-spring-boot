package com.erg.ergspringboot.repository.quiz.media;

import com.erg.ergspringboot.model.quiz.media.MediaReference;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaReferenceRepository extends JpaRepository<MediaReference, String> {

    Optional<MediaReference> findByMediaIdAndOwnerTypeAndOwnerId(String mediaId, String ownerType, String ownerId);
}
