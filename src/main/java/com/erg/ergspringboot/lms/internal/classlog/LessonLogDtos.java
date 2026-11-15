package com.erg.ergspringboot.lms.internal.classlog;

import java.time.LocalDate;
import java.util.List;

/** GraphQL shapes of the class log (schema 55-class-log.graphqls); dates travel as ISO strings via the Date scalar. */
public final class LessonLogDtos {

    private LessonLogDtos() {}

    public record LessonLogWeek(LocalDate weekStart, LocalDate weekEnd, int weekNo, List<LessonLogDay> days,
                                LessonLogStats stats, LessonLogLag lagChip) {}

    public record LessonLogDay(LocalDate date, String session, List<LessonLogPeriod> periods) {}

    public record LessonLogPeriod(String eventId, LocalDate date, int periodNo, String classId, String className,
                                  String subjectId, String subjectName, String teacherName, int ppctPeriod, int actualPeriod, String lessonTitle,
                                  List<LessonLogAbsentee> absentees, String comment, String status, boolean late,
                                  boolean happened, int version, Integer curriculumPeriodCount) {}

    public record LessonLogAbsentee(String studentId, String shortName, boolean excused) {}

    public record LessonLogStats(int taught, int confirmed, int pending) {}

    public record LessonLogLag(String classId, String className, int lag) {}

    public record LessonLogChange(String id, String at, String actorId, String actorName, String classId, String className,
                                  LocalDate date, int periodNo, String field, String oldValue, String newValue) {}

    public record CurriculumLessonRow(String topic, int period, String title) {}

    public record LessonReportMatrix(LocalDate weekStart, List<LocalDate> days, LessonReportStats stats, List<LessonReportRow> rows) {}

    /** Period counts: all taught periods of the week and how many of them are on time, late or missing. */
    public record LessonReportStats(int periods, int teachers, int schools, int onTime, int late, int missing) {}

    public record LessonReportRow(String teacherId, String teacherName, String schoolName, List<LessonReportCell> cells) {}

    public record LessonReportCell(LocalDate date, String state, int periods) {}
}
