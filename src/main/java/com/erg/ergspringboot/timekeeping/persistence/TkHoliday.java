package com.erg.ergspringboot.timekeeping.persistence;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import com.erg.ergspringboot.timekeeping.HolidayKind;
import com.erg.ergspringboot.timekeeping.HolidayPayMode;
import com.erg.ergspringboot.timekeeping.HolidayTeachingAction;
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
@Table(name = "tk_holidays")
public class TkHoliday extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "school_id")
    private String schoolId;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private HolidayKind kind = HolidayKind.STATE;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_mode", nullable = false)
    private HolidayPayMode payMode = HolidayPayMode.PAID;

    @Enumerated(EnumType.STRING)
    @Column(name = "teaching_action", nullable = false)
    private HolidayTeachingAction teachingAction = HolidayTeachingAction.CANCEL;

    @Column(name = "created_by")
    private String createdBy;
}
