package com.erg.ergspringboot.lms.internal.game;

import com.erg.ergspringboot.lms.internal.game.GameModel.Rules;
import java.util.Map;

/** REST shapes of {@code /api/v1/lms/games} (see wayground/HOP_DONG_API.md). */
public final class GameDtos {

    private GameDtos() { }

    public record RulesRequest(Boolean allowHack, Boolean emergencyPatch, Boolean showLeaderboard,
                               Boolean showSolution, Boolean allowLateJoin, String questionOrder) { }

    public record CreateRoomRequest(String gameType, String quizVersionId, String classId, Integer durationMinutes,
                                    String questionSet, RulesRequest rules) { }

    public record JoinRequest(String code) { }

    public record JoinResponse(String roomId) { }

    public record RoomSummary(String id, String gameType, String code, String status, String classId, String className,
                              String quizId, String quizVersionId, String quizTitle, String questionSet,
                              int durationMinutes, Rules rules, int playerCount, int questionCount, Double accuracy,
                              String createdAt, String startedAt, String endedAt) { }

    public record SourceItem(String quizVersionId, String quizId, String title, int versionNumber, String publishedAt,
                             int questionCount, int playableCount, int filteredCount,
                             Map<String, Integer> filteredByKind, Integer classSize, Integer classWrongCount,
                             LastGame lastGame) { }

    /** Latest ended room of the caller for a quiz (any version). */
    public record LastGame(String roomId, String gameType, String className, String endedAt) { }
}
