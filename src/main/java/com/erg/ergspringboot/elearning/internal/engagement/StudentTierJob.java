package com.erg.ergspringboot.elearning.internal.engagement;

import com.erg.ergspringboot.platform.events.ClusterJobLock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Monday 00:00 (Asia/Ho_Chi_Minh): stores the week's tier of every student. Safe to run twice (it replaces the week). */
@Component
public class StudentTierJob {

    static final String JOB_NAME = "student-tier-week";
    private static final Logger log = LoggerFactory.getLogger(StudentTierJob.class);

    private final StudentTierService tiers;
    private final ClusterJobLock jobLock;

    public StudentTierJob(StudentTierService tiers, ObjectProvider<ClusterJobLock> jobLock) {
        this.tiers = tiers;
        this.jobLock = jobLock.getIfAvailable();
    }

    @Scheduled(cron = "${erg.elearning.tier.cron:0 0 0 * * MON}", zone = "Asia/Ho_Chi_Minh")
    public void weekly() {
        // One instance runs the update, like the other cluster jobs.
        if (jobLock == null) run(mondayOf(LocalDate.now(LeaderboardXp.ZONE)));
        else jobLock.runExclusively(JOB_NAME, () -> run(mondayOf(LocalDate.now(LeaderboardXp.ZONE))));
    }

    /** Also the backfill entry point: {@code weekStart} must be a Monday. */
    public void run(LocalDate weekStart) {
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) throw new IllegalArgumentException("weekStart must be a Monday");
        int failed = 0;
        for (StudentTierService.Cohort cohort : tiers.cohorts()) {
            try {
                tiers.refreshCohort(cohort, weekStart);
            } catch (RuntimeException error) {
                // One broken cohort must not stop the others; the next run replaces the week again.
                failed++;
                log.error("Tier update failed for school {} grade {} week {}", cohort.schoolId(), cohort.grade(), weekStart, error);
            }
        }
        log.info("Tier update for week {} done, {} cohort(s) failed", weekStart, failed);
    }

    static LocalDate mondayOf(LocalDate day) {
        return day.with(DayOfWeek.MONDAY);
    }
}
