package com.erg.ergspringboot.curriculum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.erg.ergspringboot.curriculum.CurriculumDtos.CurriculumClassSubjectAllocationItem;
import com.erg.ergspringboot.curriculum.CurriculumDtos.CurriculumClassSubjectsRequest;
import com.erg.ergspringboot.curriculum.persistence.CurriculumClassSubjectAllocation;
import com.erg.ergspringboot.curriculum.persistence.CurriculumClassSubjectAllocationRepository;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CurriculumClassSubjectServiceTest {

    @Mock
    CurriculumClassSubjectAllocationRepository allocations;

    private final UserAccessContext access = new UserAccessContext(
            "user-1", "tenant-1", List.of("school-1"), List.of(), "school-1", null);

    private CurriculumClassSubjectService service() {
        return new CurriculumClassSubjectService(allocations);
    }

    private CurriculumClassSubjectAllocation row(String classId, String subjectId, long version) {
        CurriculumClassSubjectAllocation row = new CurriculumClassSubjectAllocation();
        row.setAcademicClassId(classId);
        row.setSubjectId(subjectId);
        row.setVersion(version);
        return row;
    }

    @Test
    void getGroupsSubjectsByClassAndReportsTheSetVersion() {
        when(allocations.findByTenantIdAndSchoolIdAndAcademicYearOrderByAcademicClassIdAscSubjectIdAsc(
                "tenant-1", "school-1", "2026-2027")).thenReturn(List.of(
                row("class-1", "math", 3), row("class-1", "lit", 3), row("class-2", "math", 3)));

        var response = service().get("school-1", "2026-2027", access, false);

        assertThat(response.version()).isEqualTo(3L);
        assertThat(response.allocations()).extracting(CurriculumClassSubjectAllocationItem::classId)
                .containsExactly("class-1", "class-2");
        assertThat(response.allocations().get(0).subjectIds()).containsExactly("math", "lit");
    }

    @Test
    void rejectsStaleClientVersionBeforeReplacingTheSet() {
        when(allocations.findByTenantIdAndSchoolIdAndAcademicYearOrderByAcademicClassIdAscSubjectIdAsc(
                "tenant-1", "school-1", "2026-2027")).thenReturn(List.of(row("class-1", "math", 4)));
        var request = new CurriculumClassSubjectsRequest(
                List.of(new CurriculumClassSubjectAllocationItem("class-1", List.of("math"))), 3L);

        assertThatThrownBy(() -> service().replace("school-1", "2026-2027", request, access, false))
                .isInstanceOf(CurriculumException.class)
                .hasMessageContaining("reload before saving");
        verify(allocations, never()).deleteScope(any(), any(), any());
    }

    @Test
    void replaceDeletesTheScopeThenInsertsOneRowPerClassSubjectWithTheNextVersion() {
        when(allocations.findByTenantIdAndSchoolIdAndAcademicYearOrderByAcademicClassIdAscSubjectIdAsc(
                "tenant-1", "school-1", "2026-2027")).thenReturn(List.of(row("class-9", "old", 2)));
        when(allocations.existingClassIds(any(), any(), any())).thenReturn(List.of("class-1"));
        when(allocations.existingSubjectIds(any(), any())).thenReturn(List.of("math"));
        when(allocations.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new CurriculumClassSubjectsRequest(List.of(
                new CurriculumClassSubjectAllocationItem("class-1", List.of("math", "math", " ")),
                new CurriculumClassSubjectAllocationItem("class-2", List.of())), 2L);

        var response = service().replace("school-1", "2026-2027", request, access, false);

        verify(allocations).deleteScope("tenant-1", "school-1", "2026-2027");
        assertThat(response.version()).isEqualTo(3L);
        assertThat(response.allocations()).hasSize(1);
        assertThat(response.allocations().get(0).subjectIds()).containsExactly("math");
    }

    @Test
    void removingASubjectTaughtWithinTheLastMonthIsRejectedAndNothingIsDeleted() {
        when(allocations.findByTenantIdAndSchoolIdAndAcademicYearOrderByAcademicClassIdAscSubjectIdAsc(
                "tenant-1", "school-1", "2026-2027")).thenReturn(List.of(row("class-1", "subject-1", 2)));
        when(allocations.lastTeachingDate("tenant-1", java.util.Set.of("class-1"), java.util.Set.of("class-1|subject-1")))
                .thenReturn(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(3));

        assertThatThrownBy(() -> service().replace("school-1", "2026-2027",
                new CurriculumClassSubjectsRequest(List.of(), 2L), access, false))
                .isInstanceOf(CurriculumException.class).hasMessageContaining("Chỉ gỡ được từ ngày");
        verify(allocations, never()).deleteScope(any(), any(), any());
    }

    @Test
    void removingASubjectLastTaughtOverAMonthAgoIsAllowed() {
        when(allocations.findByTenantIdAndSchoolIdAndAcademicYearOrderByAcademicClassIdAscSubjectIdAsc(
                "tenant-1", "school-1", "2026-2027")).thenReturn(List.of(row("class-1", "subject-1", 2)));
        when(allocations.lastTeachingDate("tenant-1", java.util.Set.of("class-1"), java.util.Set.of("class-1|subject-1")))
                .thenReturn(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).minusMonths(1).minusDays(1));

        service().replace("school-1", "2026-2027", new CurriculumClassSubjectsRequest(List.of(), 2L), access, false);

        verify(allocations).deleteScope("tenant-1", "school-1", "2026-2027");
    }

    @Test
    void removedPairsAreCheckedWithOneGroupedQueryEach() {
        when(allocations.findByTenantIdAndSchoolIdAndAcademicYearOrderByAcademicClassIdAscSubjectIdAsc(
                "tenant-1", "school-1", "2026-2027")).thenReturn(List.of(
                row("class-1", "subject-1", 2), row("class-1", "subject-2", 2), row("class-2", "subject-1", 2)));

        service().replace("school-1", "2026-2027", new CurriculumClassSubjectsRequest(List.of(), 2L), access, false);

        verify(allocations, org.mockito.Mockito.times(1)).lastTeachingDate("tenant-1",
                java.util.Set.of("class-1", "class-2"),
                java.util.Set.of("class-1|subject-1", "class-1|subject-2", "class-2|subject-1"));
        verify(allocations, org.mockito.Mockito.times(1)).lastTeachingHoursMonth("tenant-1", "school-1",
                java.util.Set.of("subject-1", "subject-2"));
    }

    @Test
    void rejectsSubjectUnknownInTheTenant() {
        when(allocations.findByTenantIdAndSchoolIdAndAcademicYearOrderByAcademicClassIdAscSubjectIdAsc(
                "tenant-1", "school-1", "2026-2027")).thenReturn(List.of());
        when(allocations.existingClassIds(any(), any(), any())).thenReturn(List.of("class-1"));
        when(allocations.existingSubjectIds(any(), any())).thenReturn(List.of());
        var request = new CurriculumClassSubjectsRequest(
                List.of(new CurriculumClassSubjectAllocationItem("class-1", List.of("math"))), null);

        assertThatThrownBy(() -> service().replace("school-1", "2026-2027", request, access, false))
                .isInstanceOf(CurriculumException.class).hasMessageContaining("subject not found");
        verify(allocations, never()).deleteScope(any(), any(), any());
    }

    @Test
    void aTeacherWithoutSchoolScopeReadsOnlyTheSubjectsOfTheirOwnClasses() {
        UserAccessContext teacher = new UserAccessContext("user-2", "tenant-1", List.of(), List.of("class-2"), null, null);
        when(allocations.findByTenantIdAndSchoolIdAndAcademicYearOrderByAcademicClassIdAscSubjectIdAsc(
                "tenant-1", "school-1", "2026-2027")).thenReturn(List.of(
                row("class-1", "math", 3), row("class-2", "ic3", 3)));

        var response = service().get("school-1", "2026-2027", teacher, false);

        assertThat(response.allocations()).extracting(CurriculumClassSubjectAllocationItem::classId)
                .containsExactly("class-2");
    }

    @Test
    void rejectsSchoolOutsideTheCallersScope() {
        assertThatThrownBy(() -> service().get("school-2", "2026-2027", access, false))
                .isInstanceOf(CurriculumException.class);
    }

    @Test
    void replaceStoresTheLevelOfASubjectAndReturnsIt() {
        when(allocations.findByTenantIdAndSchoolIdAndAcademicYearOrderByAcademicClassIdAscSubjectIdAsc(
                "tenant-1", "school-1", "2026-2027")).thenReturn(List.of());
        when(allocations.existingClassIds(any(), any(), any())).thenReturn(List.of("class-1"));
        when(allocations.existingSubjectIds(any(), any())).thenReturn(List.of("ic3"));
        when(allocations.existingLevelKeys(any(), any())).thenReturn(List.of("lvl-1|ic3"));
        when(allocations.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new CurriculumClassSubjectsRequest(List.of(new CurriculumClassSubjectAllocationItem(
                "class-1", List.of("ic3"), java.util.Map.of("ic3", "lvl-1"))), null);

        var response = service().replace("school-1", "2026-2027", request, access, false);

        assertThat(response.allocations().get(0).levelIds()).containsEntry("ic3", "lvl-1");
        org.mockito.ArgumentCaptor<List<CurriculumClassSubjectAllocation>> saved = org.mockito.ArgumentCaptor.captor();
        verify(allocations).saveAll(saved.capture());
        assertThat(saved.getValue()).singleElement().extracting(CurriculumClassSubjectAllocation::getLevelId).isEqualTo("lvl-1");
    }

    @Test
    void rejectsALevelThatDoesNotBelongToTheSubject() {
        when(allocations.findByTenantIdAndSchoolIdAndAcademicYearOrderByAcademicClassIdAscSubjectIdAsc(
                "tenant-1", "school-1", "2026-2027")).thenReturn(List.of());
        when(allocations.existingClassIds(any(), any(), any())).thenReturn(List.of("class-1"));
        when(allocations.existingSubjectIds(any(), any())).thenReturn(List.of("ic3"));
        when(allocations.existingLevelKeys(any(), any())).thenReturn(List.of("lvl-1|python"));
        var request = new CurriculumClassSubjectsRequest(List.of(new CurriculumClassSubjectAllocationItem(
                "class-1", List.of("ic3"), java.util.Map.of("ic3", "lvl-1"))), null);

        assertThatThrownBy(() -> service().replace("school-1", "2026-2027", request, access, false))
                .isInstanceOf(CurriculumException.class).hasMessageContaining("level not found");
        verify(allocations, never()).deleteScope(any(), any(), any());
    }

    @Test
    void withoutAnAcademicYearMoreThanTheCapIsRefusedWithACode() {
        when(allocations.findByTenantIdAndSchoolIdOrderByAcademicClassIdAscSubjectIdAsc(
                any(), any(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(java.util.Collections.nCopies(CurriculumClassSubjectService.MAX_ALL_YEARS_ROWS + 1, row("c", "math", 1)));

        assertThatThrownBy(() -> service().get("school-1", null, access, true))
                .isInstanceOf(CurriculumException.class)
                .hasFieldOrPropertyWithValue("code", "CURRICULUM_ALL_YEARS_TOO_LARGE");
    }
}
