package com.erg.ergspringboot.model.auth;

import com.erg.ergspringboot.model.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "security_events")
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class SecurityEvent extends BaseEntity {
    private String tenantId;
    private String userId;
    private String eventType;
    private String severity;
    private String portal;
    private String deviceId;
    private String ipAddress;
    private String reason;
    private String platform;
    private String result;
    private String riskCode;
    private String sessionId;
    @JdbcTypeCode(SqlTypes.JSON)
    @jakarta.persistence.Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private OffsetDateTime occurredAt;
}
