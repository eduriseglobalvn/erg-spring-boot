package com.erg.ergspringboot.calendar.internal.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.calendar.CalendarScopeService;
import com.erg.ergspringboot.calendar.TeachingScheduleOperations;
import com.erg.ergspringboot.calendar.TeachingScheduleReuseInput;
import com.erg.ergspringboot.calendar.persistence.CalendarBatch;
import com.erg.ergspringboot.calendar.persistence.CalendarBatchRepository;
import com.erg.ergspringboot.calendar.persistence.CalendarEventRepository;
import com.erg.ergspringboot.calendar.TeachingScheduleResponses.BatchResponse;
import com.erg.ergspringboot.exception.ForbiddenException;
import com.erg.ergspringboot.platform.graphql.GraphQlAccessSupport;
import com.erg.ergspringboot.platform.security.ErgPolicyService;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Permission and school-scope gates of the batch lifecycle mutations (publish / cancel / rename). */
@ExtendWith(MockitoExtension.class)
class TeachingScheduleGraphQlControllerTest {

    private static final String BATCH_ID = "batch-1";

    @Mock private TeachingScheduleOperations schedule;
    @Mock private CalendarEventRepository eventRepository;
    @Mock private CalendarBatchRepository batchRepository;

    private TeachingScheduleGraphQlController controller;

