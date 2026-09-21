package io.github.thiagojosetj.gym.data.local;

/** Helpers for SQL LIKE patterns. */
public final class SqlLike {

    private SqlLike() {
    }

    /**
     * Escapes LIKE wildcards so user text is matched literally. Queries using it must declare
     * {@code ESCAPE '\'}.
     */
    public static String escape(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
