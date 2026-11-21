package com.erg.ergspringboot.lcms.quiz.authoring;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuestionRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuizQuestion;
import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuizQuestionRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class QuizQuestionLinkServiceTest {
    private final QuizQuestionRepository links = mock(QuizQuestionRepository.class);
    private final QuestionRepository questions = mock(QuestionRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final QuizQuestionLinkService service = new QuizQuestionLinkService(links, questions, jdbc);

    @Test
    void anUnchangedOrderedListNeitherDeletesNorReinsertsLinks() {
        when(questions.findIdsByTenantIdAndIdIn("t", List.of("q1", "q2"))).thenReturn(List.of("q1", "q2"));
        when(links.findByTenantIdAndQuizIdOrderByOrderIndexAsc("t", "quiz")).thenReturn(List.of(link("q1", 0), link("q2", 1)));

        List<String> ids = service.syncManualQuestionIds("t", "quiz", List.of("q1", "q2"));

        org.assertj.core.api.Assertions.assertThat(ids).containsExactly("q1", "q2");
        verify(jdbc, never()).update(anyString(), eq("t"), eq("quiz"));
        verify(links, never()).saveAll(any());
    }

    @Test
    void aReorderedListRewritesTheLinks() {
        when(questions.findIdsByTenantIdAndIdIn("t", List.of("q2", "q1"))).thenReturn(List.of("q1", "q2"));
        when(links.findByTenantIdAndQuizIdOrderByOrderIndexAsc("t", "quiz")).thenReturn(List.of(link("q1", 0), link("q2", 1)));

        service.syncManualQuestionIds("t", "quiz", List.of("q2", "q1"));

        verify(jdbc).update(anyString(), eq("t"), eq("quiz"));
        verify(links).saveAll(any());
    }

    private static QuizQuestion link(String questionId, int order) {
        QuizQuestion link = new QuizQuestion();
        link.setQuestionId(questionId);
        link.setOrderIndex(order);
        link.setSource("manual");
        link.setSettings(Map.of());
        link.setMetadata(Map.of());
        return link;
    }
}
