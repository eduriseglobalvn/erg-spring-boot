package com.erg.ergspringboot.controller.platform.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GraphQlShellContractTest {

    @Test
    void pomAddsSpringGraphQlStarter() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));

        assertThat(pom).contains("<artifactId>spring-boot-starter-graphql</artifactId>");
    }

    @Test
    void applicationYamlConfiguresSingleGraphQlEndpointAndProductionGuards() throws IOException {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(yaml)
                .contains("http:")
                .contains("path: ${SPRING_GRAPHQL_PATH:/api/v1/graphql}")
                .contains("enabled: ${SPRING_GRAPHQL_GRAPHIQL_ENABLED:false}")
                .contains("enabled: ${SPRING_GRAPHQL_SCHEMA_INTROSPECTION_ENABLED:${ERG_GRAPHQL_INTROSPECTION_ENABLED:false}}")
                .contains("require-operation-name: ${ERG_GRAPHQL_REQUIRE_OPERATION_NAME:true}")
                .contains("introspection-enabled: ${ERG_GRAPHQL_INTROSPECTION_ENABLED:false}")
                .contains("max-query-length: ${ERG_GRAPHQL_MAX_QUERY_LENGTH:20000}")
                .contains("max-depth: ${ERG_GRAPHQL_MAX_DEPTH:8}")
                .contains("max-complexity: ${ERG_GRAPHQL_MAX_COMPLEXITY:500}")
                .contains("execution-timeout: ${ERG_GRAPHQL_EXECUTION_TIMEOUT:5s}")
                .contains("max-aliases: ${ERG_GRAPHQL_MAX_ALIASES:20}")
                .contains("reject-sensitive-variables: ${ERG_GRAPHQL_REJECT_SENSITIVE_VARIABLES:true}")
                .contains("rate-limit:")
                .contains("enabled: ${ERG_GRAPHQL_RATE_LIMIT_ENABLED:true}")
                .contains("requests-per-minute: ${ERG_GRAPHQL_RATE_LIMIT_REQUESTS_PER_MINUTE:120}")
                .contains("burst: ${ERG_GRAPHQL_RATE_LIMIT_BURST:30}");
    }

    @Test
    void schemaUsesOneEndpointWithPlatformNamespaces() throws IOException {
        String schema = Files.readString(Path.of("src/main/resources/graphql/schema.graphqls"));

        assertThat(schema)
                .contains("type Query")
                .contains("lms: LmsQuery!")
                .contains("lcms: LcmsQuery!")
                .contains("crm: PlatformNamespace!")
                .contains("elearning: ElearningQuery!")
                .contains("type LmsQuery")
                .contains("type LcmsQuery")
                .contains("questionBankWorkspace(input: QuestionBankWorkspaceInput): QuestionBankWorkspace!")
                .contains("quizKind: String")
                .contains("includeQuestions: Boolean")
                .contains("type QuestionBankOption")
                .contains("description: String")
                .contains("scopeLabel: String")
                .contains("categoryIds: [String!]!")
                .contains("type QuestionBankQuestionRow")
                .contains("scopeType: String")
                .contains("scopeId: String")
                .contains("subjectLabel: String")
                .contains("levelLabel: String")
                .contains("topicId: String")
                .contains("categoryLabel: String")
                .contains("gradeLabel: String")
                .contains("objective: String")
                .contains("choices: [QuestionBankChoice!]!")
                .contains("usageCount: Int!")
                .contains("recommendedCluster: String")
                .contains("type QuestionBankQuizOption")
                .contains("kind: String")
                .contains("categoryId: String")
                .contains("questionIds: [String!]!")
                .contains("questionCount: Int!")
                .contains("durationLabel: String")
                .contains("sourceMode: String")
                .contains("ownerLabel: String")
                .contains("type QuizWorkspace")
                .contains("quizzes: QuizSummaryPage!")
                .contains("categories: [QuestionBankOption!]!")
                .contains("type QuizSummary")
                .contains("currentVersionLabel: String")
                .contains("accessManagementWorkspace(input: AccessManagementWorkspaceInput): AccessManagementWorkspace!")
                .contains("learningResourceLibrary(input: LearningResourceLibraryInput): LearningResourceLibrary!")
                .contains("type LearningResourceLibrary")
                .contains("type LearningResourceCard")
                .contains("type LearningResourceCardPage")
                .contains("type LearningResourceProgress")
                .contains("type LearningRecentResource")
                .contains("type ElearningQuery")
                .contains("studentDashboard(input: StudentDashboardInput): StudentDashboard!")
                .contains("type StudentDashboard")
                .contains("type StudentDashboardNextAction");
    }
}
