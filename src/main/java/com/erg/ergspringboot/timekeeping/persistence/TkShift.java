package com.erg.ergspringboot.timekeeping.persistence;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tk_shifts")
public class TkShift extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "school_id", nullable = false)
    private String schoolId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "work_units", nullable = false, precision = 3, scale = 1)
    private BigDecimal workUnits = BigDecimal.ONE;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "weekdays", nullable = false)
    private Integer[] weekdays;

    @Column(name = "active", nullable = false)
    private Boolean active = true;
}
