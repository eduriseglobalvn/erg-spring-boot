package com.erg.ergspringboot.timekeeping.persistence;

import com.erg.ergspringboot.model.BaseEntity;
import com.erg.ergspringboot.timekeeping.RemoteRuleKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tk_remote_rules")
public class TkRemoteRule extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "school_id", nullable = false)
    private String schoolId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private RemoteRuleKind kind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "user_ids", nullable = false, columnDefinition = "jsonb")
    private List<String> userIds = new ArrayList<>();

    @Column(name = "weekday")
    private Integer weekday;

    @Column(name = "shift_id")
    private String shiftId;

    @Column(name = "active", nullable = false)
    private Boolean active = true;
}
