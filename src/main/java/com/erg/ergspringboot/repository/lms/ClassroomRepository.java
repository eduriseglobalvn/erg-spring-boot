package com.erg.ergspringboot.repository.lms;

import com.erg.ergspringboot.model.lms.Classroom;
import com.erg.ergspringboot.repository.lms.projection.ClassOptionProjection;
import com.erg.ergspringboot.repository.lms.projection.ClassWorkspaceProjection;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ClassroomRepository extends JpaRepository<Classroom, String> {

    List<Classroom> findTop500ByTenantIdOrderByNameAsc(String tenantId);

    long countByTenantIdAndSchoolIdAndStatusIgnoreCase(String tenantId, String schoolId, String status);

    long countByTenantIdAndSchoolId(String tenantId, String schoolId);

    List<Classroom> findByTenantIdOrderByNameAsc(String tenantId);

    List<Classroom> findByTenantIdAndSchoolIdOrderByNameAsc(String tenantId, String schoolId);

    List<Classroom> findByTenantIdAndSchoolIdOrderByNameAsc(
            String tenantId, String schoolId, Pageable pageable);

    List<Classroom> findByTenantIdAndSchoolIdInOrderByNameAsc(String tenantId, Collection<String> schoolIds);

    List<Classroom> findByTenantIdAndSchoolIdInOrderByNameAsc(
            String tenantId, Collection<String> schoolIds, Pageable pageable);

    List<Classroom> findByTenantIdAndIdIn(String tenantId, Collection<String> ids);

    @Query("""
            select distinct classroom.schoolId
            from Classroom classroom
            where classroom.tenantId = :tenantId
              and classroom.schoolId is not null
              and classroom.schoolId <> ''
            order by classroom.schoolId asc
            """)
    List<String> findDistinctSchoolIdsByTenant(String tenantId);

    @Query("""
            select distinct classroom.homeroomTeacherId
            from Classroom classroom
            where classroom.tenantId = :tenantId
              and classroom.homeroomTeacherId is not null
              and classroom.homeroomTeacherId <> ''
            order by classroom.homeroomTeacherId asc
            """)
    List<String> findHomeroomTeacherIdsByTenant(String tenantId);

    @Query("""
            select distinct classroom.homeroomTeacherId
            from Classroom classroom
            where classroom.tenantId = :tenantId
              and classroom.homeroomTeacherId is not null
              and classroom.homeroomTeacherId <> ''
            order by classroom.homeroomTeacherId asc
            """)
    List<String> findHomeroomTeacherIdsByTenant(String tenantId, Pageable pageable);

    @Query("""
            select distinct classroom.homeroomTeacherId
            from Classroom classroom
            where classroom.tenantId = :tenantId
              and classroom.schoolId in :schoolIds
              and classroom.homeroomTeacherId is not null
              and classroom.homeroomTeacherId <> ''
            order by classroom.homeroomTeacherId asc
            """)
    List<String> findHomeroomTeacherIdsByTenantAndSchoolIds(String tenantId, Collection<String> schoolIds);

    @Query("""
            select distinct classroom.homeroomTeacherId
            from Classroom classroom
            where classroom.tenantId = :tenantId
              and classroom.schoolId in :schoolIds
              and classroom.homeroomTeacherId is not null
              and classroom.homeroomTeacherId <> ''
            order by classroom.homeroomTeacherId asc
            """)
    List<String> findHomeroomTeacherIdsByTenantAndSchoolIds(
            String tenantId, Collection<String> schoolIds, Pageable pageable);

    @Query("""
            select distinct classroom.schoolId
            from Classroom classroom
            where classroom.tenantId = :tenantId
              and classroom.schoolId is not null
              and classroom.schoolId <> ''
              and (
                    classroom.homeroomTeacherId = :userId
                    or exists (
                        select 1
                        from Assignment assignment
                        where assignment.tenantId = :tenantId
                          and assignment.academicClassId = classroom.id
                          and assignment.assignedBy = :userId
                    )
              )
            order by classroom.schoolId asc
            """)
    List<String> findTeacherManagedSchoolIds(String tenantId, String userId);

    @Query("""
            select distinct new com.erg.ergspringboot.repository.lms.projection.ClassOptionProjection(
                classroom.id,
                classroom.schoolId,
                classroom.name,
                classroom.grade,
                classroom.academicYear,
                classroom.status
            )
            from Classroom classroom
            where classroom.tenantId = :tenantId
              and (
                    classroom.homeroomTeacherId = :teacherId
                    or exists (
                        select 1
                        from Assignment assignment
                        where assignment.tenantId = :tenantId
                          and assignment.academicClassId = classroom.id
                          and assignment.assignedBy = :teacherId
                    )
              )
            order by classroom.name asc
            """)
    List<ClassOptionProjection> findTeacherClassOptions(String tenantId, String teacherId);

    @Query(
            value = """
                    select new com.erg.ergspringboot.repository.lms.projection.ClassWorkspaceProjection(
                        classroom.id,
                        classroom.schoolId,
                        classroom.name,
                        classroom.grade,
                        classroom.academicYear,
                        classroom.status,
                        classroom.homeroomTeacherId,
                        (
                            select count(student.id)
                            from Student student
                            where student.tenantId = :tenantId
                              and student.academicClassId = classroom.id
                        ),
                        (
                            select count(assignment.id)
                            from Assignment assignment
                            where assignment.tenantId = :tenantId
                              and assignment.academicClassId = classroom.id
                        ),
                        classroom.updatedAt
                    )
                    from Classroom classroom
                    where classroom.tenantId = :tenantId
                      and classroom.schoolId = :schoolId
                      and (:status is null or lower(classroom.status) = :status)
                    order by classroom.name asc
                    """,
            countQuery = """
                    select count(classroom.id)
                    from Classroom classroom
                    where classroom.tenantId = :tenantId
                      and classroom.schoolId = :schoolId
                      and (:status is null or lower(classroom.status) = :status)
                    """
    )
    Page<ClassWorkspaceProjection> findSchoolClassWorkspaces(
            String tenantId,
            String schoolId,
            String status,
            Pageable pageable
    );

    @Query("""
            select new com.erg.ergspringboot.repository.lms.projection.ClassWorkspaceProjection(
                classroom.id,
                classroom.schoolId,
                classroom.name,
                classroom.grade,
                classroom.academicYear,
                classroom.status,
                classroom.homeroomTeacherId,
                (
                    select count(student.id)
                    from Student student
                    where student.tenantId = :tenantId
                      and student.academicClassId = classroom.id
                ),
                (
                    select count(assignment.id)
                    from Assignment assignment
                    where assignment.tenantId = :tenantId
                      and assignment.academicClassId = classroom.id
                ),
                classroom.updatedAt
            )
            from Classroom classroom
            where classroom.tenantId = :tenantId
              and classroom.id = :classId
              and (:schoolId is null or classroom.schoolId = :schoolId)
            """)
    Optional<ClassWorkspaceProjection> findClassWorkspace(
            String tenantId,
            String classId,
            String schoolId
    );
}
