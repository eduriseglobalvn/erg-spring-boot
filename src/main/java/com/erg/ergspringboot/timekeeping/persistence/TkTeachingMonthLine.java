package com.erg.ergspringboot.timekeeping.persistence;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Snapshot row written when HR closes a month; later reads use it instead of the live schedule. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tk_teaching_month_lines")
public class TkTeachingMonthLine extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "month", length = 7, nullable = false)
    private String month;

    @Column(name = "teacher_user_id", length = 36, nullable = false)
    private String teacherUserId;

    /** MAIN | ASSISTANT. */
    @Column(name = "role", length = 16, nullable = false)
    private String role;

    @Column(name = "school_id", nullable = false)
    private String schoolId;

    @Column(name = "subject_id", length = 100, nullable = false)
    private String subjectId;

    @Column(name = "class_names", nullable = false, columnDefinition = "text")
    private String classNames;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "week_totals", nullable = false, columnDefinition = "integer[]")
    private Integer[] weekTotals;

    @Column(name = "total", nullable = false)
    private Integer total;
}
