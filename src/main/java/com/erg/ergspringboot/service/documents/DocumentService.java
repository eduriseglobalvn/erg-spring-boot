package com.erg.ergspringboot.service.documents;

import com.erg.ergspringboot.service.storage.StoragePolicyService;
import com.erg.ergspringboot.utils.IdGenerator;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class DocumentService {

    private final Map<String, Map<String, Object>> documents = new ConcurrentHashMap<>();
    private final StoragePolicyService storagePolicyService;

    public DocumentService(StoragePolicyService storagePolicyService) {
        this.storagePolicyService = storagePolicyService;
    }

    public Map<String, Object> upload(Map<String, Object> body, String uploadedBy, String tenantId) {
        String filename = value(body, "filename", "document.pdf");
        String mimeType = value(body, "mimeType", "application/pdf");
        long size = longValue(body, "size", 0);
        storagePolicyService.validate(filename, mimeType, size);
        String id = IdGenerator.newId();
        Map<String, Object> document = new java.util.LinkedHashMap<>();
        document.put("id", id);
        document.put("tenantId", tenantId);
        document.put("filename", filename);
        document.put("originalName", value(body, "originalName", filename));
        document.put("mimeType", mimeType);
        document.put("size", size);
        document.put("storageType", value(body, "storageType", "local"));
        document.put("storageKey", "documents/" + id + "/" + filename);
        document.put("status", "active");
        document.put("uploadedBy", uploadedBy);
        document.put("createdAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        documents.put(id, document);
        return document;
    }

    public List<Map<String, Object>> list() {
        return documents.values().stream().toList();
    }

    public Map<String, Object> get(String id) {
        return documents.getOrDefault(id, Map.of("id", id, "status", "missing"));
    }

    public Map<String, Object> file(String id) {
        Map<String, Object> document = get(id);
        return Map.of(
                "id", id,
                "storageKey", document.getOrDefault("storageKey", ""),
                "streaming", true,
                "downloadUrl", "/api/v1/documents/" + id + "/file"
        );
    }

    public Map<String, Object> update(String id, Map<String, Object> body) {
        Map<String, Object> document = new java.util.LinkedHashMap<>(get(id));
        if (body != null) document.putAll(body);
        document.put("updatedAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        documents.put(id, document);
        return document;
    }

    public Map<String, Object> delete(String id) {
        documents.remove(id);
        return Map.of("deleted", true, "id", id, "remoteObjectDeleted", true);
    }

    private String value(Map<String, Object> body, String key, String fallback) {
        if (body == null || body.get(key) == null) {
            return fallback;
        }
        return body.get(key).toString();
    }

    private long longValue(Map<String, Object> body, String key, long fallback) {
        if (body == null || body.get(key) == null) {
            return fallback;
        }
        Object value = body.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
