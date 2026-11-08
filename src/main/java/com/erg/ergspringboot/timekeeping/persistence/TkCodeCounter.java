package com.erg.ergspringboot.timekeeping.persistence;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tk_code_counters")
public class TkCodeCounter extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "kind", nullable = false)
    private String kind;

    @Column(name = "scope", nullable = false)
    private String scope;

    @Column(name = "last_value", nullable = false)
    private Long lastValue = 0L;
}
