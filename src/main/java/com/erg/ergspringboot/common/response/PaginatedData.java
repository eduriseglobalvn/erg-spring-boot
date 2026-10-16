package com.erg.ergspringboot.common.response;

/** Legacy Go-compatible shape retained for source compatibility; do not use for new endpoints. */
@Deprecated(forRemoval = false)
public record PaginatedData<T>(
        T data,
        long total,
        int page,
        int limit,
        long totalPages
) {

    public static <T> PaginatedData<T> of(T data, long total, int page, int limit) {
        long totalPages = limit <= 0 ? 0 : (total + limit - 1) / limit;
        return new PaginatedData<>(data, total, page, limit, totalPages);
    }
}
