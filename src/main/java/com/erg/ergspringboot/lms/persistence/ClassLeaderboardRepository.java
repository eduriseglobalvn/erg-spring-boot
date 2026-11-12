package com.erg.ergspringboot.lms.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Whether a class shows the "Thi đua" board to its students (the board itself is read by ClassLeaderboardService). */
public interface ClassLeaderboardRepository extends Repository<Classroom, String> {

    @Query("""
            select classroom.leaderboardEnabled from Classroom classroom
            where classroom.tenantId = :tenantId and classroom.id = :academicClassId
            """)
    Optional<Boolean> findLeaderboardEnabled(String tenantId, String academicClassId);
}
