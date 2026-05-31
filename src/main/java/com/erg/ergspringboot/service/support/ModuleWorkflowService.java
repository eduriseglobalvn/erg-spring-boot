package com.erg.ergspringboot.service.support;

import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class ModuleWorkflowService {

    private final Map<String, Map<String, Map<String, Object>>> stores = new ConcurrentHashMap<>();

    public List<Map<String, Object>> list(String module, String type) {
        return new ArrayList<>(store(module, type).values());
    }

    public Map<String, Object> get(String module, String type, String id) {
        return store(module, type).getOrDefault(id, Map.of("id", id, "module", module, "type", type, "status", "missing"));
    }

    public Map<String, Object> create(String module, String type, Map<String, Object> body) {
        Map<String, Object> item = base(module, type, IdGenerator.newId());
        item.put("status", item.getOrDefault("status", "active"));
        if (body != null) {
            item.putAll(body);
        }
        store(module, type).put(item.get("id").toString(), item);
        return item;
    }

    public Map<String, Object> update(String module, String type, String id, Map<String, Object> body) {
        Map<String, Object> item = new LinkedHashMap<>(get(module, type, id));
        if (body != null) {
            item.putAll(body);
        }
        item.put("updatedAt", now());
        store(module, type).put(id, item);
        return item;
    }

    public Map<String, Object> mark(String module, String type, String id, String status) {
        Map<String, Object> item = new LinkedHashMap<>(get(module, type, id));
        item.put("status", status);
        item.put("updatedAt", now());
        store(module, type).put(id, item);
        return item;
    }

    public Map<String, Object> delete(String module, String type, String id) {
        store(module, type).remove(id);
        return Map.of("deleted", true, "id", id, "module", module, "type", type);
    }

    public Map<String, Object> accepted(String module, String operation, Map<String, Object> body) {
        return Map.of(
                "module", module,
                "operation", operation,
                "accepted", true,
                "request", body == null ? Map.of() : body,
                "acceptedAt", now()
        );
    }

    public Map<String, Object> status(String module, String operation) {
        return Map.of(
                "module", module,
                "operation", operation,
                "status", "available",
                "healthy", true,
                "checkedAt", now()
        );
    }

    public Map<String, Object> job(String module, String operation, Map<String, Object> body) {
        Map<String, Object> job = create(module, "jobs", Map.of(
                "operation", operation,
                "payload", body == null ? Map.of() : body,
                "status", "queued",
                "retryPolicy", Map.of("maxAttempts", 3, "backoff", "exponential"),
                "dlq", module + ":dlq"
        ));
        return job;
    }

    public Map<String, Object> checklist(String module, List<String> items) {
        return Map.of(
                "module", module,
                "items", items.stream().map(item -> Map.of("name", item, "status", "ready")).toList(),
                "generatedAt", now()
        );
    }

    private Map<String, Object> base(String module, String type, String id) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("module", module);
        item.put("type", type);
        item.put("createdAt", now());
        return item;
    }

    private Map<String, Map<String, Object>> store(String module, String type) {
        return stores.computeIfAbsent(module + ":" + type, key -> new ConcurrentHashMap<>());
    }

    private String now() {
        return OffsetDateTime.now(ZoneOffset.UTC).toString();
    }
}
