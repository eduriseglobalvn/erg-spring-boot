package com.erg.ergspringboot.service.storage;

import com.erg.ergspringboot.exception.BadRequestException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class StoragePolicyService {

    private static final long MAX_BYTES = 25L * 1024L * 1024L;
    private static final List<String> ALLOWED_TYPES = List.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/webp",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    public Map<String, Object> validate(String filename, String mimeType, long size) {
        if (size > MAX_BYTES) {
            throw new BadRequestException("file is too large");
        }
        if (mimeType != null && !mimeType.isBlank() && !ALLOWED_TYPES.contains(mimeType)) {
            throw new BadRequestException("file type is not allowed");
        }
        return Map.of("filename", filename, "mimeType", mimeType == null ? "application/octet-stream" : mimeType, "size", size);
    }
}
