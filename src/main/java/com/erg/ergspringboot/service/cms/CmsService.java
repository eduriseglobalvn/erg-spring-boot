package com.erg.ergspringboot.service.cms;

import com.erg.ergspringboot.utils.IdGenerator;
import com.erg.ergspringboot.platform.persistence.ProcessLocalStatePolicy;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class CmsService {

    private final Map<String, Map<String, Object>> posts = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> categories = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> pages = new ConcurrentHashMap<>();
    private final ProcessLocalStatePolicy statePolicy;

    public CmsService(ProcessLocalStatePolicy statePolicy) {
        this.statePolicy = statePolicy;
    }

    public List<Map<String, Object>> list(String type) {
        statePolicy.assertEnabled("cms");
        return store(type).values().stream().toList();
    }

    public Map<String, Object> get(String type, String id) {
        statePolicy.assertEnabled("cms");
        return store(type).getOrDefault(id, Map.of("id", id, "type", type));
    }

    public Map<String, Object> bySlug(String type, String slug) {
        statePolicy.assertEnabled("cms");
        return store(type).values().stream()
                .filter(item -> slug.equals(item.get("slug")))
                .findFirst()
                .orElse(Map.of("slug", slug, "type", type));
    }

    public Map<String, Object> save(String type, Map<String, Object> body) {
        statePolicy.assertEnabled("cms");
        String id = IdGenerator.newId();
        Map<String, Object> item = new java.util.LinkedHashMap<>();
        item.put("id", id);
        item.put("type", type);
        item.put("status", "draft");
        item.put("createdAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        if (body != null) item.putAll(body);
        store(type).put(id, item);
        return item;
    }

    public Map<String, Object> update(String type, String id, Map<String, Object> body) {
        statePolicy.assertEnabled("cms");
        Map<String, Object> item = new java.util.LinkedHashMap<>(get(type, id));
        item.put("updatedAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        if (body != null) item.putAll(body);
        store(type).put(id, item);
        return item;
    }

    public Map<String, Object> mark(String type, String id, String status) {
        statePolicy.assertEnabled("cms");
        Map<String, Object> item = new java.util.LinkedHashMap<>(get(type, id));
        item.put("status", status);
        store(type).put(id, item);
        return item;
    }

    public Map<String, Object> delete(String type, String id) {
        statePolicy.assertEnabled("cms");
        store(type).remove(id);
        return Map.of("deleted", true, "id", id);
    }

    public Map<String, Object> menuStructure() {
        statePolicy.assertEnabled("cms");
        return Map.of("items", List.of(), "version", 1);
    }

    public Map<String, Object> imageUpload(Map<String, Object> body) {
        statePolicy.assertEnabled("cms");
        return Map.of("filename", IdGenerator.newId(), "url", "/uploads/posts/" + IdGenerator.newId(), "meta", body == null ? Map.of() : body);
    }

    private Map<String, Map<String, Object>> store(String type) {
        return switch (type) {
            case "categories" -> categories;
            case "pages" -> pages;
            default -> posts;
        };
    }
}
