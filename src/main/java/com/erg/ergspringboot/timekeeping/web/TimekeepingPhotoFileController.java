package com.erg.ergspringboot.timekeeping.web;

import com.erg.ergspringboot.timekeeping.TimekeepingOperations;
import com.erg.ergspringboot.timekeeping.TimekeepingViews.PhotoFile;
import com.erg.ergspringboot.timekeeping.internal.evidence.LocalPrivateObjectStorage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves locally stored punch photos (dev). No session: the 5-minute HMAC signature in the URL is the authorisation,
 * so the path is permitAll in SecurityConfig. With R2 the presigned URL is used and this answers 404.
 */
@RestController
public class TimekeepingPhotoFileController {

    private final TimekeepingOperations timekeeping;

    public TimekeepingPhotoFileController(TimekeepingOperations timekeeping) {
        this.timekeeping = timekeeping;
    }

    @GetMapping(LocalPrivateObjectStorage.PHOTO_FILE_PATH)
    public ResponseEntity<byte[]> photo(@RequestParam String key, @RequestParam long exp, @RequestParam String sig) {
        PhotoFile file = timekeeping.signedPhoto(key, exp, sig);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(file.bytes());
    }
}
