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
    private static final Pattern HANDED_IN = Pattern.compile(
            "status\\) in \\(([^)]*)\\) then 1 else 0 end\\), 0\\) as submittedCount");

    @Test
    void aggregateQueriesUseThePolicyStates() {
        List<String> attemptQueries = queries(AttemptRepository.class, "summarizeAssignmentProgress");
        List<String> progressQueries = queries(StudentProgressRepository.class, "summarizeAssignment");
        assertThat(attemptQueries).hasSize(1);
        assertThat(progressQueries).hasSize(2);
        List<String> reviewStates = Stream.of("submitted", "needs_review", "review_required", "graded", "completed", "in_progress")
                .filter(LmsStatePolicy::attemptNeedsReview).sorted().toList();
        for (String query : Stream.concat(attemptQueries.stream(), progressQueries.stream()).toList()) {
            var review = NEEDS_REVIEW.matcher(query);
            boolean found = false;
            while (review.find()) {
                found |= states(review.group(1)).equals(reviewStates);
            }
            assertThat(found).as(query).isTrue();
        }
        for (String query : attemptQueries) {
            var equals = EQUALS.matcher(query);
            List<String> singles = Stream.iterate(equals.find(), f -> f, f -> equals.find()).map(f -> equals.group(1)).toList();
            assertThat(singles).containsExactly(
                    LmsStatePolicy.submittedAttemptState(), LmsStatePolicy.initialAttemptState());
        }
        // DATA-01: the teacher's "submitted" column counts every handed-in state, graded included.
        for (String query : progressQueries) {
            var handedIn = HANDED_IN.matcher(query);
            assertThat(handedIn.find()).as(query).isTrue();
            assertThat(states(handedIn.group(1))).isEqualTo(LmsStatePolicy.completedAttemptStates().stream().sorted().toList());
            var equals = EQUALS.matcher(query);
            assertThat(equals.find()).isTrue();
            assertThat(equals.group(1)).isEqualTo(LmsStatePolicy.initialAttemptState());
        }
    }

    private static List<String> queries(Class<?> type, String name) {
        return Arrays.stream(type.getMethods())
                .filter(method -> method.getName().equals(name))
                .map(method -> method.getAnnotation(Query.class))
                .filter(query -> query != null)
                .map(Query::value)
                .toList();
    }

    private static List<String> states(String csv) {
        return Arrays.stream(csv.split(",")).map(s -> s.trim().replace("'", "")).sorted().toList();
    }
}
