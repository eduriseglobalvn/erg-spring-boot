package com.erg.ergspringboot.lcms.persistence.quiz.runtime;

import com.erg.ergspringboot.lcms.persistence.quiz.runtime.AttemptEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttemptEventRepository extends JpaRepository<AttemptEvent, String> {

    List<AttemptEvent> findByAttemptIdOrderByCreatedAtAsc(String attemptId);

    boolean existsById(String id);

    /** Which of these client event ids are already stored: one query for a whole replayed batch. */
    @Query("select event.id from AttemptEvent event where event.id in :ids")
    List<String> findExistingIds(@Param("ids") java.util.Collection<String> ids);
}
