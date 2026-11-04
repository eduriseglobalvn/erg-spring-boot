package com.erg.ergspringboot.curriculum;

import java.util.List;

/**
 * Lets modules that reference curriculum nodes report their usage, so curriculum's impact checks
 * do not read other modules' persistence (that would be a module cycle).
 */
public interface CurriculumUsageCounter {

    enum Node { SUBJECT, LEVEL, TOPIC }

    /** @param topicIds the topics under the node; for a TOPIC node just its own id */
    Usage count(String tenantId, Node node, String id, List<String> topicIds);

    record Usage(long questions, long quizzes, long publishedQuizVersions,
                 long contentItems, long assignments, long studentAttempts) {
        public static final Usage NONE = new Usage(0, 0, 0, 0, 0, 0);

        public Usage plus(Usage other) {
            return new Usage(questions + other.questions, quizzes + other.quizzes,
                    publishedQuizVersions + other.publishedQuizVersions, contentItems + other.contentItems,
                    assignments + other.assignments, studentAttempts + other.studentAttempts);
        }
    }
}
