package com.erg.ergspringboot.repository.quiz.runtime;

import com.erg.ergspringboot.model.quiz.runtime.AttemptEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptEventRepository extends JpaRepository<AttemptEvent, String> {

    List<AttemptEvent> findByAttemptIdOrderByCreatedAtAsc(String attemptId);

    boolean existsById(String id);
}
