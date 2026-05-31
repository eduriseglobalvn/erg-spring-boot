package com.erg.ergspringboot.common.dto;

public record PageRequestDto(
        Integer page,
        Integer size,
        String sortBy,
        String sortDirection
) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    public int normalizedPage() {
        return page == null || page < 0 ? DEFAULT_PAGE : page;
    }

    public int normalizedSize() {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }

        return Math.min(size, MAX_SIZE);
    }
}