    @BeforeEach
    void setUp() {
        GraphQlAccessSupport accessSupport = new GraphQlAccessSupport(new ErgPolicyService());
        controller = new TeachingScheduleGraphQlController(schedule, accessSupport,
                new CalendarScopeService(accessSupport, eventRepository, batchRepository));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void publishPassesTheExpectedVersionAndTheActor() {
        authenticate(principal(List.of("lms.schedule.manage"), List.of("school-1")));
        when(batchRepository.findByTenantIdAndId("tenant-a", BATCH_ID)).thenReturn(Optional.of(batch("school-1")));
        BatchResponse expected = new BatchResponse(BATCH_ID, "school-1", "Đợt 1", "PUBLISHED", 3, null, null, null, null, 10);
        when(schedule.publishBatch("tenant-a", BATCH_ID, 2, "teacher-1")).thenReturn(expected);

        assertThat(controller.publishTeachingScheduleBatch(BATCH_ID, 2)).isEqualTo(expected);
        verify(schedule).publishBatch("tenant-a", BATCH_ID, 2, "teacher-1");
    }

    @Test
    void cancelAndRenameRunWithTheirPermissions() {
        authenticate(principal(List.of("lms.schedule.manage"), List.of("school-1")));
        when(batchRepository.findByTenantIdAndId("tenant-a", BATCH_ID)).thenReturn(Optional.of(batch("school-1")));

        controller.cancelTeachingScheduleBatch(BATCH_ID, 1);
        controller.renameTeachingScheduleBatch(BATCH_ID, "HK1", 1);

        verify(schedule).cancelBatch("tenant-a", BATCH_ID, 1, "teacher-1");
        verify(schedule).renameBatch("tenant-a", BATCH_ID, "HK1", 1);
    }

    @Test
    void unparsableDatesAreADomainErrorNotAnInternalOne() {
        authenticate(principal(List.of("lms.schedule.manage"), List.of("school-1")));

        assertThatThrownBy(() -> controller.teachingScheduleOverviewGrid("not-a-date", List.of("school-1")))
                .isInstanceOfSatisfying(com.erg.ergspringboot.exception.TeachingScheduleException.class,
                        error -> assertThat(error.getCode()).isEqualTo("INVALID_DATE"));
        assertThatThrownBy(() -> controller.teachingScheduleSlotOptions("school-1", null, "2026-13-45", 1, null, null))
                .isInstanceOf(com.erg.ergspringboot.exception.TeachingScheduleException.class);
    }

    @Test
    void dayLoadRangesLongerThanSixWeeksOrReversedAreRejectedWithInvalidDateRange() {
        authenticate(principal(List.of("lms.schedule.read"), List.of("school-1")));

        for (String[] range : new String[][] {{"2026-11-01", "2026-12-14"}, {"2026-11-10", "2026-11-09"}}) {
            assertThatThrownBy(() -> controller.teachingScheduleDayLoads(range[0], range[1]))
                    .isInstanceOfSatisfying(com.erg.ergspringboot.exception.TeachingScheduleException.class,
                            error -> assertThat(error.getCode()).isEqualTo("INVALID_DATE_RANGE"));
        }
        verifyNoInteractions(schedule);
    }

    @Test
    void readOnlyPrincipalIsRejectedOnEveryLifecycleMutation() {
        authenticate(principal(List.of("lms.schedule.read"), List.of("school-1")));

        assertThatThrownBy(() -> controller.publishTeachingScheduleBatch(BATCH_ID, 1)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> controller.cancelTeachingScheduleBatch(BATCH_ID, 1)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> controller.renameTeachingScheduleBatch(BATCH_ID, "x", 1)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(schedule);
    }

    @Test
    void batchOfAnotherSchoolIsRejected() {
        authenticate(principal(List.of("lms.schedule.manage"), List.of("school-1")));
        when(batchRepository.findByTenantIdAndId("tenant-a", BATCH_ID)).thenReturn(Optional.of(batch("school-2")));

        assertThatThrownBy(() -> controller.publishTeachingScheduleBatch(BATCH_ID, 1)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(schedule);
    }

    @Test
    void overviewIsLimitedToTheManagedSchoolsOfTheCaller() {
        authenticate(principal(List.of("lms.schedule.read"), List.of("school-1", "school-2")));

        controller.teachingScheduleOverviewSchools("2026-11-09", "9a", "CONFLICT", 0, 50);
        controller.teachingScheduleOverviewTeachers("2026-11-09", null, null, "LOAD_DESC", 0, 50);

        verify(schedule).overviewSchools("tenant-a", List.of("school-1", "school-2"), java.time.LocalDate.of(2026, 11, 9), "9a", "CONFLICT", 0, 50);
        verify(schedule).overviewTeachers("tenant-a", List.of("school-1", "school-2"), java.time.LocalDate.of(2026, 11, 9), null, null,
                "LOAD_DESC", 0, 50);
    }

    @Test
    void overviewGridRejectsASchoolOutsideTheScope() {
        authenticate(principal(List.of("lms.schedule.read"), List.of("school-1")));

        assertThatThrownBy(() -> controller.teachingScheduleOverviewGrid("2026-11-09", List.of("school-1", "school-9")))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(schedule);
    }

    @Test
    void loadSettingsAreChangedOnlyByTheScheduleManager() {
        authenticate(principal(List.of("lms.schedule.update"), List.of("school-1")));
        assertThatThrownBy(() -> controller.updateTeachingScheduleLoadSettings(18, 24)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(schedule);

        authenticate(principal(List.of("lms.schedule.manage"), List.of("school-1")));
        controller.updateTeachingScheduleLoadSettings(16, 20);
        verify(schedule).updateLoadSettings("tenant-a", 16, 20, "teacher-1");
    }

    @Test
    void reuseNeedsCreatePermissionAndTheSourceSchoolInScope() {
        authenticate(principal(List.of("lms.schedule.read"), List.of("school-1")));
        TeachingScheduleReuseInput input = new TeachingScheduleReuseInput("school-1", "2026-11-09", "REPEAT_UNTIL", null, "2026-12-31", true, true);
        assertThatThrownBy(() -> controller.reuseTeachingScheduleWeek(input)).isInstanceOf(ForbiddenException.class);

        authenticate(principal(List.of("lms.schedule.create"), List.of("school-2")));
        assertThatThrownBy(() -> controller.reuseTeachingScheduleWeek(input)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(schedule);

        authenticate(principal(List.of("lms.schedule.create"), List.of("school-1")));
        controller.reuseTeachingScheduleWeek(input);
        verify(schedule).reuseWeek("tenant-a", input, "teacher-1");
    }

    @Test
    void periodTimesNeedTheManagePermissionAndTheSchoolInScope() {
        com.erg.ergspringboot.calendar.SchoolPeriodTimesInput input = new com.erg.ergspringboot.calendar.SchoolPeriodTimesInput(
                "school-1", "2026-11-01", "Mùa đông", List.of());
        authenticate(principal(List.of("lms.schedule.update"), List.of("school-1")));
        assertThatThrownBy(() -> controller.saveSchoolPeriodTimes(input)).isInstanceOf(ForbiddenException.class);

        authenticate(principal(List.of("lms.schedule.manage"), List.of("school-2")));
        assertThatThrownBy(() -> controller.saveSchoolPeriodTimes(input)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> controller.assignSchoolPeriodTemplate("school-1", "tpl-1", "2026-11-01"))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(schedule);

        authenticate(principal(List.of("lms.schedule.manage"), List.of("school-1")));
        controller.saveSchoolPeriodTimes(input);
        verify(schedule).saveSchoolPeriodTimes("tenant-a", input, "teacher-1");
    }

    @Test
    void aSharedOrDefaultTemplateCannotBeReplacedByAScopedManager() {
        authenticate(principal(List.of("lms.schedule.manage"), List.of("school-1")));
        var update = new com.erg.ergspringboot.calendar.TeachingPeriodTemplateInput("tpl-1", "x", List.of());
        when(schedule.isDefaultTemplate("tenant-a", "tpl-1")).thenReturn(false);
        when(schedule.templateSchools("tenant-a", "tpl-1")).thenReturn(List.of("school-1", "school-2"));
        assertThatThrownBy(() -> controller.saveTeachingPeriodTemplate(update)).isInstanceOf(ForbiddenException.class);

        when(schedule.isDefaultTemplate("tenant-a", "tpl-2")).thenReturn(true);
        assertThatThrownBy(() -> controller.saveTeachingPeriodTemplate(
                new com.erg.ergspringboot.calendar.TeachingPeriodTemplateInput("tpl-2", "x", List.of())))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void slotEditAndSwapNeedUpdatePermissionAndTheSchoolOfThePeriodInScope() {
        var scope = new com.erg.ergspringboot.calendar.TeachingScheduleSlotChanges.Scope("THIS_ONLY", null);
        var edit = new com.erg.ergspringboot.calendar.TeachingScheduleSlotChanges.Edit("e1", null, scope, null, 7, null, null, null, null,
                null, null);
        var swap = new com.erg.ergspringboot.calendar.TeachingScheduleSlotChanges.Swap("e1", null, scope, "t2", null, null, null);
        authenticate(principal(List.of("lms.schedule.read"), List.of("school-1")));
        assertThatThrownBy(() -> controller.editTeachingScheduleSlot(edit)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> controller.swapTeachingScheduleTeacher(swap)).isInstanceOf(ForbiddenException.class);

        com.erg.ergspringboot.calendar.persistence.CalendarEvent event = new com.erg.ergspringboot.calendar.persistence.CalendarEvent();
        event.setSchoolId("school-2");
        when(eventRepository.findByTenantIdAndId("tenant-a", "e1")).thenReturn(Optional.of(event));
        authenticate(principal(List.of("lms.schedule.update"), List.of("school-1")));
        assertThatThrownBy(() -> controller.editTeachingScheduleSlot(edit)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> controller.swapTeachingScheduleTeacher(swap)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(schedule);

        event.setSchoolId("school-1");
        controller.editTeachingScheduleSlot(edit);
        controller.swapTeachingScheduleTeacher(swap);
        verify(schedule).editSlot("tenant-a", edit, "teacher-1");
        verify(schedule).swapTeacher("tenant-a", swap, "teacher-1");
    }

    private static CalendarBatch batch(String schoolId) {
        CalendarBatch batch = new CalendarBatch();
        batch.setId(BATCH_ID);
        batch.setTenantId("tenant-a");
        batch.setSchoolId(schoolId);
        return batch;
    }

    private static ErgPrincipal principal(List<String> permissions, List<String> schools) {
        return new ErgPrincipal("teacher-1", "session-1", "tenant-a", List.of("teacher"), permissions, List.of(),
                List.of("lms"), "teacher", "school",
                Map.of("managedClassIds", List.of(), "managedEducationUnitIds", schools));
    }

    private static void authenticate(ErgPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}
