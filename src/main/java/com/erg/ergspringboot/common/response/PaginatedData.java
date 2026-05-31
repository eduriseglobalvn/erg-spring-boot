package com.erg.ergspringboot.common.response;

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
