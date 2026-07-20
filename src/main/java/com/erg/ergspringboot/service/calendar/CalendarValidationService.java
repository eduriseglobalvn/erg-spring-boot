package com.erg.ergspringboot.service.calendar;

import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarBulkCreateRequest;
import com.erg.ergspringboot.common.request.calendar.CalendarRequests.CalendarBulkRowRequest;
import com.erg.ergspringboot.common.response.calendar.CalendarResponses.CalendarRowMessageResponse;
import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.model.lms.Level;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class CalendarValidationService {

    public List<RowValidationResult> validateRows(
            CalendarBulkCreateRequest input,
            CalendarExpansionService.ReferenceData refData
    ) {
        List<RowValidationResult> results = new ArrayList<>();

        for (CalendarBulkRowRequest row : input.rows()) {
            List<CalendarRowMessageResponse> errors = new ArrayList<>();
            List<CalendarRowMessageResponse> warnings = new ArrayList<>();

            if (row.classIds() == null || row.classIds().isEmpty()) {
                errors.add(msg("CLASS_REQUIRED", "classIds", "It nhat mot lop hoc la bat buoc"));
            } else {
                for (String classId : row.classIds()) {
                    Classroom cls = refData.classMap().get(classId);
                    if (cls == null) {
                        errors.add(msg("CLASS_NOT_FOUND", "classIds",
                                "Lop khong ton tai: " + classId));
                    } else if (!input.schoolId().equals(cls.getSchoolId())) {
                        errors.add(msg("CLASS_NOT_IN_SCHOOL", "classIds",
                                "Lop " + cls.getName() + " khong thuoc truong da chon"));
                    }
                }
            }

            if (row.mainTeacherIds() == null || row.mainTeacherIds().isEmpty()) {
                errors.add(msg("MAIN_TEACHER_REQUIRED", "mainTeacherIds",
                        "It nhat mot giao vien chinh la bat buoc"));
            } else {
                for (String teacherId : row.mainTeacherIds()) {
                    if (!refData.userMap().containsKey(teacherId)) {
                        errors.add(msg("TEACHER_NOT_FOUND", "mainTeacherIds",
                                "Giao vien khong ton tai: " + teacherId));
                    }
                }
            }

            if (row.assistantTeacherIds() != null) {
                for (String teacherId : row.assistantTeacherIds()) {
                    if (!refData.userMap().containsKey(teacherId)) {
                        errors.add(msg("TEACHER_NOT_FOUND", "assistantTeacherIds",
                                "Giao vien khong ton tai: " + teacherId));
                    }
                }
            }

            if (!refData.subjectMap().containsKey(row.subjectId())) {
                errors.add(msg("SUBJECT_NOT_FOUND", "subjectId",
                        "Mon hoc khong ton tai: " + row.subjectId()));
            }

            if (row.levelId() != null && !row.levelId().isBlank()) {
                Level level = refData.levelMap().get(row.levelId());
                if (level == null) {
                    errors.add(msg("LEVEL_NOT_FOUND", "levelId",
                            "Level khong ton tai: " + row.levelId()));
                } else if (!level.getSubjectId().equals(row.subjectId())) {
                    errors.add(msg("LEVEL_NOT_IN_SUBJECT", "levelId",
                            "Level khong thuoc mon hoc da chon"));
                }
            }

            if (row.assistantTeacherIds() != null && row.mainTeacherIds() != null) {
                Set<String> mainSet = new HashSet<>(row.mainTeacherIds());
                for (String assistantId : row.assistantTeacherIds()) {
                    if (mainSet.contains(assistantId)) {
                        errors.add(msg("DUPLICATE_TEACHER_ROLE", "assistantTeacherIds",
                                "Giao vien khong the vua la chinh vua la tro giang"));
                    }
                }
            }

            if (row.periodCount() < 1 || row.periodCount() > 10) {
                errors.add(msg("INVALID_PERIOD_COUNT", "periodCount",
                        "So tiet phai tu 1 den 10"));
            }

            if (row.periodStart() < 1) {
                errors.add(msg("INVALID_PERIOD_START", "periodStart",
                        "Tiet bat dau phai >= 1"));
            }

            try {
                LocalTime start = LocalTime.parse(row.startTime());
                LocalTime end = LocalTime.parse(row.endTime());
                if (!start.isBefore(end)) {
                    errors.add(msg("INVALID_TIME_RANGE", "startTime",
                            "Gio bat dau phai truoc gio ket thuc"));
                }
            } catch (DateTimeParseException e) {
                errors.add(msg("INVALID_TIME_FORMAT", "startTime",
                        "Dinh dang gio khong hop le (HH:mm)"));
            }

            if (row.weekday() < 1 || row.weekday() > 7) {
                errors.add(msg("INVALID_WEEKDAY", "weekday",
                        "Thu trong tuan phai tu 1 den 7"));
            }

            results.add(new RowValidationResult(
                    row.clientRowId(),
                    errors.isEmpty(),
                    errors,
                    warnings
            ));
        }

        return results;
    }

    private static CalendarRowMessageResponse msg(String code, String field, String message) {
        return new CalendarRowMessageResponse(code, field, message);
    }

    public record RowValidationResult(
            String clientRowId,
            boolean valid,
            List<CalendarRowMessageResponse> errors,
            List<CalendarRowMessageResponse> warnings
    ) {}
}
