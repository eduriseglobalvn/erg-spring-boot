package com.erg.ergspringboot.lms.internal.attempt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.ImportPortableResultRequest;
import com.erg.ergspringboot.common.dto.lms.LmsRuntimeDtos.ImportedAttemptResponse;
import com.erg.ergspringboot.exception.BadRequestException;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizPackage;
import com.erg.ergspringboot.lms.LmsAttemptFixture;
import com.erg.ergspringboot.lms.persistence.Assignment;
import com.erg.ergspringboot.lms.persistence.Attempt;
import com.erg.ergspringboot.lms.persistence.Classroom;
import com.erg.ergspringboot.lms.persistence.Student;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PortableResultImportTest {

    private LmsAttemptFixture fixture;
    private LmsPortableResultImportUseCase service;

    @BeforeEach
    void setUp() {
        fixture = new LmsAttemptFixture();
        service = new LmsPortableResultImportUseCase(fixture.policy, fixture.packages, fixture.attempts,
                fixture.useCase(Clock.systemUTC()));

        Student student = new Student();
        student.setId("student-1");
        student.setAuthUserId("user-1");
        student.setStudentCode("HS001");
        student.setAcademicClassId("class-1");
        Classroom classroom = new Classroom();
        classroom.setId("class-1");
        classroom.setName("10A1");
        Assignment assignment = new Assignment();
        assignment.setId("assignment-1");
        assignment.setTenantId("erg");
        assignment.setAcademicClassId("class-1");
        assignment.setQuizId("quiz-1");
        assignment.setStatus("active");
        assignment.setStudentIds(List.of("student-1"));
        QuizPackage pkg = new QuizPackage();
        pkg.setId("pkg-1");
        pkg.setQuizId("quiz-1");
        pkg.setQuizVersionId("version-1");
        pkg.setContentHash("sha256:abc");

        when(fixture.students.findByTenantIdAndStudentCode("erg", "HS001")).thenReturn(Optional.of(student));
        when(fixture.classrooms.findByTenantIdAndId("erg", "class-1")).thenReturn(Optional.of(classroom));
        when(fixture.packages.packageEntityById("erg", "pkg-1")).thenReturn(Optional.of(pkg));
        when(fixture.assignments.findByTenantIdAndQuizId("erg", "quiz-1")).thenReturn(List.of(assignment));
        when(fixture.assignments.findByTenantIdAndIdIn(any(), any())).thenReturn(List.of(assignment));
        when(fixture.assignments.findByTenantIdAndId("erg", "assignment-1")).thenReturn(Optional.of(assignment));
    }

    @Test
    void checksumMatchesTheClientCanonicalJsonByteForByte() {
        Map<String, Object> q1 = Map.of("type", "single", "choiceId", "cé\"\n");
        Map<String, Object> q2 = Map.of("n", 1.5, "m", 2, "z", 0.25);
        Map<String, Object> body = body(Map.of("q1", q1, "q2", q2));
        body.put("attemptId", "a-1");

        // Value produced by the TypeScript canonicalizer + SHA-256 for the same body (see portable-exam-result.ts).
        assertThat(PortableResultChecksum.canonical(body)).contains("\"q2\":{\"m\":2,\"n\":1.5,\"z\":0.25}");
        assertThat(PortableResultChecksum.hash(body)).isEqualTo(
                "sha256:c4905c6fcc1c419dea9a0d14daa1317a849cf1718c5e3fce57ad974268841e6c");
    }

    @Test
    void importCreatesOneServerGradedAttemptAndKeepsClientTimesAsMetadata() {
        Map<String, Object> body = body(Map.of("q1", "A"));

        ImportedAttemptResponse result = service.importPortableResult(request(body), null, null, access("user-1"));

        assertThat(result.duplicate()).isFalse();
        assertThat(result.studentId()).isEqualTo("student-1");
        ArgumentCaptor<Attempt> saved = ArgumentCaptor.forClass(Attempt.class);
        verify(fixture.attempts).saveAndFlush(saved.capture());
        Attempt attempt = saved.getValue();
        assertThat(attempt.getPackageId()).isEqualTo("pkg-1");
        assertThat(attempt.getSubmittedAt()).isAfter(OffsetDateTime.parse("2026-11-02T00:00:00Z"));
        assertThat(attempt.getClient()).containsEntry("source", "portable-result")
                .containsEntry("clientSubmittedAt", "2026-11-01T01:30:00.000Z");
        assertThat(attempt.getSubmitIdempotencyKey()).startsWith("sha256:");
        assertThat(attempt.getStatus()).isNotEqualTo("in_progress");
    }

    @Test
    void sameFileAgainReturnsTheExistingAttemptWithoutSavingTwice() {
        Map<String, Object> body = body(Map.of("q1", "A"));
        ImportPortableResultRequest request = request(body);
        Attempt existing = new Attempt();
        existing.setId("attempt-9");
        existing.setStatus("submitted");
        when(fixture.attempts.findByStartIdempotencyKey("erg", "assignment-1", "quiz-1", "student-1",
                request.checksum())).thenReturn(Optional.of(existing));

        ImportedAttemptResponse result = service.importPortableResult(request, null, null, access("user-1"));

        assertThat(result.duplicate()).isTrue();
        assertThat(result.attemptId()).isEqualTo("attempt-9");
        verify(fixture.attempts, never()).saveAndFlush(any());
    }

    @Test
    void tamperedBodyFailsTheChecksum() {
        Map<String, Object> body = body(Map.of("q1", "A"));
        ImportPortableResultRequest request = request(body);
        body.put("attemptId", "edited");

        assertThatThrownBy(() -> service.importPortableResult(request, null, null, access("user-1")))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("checksum");
        verify(fixture.attempts, never()).saveAndFlush(any());
    }

    @Test
    void packageHashThatDiffersFromThePublishedPackageIsRejected() {
        Map<String, Object> body = body(Map.of("q1", "A"));
        ((Map<String, Object>) body.get("package")).put("contentHash", "sha256:other");

        assertThatThrownBy(() -> service.importPortableResult(request(body), null, null, access("user-1")))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("package contract");
        verify(fixture.attempts, never()).saveAndFlush(any());
    }

    @Test
    void anotherLearnersFileNeedsAReviewerPermission() {
        Map<String, Object> body = body(Map.of("q1", "A"));

        assertThatThrownBy(() -> service.importPortableResult(request(body), null, null, access("someone-else")))
                .isInstanceOf(ForbiddenException.class);
        verify(fixture.attempts, never()).saveAndFlush(any());
    }

    @Test
    void classCodeThatDoesNotMatchTheStudentsClassIsRejected() {
        Map<String, Object> body = body(Map.of("q1", "A"));
        ((Map<String, Object>) body.get("identity")).put("classCode", "11B2");

        assertThatThrownBy(() -> service.importPortableResult(request(body), null, null, access("user-1")))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("class code");
    }

    private Map<String, Object> body(Map<String, Object> answers) {
        Map<String, Object> pkg = new LinkedHashMap<>(Map.of("id", "pkg-1", "version", "v1", "quizId", "quiz-1",
                "contentHash", "sha256:abc", "gradingMode", "server-authoritative"));
        Map<String, Object> identity = new LinkedHashMap<>(Map.of("classCode", "10A1", "studentCode", "HS001"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("format", "erg.portable-result");
        body.put("schemaVersion", 1);
        body.put("package", pkg);
        body.put("identity", identity);
        body.put("attemptId", "a-1");
        body.put("startedAt", "2026-11-01T01:00:00.000Z");
        body.put("submittedAt", "2026-11-01T01:30:00.000Z");
        body.put("exportedAt", "2026-11-01T01:31:00.000Z");
        body.put("answers", answers);
        return body;
    }

    private ImportPortableResultRequest request(Map<String, Object> body) {
        return new ImportPortableResultRequest(body, PortableResultChecksum.hash(body));
    }

    private UserAccessContext access(String userId) {
        return new UserAccessContext(userId, "erg", List.of("*"), List.of("*"), null, null);
    }
}
