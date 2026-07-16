package com.agricore.common.api;

import java.util.List;

/**
 * Standard pagination envelope.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / (double) size);
        return new PageResponse<>(
                content,
                page,
                size,
                totalElements,
                totalPages,
                page <= 0,
                page + 1 >= totalPages
        );
    }
}
