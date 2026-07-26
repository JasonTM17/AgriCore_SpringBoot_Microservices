package com.agricore.common.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code totalPages}, {@code first}, and {@code last} are computed rather than supplied, so they are
 * the part of this envelope that can be wrong. Every list endpoint on the platform returns it.
 */
class PageResponseTest {

    @Test
    void roundsAPartialLastPageUp() {
        PageResponse<String> page = PageResponse.of(List.of("a"), 0, 20, 45);

        assertThat(page.totalPages()).as("45 items at 20 per page is 3 pages, not 2").isEqualTo(3);
        assertThat(page.first()).isTrue();
        assertThat(page.last()).isFalse();
    }

    @Test
    void doesNotAddAnEmptyPageWhenTheCountDividesExactly() {
        PageResponse<String> page = PageResponse.of(List.of("a"), 1, 20, 40);

        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.first()).isFalse();
        assertThat(page.last()).as("page index 1 is the second and final page").isTrue();
    }

    @Test
    void marksTheFinalPage() {
        PageResponse<String> page = PageResponse.of(List.of("a"), 2, 20, 45);

        assertThat(page.first()).isFalse();
        assertThat(page.last()).isTrue();
    }

    @Test
    void reportsAnEmptyResultAsBothFirstAndLast() {
        PageResponse<String> page = PageResponse.of(List.of(), 0, 20, 0);

        assertThat(page.totalPages()).isZero();
        assertThat(page.content()).isEmpty();
        assertThat(page.first()).isTrue();
        assertThat(page.last()).as("there is no next page to fetch").isTrue();
    }

    /**
     * Guards the division. A caller that passes {@code size = 0} gets zero pages rather than an
     * {@link ArithmeticException} out of a read endpoint.
     */
    @Test
    void survivesAZeroPageSize() {
        PageResponse<String> page = PageResponse.of(List.of(), 0, 0, 100);

        assertThat(page.totalPages()).isZero();
        assertThat(page.last()).isTrue();
    }

    /**
     * A page index past the end is still terminal — a client paging forward must stop rather than
     * loop.
     */
    @Test
    void treatsAPageBeyondTheEndAsLast() {
        PageResponse<String> page = PageResponse.of(List.of(), 9, 20, 45);

        assertThat(page.first()).isFalse();
        assertThat(page.last()).isTrue();
    }
}
