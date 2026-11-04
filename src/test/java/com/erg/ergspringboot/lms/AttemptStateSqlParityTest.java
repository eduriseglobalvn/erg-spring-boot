package com.erg.ergspringboot.lms;

import static org.assertj.core.api.Assertions.assertThat;

import com.erg.ergspringboot.lcms.persistence.quiz.runtime.StudentProgressRepository;
import com.erg.ergspringboot.lms.persistence.AttemptRepository;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

/** P2-4: status literals in aggregate JPQL must stay the vocabulary owned by {@link LmsStatePolicy}. */
class AttemptStateSqlParityTest {
    private static final Pattern NEEDS_REVIEW = Pattern.compile("status\\) in \\(([^)]*)\\) then 1 else 0 end");
    private static final Pattern EQUALS = Pattern.compile("status\\) = '([a-z_]+)' then");

    @Test
    void aggregateQueriesUseThePolicyStates() {
        List<String> queries = Stream.of(AttemptRepository.class, StudentProgressRepository.class)
                .flatMap(type -> Arrays.stream(type.getMethods()))
                .filter(method -> method.getName().equals("summarizeAssignment")
                        || method.getName().equals("summarizeAssignmentProgress"))
                .map(method -> method.getAnnotation(Query.class))
                .filter(query -> query != null)
                .map(Query::value)
                .toList();
        assertThat(queries).hasSizeGreaterThanOrEqualTo(3);
        List<String> reviewStates = Stream.of("submitted", "needs_review", "review_required", "graded", "completed", "in_progress")
                .filter(LmsStatePolicy::attemptNeedsReview).sorted().toList();
        for (String query : queries) {
            var review = NEEDS_REVIEW.matcher(query);
            assertThat(review.find()).as(query).isTrue();
            assertThat(Arrays.stream(review.group(1).split(",")).map(s -> s.trim().replace("'", "")).sorted().toList())
                    .isEqualTo(reviewStates);
            var equals = EQUALS.matcher(query);
            List<String> singles = Stream.iterate(equals.find(), found -> found, found -> equals.find())
                    .map(found -> equals.group(1)).toList();
            assertThat(singles).containsExactly(
                    LmsStatePolicy.submittedAttemptState(), LmsStatePolicy.initialAttemptState());
        }
    }

    @Test
    void quizReportStatusConstantsUseThePolicyStates() throws Exception {
        assertThat(constant("STATUS_SUBMITTED")).isEqualTo(LmsStatePolicy.submittedAttemptState());
        assertThat(constant("STATUS_IN_PROGRESS")).isEqualTo(LmsStatePolicy.initialAttemptState());
        assertThat(LmsStatePolicy.attemptNeedsReview(constant("STATUS_NEEDS_REVIEW"))).isTrue();
    }

    private static String constant(String name) throws Exception {
        var field = com.erg.ergspringboot.reporting.quiz.QuizReportService.class.getDeclaredField(name);
        field.setAccessible(true);
        return (String) field.get(null);
    }
}
