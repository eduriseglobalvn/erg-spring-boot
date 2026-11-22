package com.erg.ergspringboot.lms;

import com.erg.ergspringboot.lms.LmsClassScoreBoardResponses.ClassScoreBoardResponse;
import com.erg.ergspringboot.lms.LmsClassScoreBoardResponses.ScoreBoardAttempt;
import com.erg.ergspringboot.lms.LmsClassScoreBoardResponses.ScoreBoardStudent;
import com.erg.ergspringboot.lms.persistence.ClassStudentScoreRepository;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.lms.persistence.StudentRepository;
import com.erg.ergspringboot.lms.persistence.projection.ClassScoreBoardAttemptProjection;
import com.erg.ergspringboot.lms.persistence.projection.ClassWorkspaceProjection;
import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read boundary of the class score board (owner request 18/11): every handed-in attempt of a class with its topic. */
@Service
// Without a tenant-aware transaction the tenant GUC is never set and RLS hides every row from the runtime role.
@Transactional(value = "postgresTransactionManager", readOnly = true)
public class LmsClassScoreBoardOperations {

    // shortcut: one request carries the whole class; past this many attempts the oldest are cut and "truncated" says so, page it then.
    static final int MAX_ATTEMPTS = 5_000;
    private static final int MAX_STUDENTS = 200;

    private final ClassroomRepository classrooms;
    private final StudentRepository students;
    private final ClassStudentScoreRepository attempts;

    public LmsClassScoreBoardOperations(ClassroomRepository classrooms, StudentRepository students, ClassStudentScoreRepository attempts) {
        this.classrooms = classrooms;
        this.students = students;
        this.attempts = attempts;
    }

    /** Finished (handed-in) attempts only, never live attempt state; 30 s, evicted by the attempt and roster writes of the class. */
    @Cacheable(cacheNames = "lmsClassScoreBoard", sync = true, key = "{#tenantId, #classId}")
    public ClassScoreBoardResponse scoreBoard(String tenantId, String classId) {
        ClassWorkspaceProjection info = classrooms.findClassWorkspace(tenantId, classId, null)
                .orElseThrow(() -> new NoSuchElementException("Class not found."));
        var rosterPage = students.findClassRoster(tenantId, classId, null,
                        PageRequest.of(0, MAX_STUDENTS, Sort.by("fullName").ascending().and(Sort.by("studentCode").ascending())));
        List<ScoreBoardStudent> roster = rosterPage.getContent().stream()
                .map(s -> new ScoreBoardStudent(s.id(), s.studentCode(), s.fullName())).toList();
        List<ClassScoreBoardAttemptProjection> rows = attempts.findClassAttempts(tenantId, classId,
                LmsStatePolicy.completedAttemptStates(), PageRequest.of(0, MAX_ATTEMPTS + 1));
        boolean truncated = rows.size() > MAX_ATTEMPTS;
        List<ScoreBoardAttempt> items = rows.stream().limit(MAX_ATTEMPTS).map(LmsClassScoreBoardOperations::attempt).toList();
        return new ClassScoreBoardResponse(info.id(), info.name(), roster, items, truncated, rosterPage.hasNext());
    }

    static ScoreBoardAttempt attempt(ClassScoreBoardAttemptProjection row) {
        Integer seconds = row.startedAt() == null || row.submittedAt() == null || row.submittedAt().isBefore(row.startedAt()) ? null
                : (int) Math.min(Integer.MAX_VALUE, Duration.between(row.startedAt(), row.submittedAt()).getSeconds());
        return new ScoreBoardAttempt(row.id(), row.studentId(), row.assignmentId(), row.assignmentTitle(), row.quizId(), row.quizTitle(),
                row.topicId(), row.topicName(), row.score(), row.maxScore(), row.percent(),
                row.status() == null ? null : row.status().toLowerCase(java.util.Locale.ROOT), row.startedAt(), row.submittedAt(), seconds);
    }
}
