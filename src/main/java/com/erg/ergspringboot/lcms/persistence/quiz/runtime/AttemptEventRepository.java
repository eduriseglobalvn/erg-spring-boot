package com.erg.ergspringboot.lcms.persistence.quiz.runtime;

import com.erg.ergspringboot.lcms.persistence.quiz.runtime.AttemptEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptEventRepository extends JpaRepository<AttemptEvent, String> {

    List<AttemptEvent> findByAttemptIdOrderByCreatedAtAsc(String attemptId);

    boolean existsById(String id);
}
