package com.erg.ergspringboot.service.lms;

import com.erg.ergspringboot.model.lms.Quiz;
import com.erg.ergspringboot.repository.lms.QuizRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(value = "postgresTransactionManager", readOnly = true)
public class LmsQuizService {

    private final QuizRepository repository;

    public LmsQuizService(QuizRepository repository) {
        this.repository = repository;
    }

    public Optional<Quiz> findById(String id) {
        return repository.findById(id);
    }
}
