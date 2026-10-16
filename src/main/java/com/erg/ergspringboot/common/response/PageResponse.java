package com.erg.ergspringboot.common.response;

import java.util.List;

/** Stable zero-based offset pagination contract. Cursor pagination must use a separate response type. */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
}
