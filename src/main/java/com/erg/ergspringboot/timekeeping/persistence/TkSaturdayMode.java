package com.erg.ergspringboot.timekeeping.persistence;

import com.erg.ergspringboot.model.BaseEntity;
import com.erg.ergspringboot.timekeeping.SaturdayMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tk_saturday_modes")
public class TkSaturdayMode extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false)
    private SaturdayMode mode;

    @Column(name = "created_by")
    private String createdBy;
}
