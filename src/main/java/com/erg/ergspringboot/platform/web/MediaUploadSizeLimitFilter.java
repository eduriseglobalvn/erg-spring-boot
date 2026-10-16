package com.erg.ergspringboot.platform.web;

import com.erg.ergspringboot.service.quiz.media.MediaService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// The unauthenticated media upload endpoint binds @RequestBody byte[], so Spring buffers the entire
// body into the heap before the controller can check its size. Reject oversized (or Content-Length
// absent / chunked) uploads here, before a single byte is read, so an anonymous caller cannot OOM
// the process. ponytail: header check only; a chunked body with no Content-Length is refused
// outright rather than stream-counted, which is enough for this permitAll path.
@Component
public class MediaUploadSizeLimitFilter extends OncePerRequestFilter {

    private static final String UPLOAD_PATH_PREFIX = "/api/v1/lcms/media/upload/";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (request.getRequestURI().startsWith(UPLOAD_PATH_PREFIX)) {
            long contentLength = request.getContentLengthLong();
            if (contentLength < 0 || contentLength > MediaService.MAX_IMAGE_BYTES) {
                response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                        "media upload exceeds the maximum allowed size");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
