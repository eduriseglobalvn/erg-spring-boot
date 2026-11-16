package com.erg.ergspringboot.audit.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.erg.ergspringboot.audit.AuditOutcome;
import com.erg.ergspringboot.audit.AuditRecord;
import com.erg.ergspringboot.exception.ForbiddenException;
import graphql.GraphQL;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GraphQlMutationAuditInstrumentationTest {
    private final AuditOutboxWriter writer = mock(AuditOutboxWriter.class);
    private GraphQL graphQl;

    @BeforeEach
    void setUp() {
        String sdl = """
                type Query { ping: String }
                type Mutation {
                  renameClass(name: String): String
                  deleteClass: String
                  markStudentAnnouncementRead: String
                }
                """;
        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", type -> type.dataFetcher("ping", env -> "pong"))
                .type("Mutation", type -> type
                        .dataFetcher("renameClass", env -> "ok")
                        .dataFetcher("deleteClass", env -> { throw new ForbiddenException("no"); })
                        .dataFetcher("markStudentAnnouncementRead", env -> "ok"))
                .build();
        graphQl = GraphQL.newGraphQL(new SchemaGenerator().makeExecutableSchema(new SchemaParser().parse(sdl), wiring))
                .instrumentation(new GraphQlMutationAuditInstrumentation(writer))
                .build();
        CaptureTestSupport.signIn();
    }

    @AfterEach
    void tearDown() {
        CaptureTestSupport.signOut();
    }

    @Test
    void graphqlMutationIsAudited() {
        graphQl.execute("mutation { renameClass(name: \"1A\") }");

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(writer).write(captor.capture());
        AuditRecord record = captor.getValue();
        assertThat(record.action()).isEqualTo("renameClass");
        assertThat(record.resourceType()).isEqualTo("GRAPHQL");
        assertThat(record.outcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(record.actorId()).isEqualTo("u-1");
        assertThat(record.details()).containsKey("arguments");
    }

    @Test
    void graphqlQueryIsNotAudited() {
        graphQl.execute("query { ping }");

        verify(writer, never()).write(any());
    }

    @Test
    void graphqlDeniedMutationIsRecordedAsDenied() {
        graphQl.execute("mutation { deleteClass }");

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(writer).write(captor.capture());
        assertThat(captor.getValue().outcome()).isEqualTo(AuditOutcome.DENIED);
    }

    @Test
    void selfServiceMutationIsSkippedAndAnonymousIsNotAudited() {
        graphQl.execute("mutation { markStudentAnnouncementRead }");
        CaptureTestSupport.signOut();
        graphQl.execute("mutation { renameClass(name: \"x\") }");

        verify(writer, never()).write(any());
    }
}
