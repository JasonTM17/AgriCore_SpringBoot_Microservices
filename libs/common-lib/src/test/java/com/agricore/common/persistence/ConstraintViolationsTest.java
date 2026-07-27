package com.agricore.common.persistence;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class ConstraintViolationsTest {

    /**
     * The shape a service actually sees: the persistence provider wraps the driver exception twice
     * before it reaches an exception handler.
     */
    @Test
    void findsSqlStateThroughAWrappedChain() {
        Throwable wrapped = new RuntimeException("could not execute statement",
                new IllegalStateException("constraint violation",
                        new SQLException("duplicate key", "23505")));

        assertThat(ConstraintViolations.isUniqueViolation(wrapped)).isTrue();
        assertThat(ConstraintViolations.sqlState(wrapped)).isEqualTo("23505");
    }

    /**
     * The distinction the handler exists to make. A missing referenced row and a missing required
     * value are server-side faults; answering either with "already exists" would be a new wrong
     * answer replacing the old one.
     */
    @Test
    void separatesForeignKeyAndNotNullFromDuplicate() {
        Throwable foreignKey = new RuntimeException(new SQLException("fk", "23503"));
        Throwable notNull = new RuntimeException(new SQLException("not null", "23502"));

        assertThat(ConstraintViolations.isUniqueViolation(foreignKey)).isFalse();
        assertThat(ConstraintViolations.isForeignKeyViolation(foreignKey)).isTrue();

        assertThat(ConstraintViolations.isUniqueViolation(notNull)).isFalse();
        assertThat(ConstraintViolations.isNotNullViolation(notNull)).isTrue();
    }

    @Test
    void reportsNothingWhenTheChainHoldsNoSqlException() {
        Throwable plain = new RuntimeException("timeout", new IllegalStateException("closed"));

        assertThat(ConstraintViolations.sqlState(plain)).isNull();
        assertThat(ConstraintViolations.isUniqueViolation(plain)).isFalse();
    }

    @Test
    void toleratesNull() {
        assertThat(ConstraintViolations.sqlState(null)).isNull();
        assertThat(ConstraintViolations.isUniqueViolation(null)).isFalse();
    }

    /**
     * Some drivers construct a {@link SQLException} with no state at all. Returning the empty value
     * would make every classifier answer false for the wrong reason, so the walk continues instead.
     */
    @Test
    void skipsASqlExceptionCarryingNoState() {
        Throwable chain = new SQLException("outer", (String) null,
                new SQLException("inner", "23505"));

        assertThat(ConstraintViolations.sqlState(chain)).isEqualTo("23505");
    }

    /**
     * A self-referencing cause is malformed but not impossible, and it must not hang the request
     * thread inside an error handler.
     */
    @Test
    void doesNotSpinOnACyclicCauseChain() {
        SQLException first = new SQLException("first", (String) null);
        SQLException second = new SQLException("second", (String) null, first);
        first.initCause(second);

        assertThat(ConstraintViolations.sqlState(first)).isNull();
    }
}
