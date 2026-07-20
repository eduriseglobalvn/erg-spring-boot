package com.erg.ergspringboot.service.catalog;

import com.erg.ergspringboot.utils.IdGenerator;
import com.erg.ergspringboot.platform.persistence.ProcessLocalStatePolicy;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {

    private final Map<String, Map<String, Object>> courses = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> categories = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> levels = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> units = new ConcurrentHashMap<>();
    private final ProcessLocalStatePolicy statePolicy;

    public CatalogService(ProcessLocalStatePolicy statePolicy) {
        this.statePolicy = statePolicy;
    }

    public List<Map<String, Object>> list(String type) {
        statePolicy.assertEnabled("catalog");
        return store(type).values().stream().toList();
    }

    public Map<String, Object> get(String type, String id) {
        statePolicy.assertEnabled("catalog");
        return store(type).getOrDefault(id, Map.of("id", id, "type", type));
    }

    public Map<String, Object> findBySlug(String type, String slug) {
        statePolicy.assertEnabled("catalog");
        return store(type).values().stream()
                .filter(item -> slug.equals(item.get("slug")) || slug.equals(item.get("subdomain")))
                .findFirst()
                .orElse(Map.of("slug", slug, "type", type));
    }

    public Map<String, Object> create(String type, Map<String, Object> body) {
        statePolicy.assertEnabled("catalog");
        String id = IdGenerator.newId();
        Map<String, Object> item = new java.util.LinkedHashMap<>();
        item.put("id", id);
        item.put("type", type);
        item.put("createdAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        if (body != null) item.putAll(body);
        store(type).put(id, item);
        return item;
    }

    public Map<String, Object> update(String type, String id, Map<String, Object> body) {
        statePolicy.assertEnabled("catalog");
        Map<String, Object> item = new java.util.LinkedHashMap<>(get(type, id));
        item.put("updatedAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        if (body != null) item.putAll(body);
        store(type).put(id, item);
        return item;
    }

    public Map<String, Object> delete(String type, String id) {
        statePolicy.assertEnabled("catalog");
        store(type).remove(id);
        return Map.of("deleted", true, "id", id);
    }

    public Map<String, Object> schema(String courseId) {
        statePolicy.assertEnabled("catalog");
        return Map.of("courseId", courseId, "schemaType", "course", "schemaData", Map.of());
    }

    private Map<String, Map<String, Object>> store(String type) {
        return switch (type) {
            case "categories" -> categories;
            case "levels" -> levels;
            case "units" -> units;
            default -> courses;
        };
    }
}
