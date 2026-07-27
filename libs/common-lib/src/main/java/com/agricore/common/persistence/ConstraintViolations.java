package com.agricore.common.persistence;

import java.sql.SQLException;

/**
 * Classifies database constraint failures by SQLState.
 *
 * <p>Services need to tell a duplicate key — which is the caller's problem and a 409 — apart from
 * a foreign-key or not-null violation, which is the service's own problem and a 500. The
 * persistence layer does not make that easy: Hibernate's JPA dialect translates every class 23
 * failure to a single {@code DataIntegrityViolationException}, and the finer
 * {@code DuplicateKeyException} is only produced on the plain JDBC path.
 *
 * <p>So the discrimination happens here, on the SQLState carried by the root {@link SQLException}.
 * SQLState is chosen over message matching deliberately: the standard class 23 codes are reported
 * identically by H2 and PostgreSQL, while the message text differs by dialect and by version.
 *
 * <p>Deliberately free of Spring types. That is what lets this sit in {@code common-lib}, which
 * {@code PackageArchitectureTest} keeps framework-independent — the caller catches the Spring
 * exception and passes it in as a plain {@link Throwable}.
 */
public final class ConstraintViolations {

    /** SQL-standard class 23 integrity-constraint violations. */
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final String NOT_NULL_VIOLATION = "23502";

    /**
     * Guards against a self-referencing or cyclic cause chain, which would otherwise spin forever.
     * Real chains are three or four deep; anything past this is malformed.
     */
    private static final int MAX_CAUSE_DEPTH = 20;

    private ConstraintViolations() {
    }

    /**
     * True when the failure was a unique or primary key violation — the caller supplied a value
     * that is already taken.
     */
    public static boolean isUniqueViolation(Throwable throwable) {
        return UNIQUE_VIOLATION.equals(sqlState(throwable));
    }

    /**
     * True when a referenced row is missing or still referenced. Reported separately from a
     * duplicate because the two need opposite responses.
     */
    public static boolean isForeignKeyViolation(Throwable throwable) {
        return FOREIGN_KEY_VIOLATION.equals(sqlState(throwable));
    }

    /** True when a NOT NULL column was given no value. */
    public static boolean isNotNullViolation(Throwable throwable) {
        return NOT_NULL_VIOLATION.equals(sqlState(throwable));
    }

    /**
     * The SQLState of the first {@link SQLException} in the cause chain, or {@code null} when the
     * chain holds none. The chain is walked from the outside in, so the outermost driver exception
     * wins — that is the one whose SQLState the driver actually set.
     */
    public static String sqlState(Throwable throwable) {
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof SQLException sqlException) {
                String state = sqlException.getSQLState();
                if (state != null && !state.isBlank()) {
                    return state;
                }
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                return null;
            }
            current = cause;
        }
        return null;
    }
}
