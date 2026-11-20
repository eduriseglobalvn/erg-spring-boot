package com.erg.ergspringboot.lms.internal.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.erg.ergspringboot.lcms.persistence.quiz.bank.QuizRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizVersionQuestionRepository;
import com.erg.ergspringboot.lcms.persistence.quiz.version.QuizVersionRepository;
import com.erg.ergspringboot.lms.internal.game.GameDtos.RoomSummary;
import com.erg.ergspringboot.lms.internal.game.GameModel.Room;
import com.erg.ergspringboot.lms.internal.game.GameModel.Rules;
import com.erg.ergspringboot.lms.internal.quiz.runtime.QuizRuntimePackageService;
import com.erg.ergspringboot.lms.persistence.ClassroomRepository;
import com.erg.ergspringboot.lms.persistence.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

/** {@code RoomSummary} of a live room: ids, question set and accuracy come from memory. */
class GameRoomSummaryTest {

    private final GameRoomService service = new GameRoomService(mock(QuizVersionRepository.class),
            mock(QuizVersionQuestionRepository.class), mock(QuizRepository.class),
            mock(QuizRuntimePackageService.class), mock(ClassroomRepository.class), mock(StudentRepository.class),
            mock(GameRoomManager.class), mock(GameResultWriter.class), mock(JdbcTemplate.class), new ObjectMapper());

    @Test
    void accuracyIsCorrectOverAnsweredAndNullBeforeTheFirstAnswer() {
        Room room = new Room();
        room.id = "r1";
        room.quizId = "quiz-1";
        room.quizVersionId = "qv-1";
        room.questionSet = "CLASS_WRONG";
        room.rules = new Rules(false, false, true, true, true, "RANDOM");
        RoomSummary empty = service.summary(room);
        assertThat(empty.accuracy()).isNull();
        assertThat(empty.quizId()).isEqualTo("quiz-1");
        assertThat(empty.quizVersionId()).isEqualTo("qv-1");
        assertThat(empty.questionSet()).isEqualTo("CLASS_WRONG");

        room.questionStats.put("q0", new int[] {3, 1});
        room.questionStats.put("q1", new int[] {1, 0});
        assertThat(service.summary(room).accuracy()).isEqualTo(0.75);
    }
}
