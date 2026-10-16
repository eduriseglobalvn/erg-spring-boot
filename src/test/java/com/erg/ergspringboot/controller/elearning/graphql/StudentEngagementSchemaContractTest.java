package com.erg.ergspringboot.controller.elearning.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StudentEngagementSchemaContractTest {

    @Test
    void schemaPublishesStudentDiscussionAndAnnouncementCommands() throws IOException {
        String schema = Files.readString(Path.of("src/main/resources/graphql/schema.graphqls"));

        assertThat(schema)
                .contains("discussionFeed(input: StudentDiscussionFeedInput)")
                .contains("createStudentDiscussion(input: CreateStudentDiscussionInput!)")
                .contains("replyStudentDiscussion(input: ReplyStudentDiscussionInput!)")
                .contains("setStudentDiscussionReaction(input: SetStudentDiscussionReactionInput!)")
                .contains("markStudentAnnouncementRead(input: MarkStudentAnnouncementReadInput!)");
    }
}
