package com.erg.ergspringboot.common.request.documents;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.HashMap;
import java.util.Map;

public final class DocumentRequests {

    private DocumentRequests() {}

    public record UploadDocumentRequest(
            @NotBlank(message = "Document name is required")
            @Size(max = 500, message = "Document name must not exceed 500 characters")
            String name,

            @NotBlank(message = "Document content is required")
            String content,

            Map<String, Object> metadata
    ) {

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("name", name);
            map.put("content", content);
            if (metadata != null) {
                map.putAll(metadata);
            }
            return map;
        }
    }

    public record UpdateDocumentRequest(
            @Size(max = 500, message = "Document name must not exceed 500 characters")
            String name,

            String content,

            Map<String, Object> metadata
    ) {

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            if (name != null) {
                map.put("name", name);
            }
            if (content != null) {
                map.put("content", content);
            }
            if (metadata != null) {
                map.putAll(metadata);
            }
            return map;
        }
    }
}
