package com.erg.ergspringboot.repository.lms;

import com.erg.ergspringboot.model.lms.Classroom;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ClassroomRepository extends JpaRepository<Classroom, String> {

    List<Classroom> findByTenantIdOrderByNameAsc(String tenantId);

    List<Classroom> findByTenantIdAndSchoolIdOrderByNameAsc(String tenantId, String schoolId);

    List<Classroom> findByTenantIdAndSchoolIdInOrderByNameAsc(String tenantId, Collection<String> schoolIds);

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
}
