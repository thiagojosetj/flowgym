package io.github.thiagojosetj.gym.domain.template;

/** Where a template came from. Persisted by {@link #name()}: never rename a constant. */
public enum TemplateOrigin {
    CREATED,
    /** Copy of another template of the same user. */
    DUPLICATED,
    /** Imported from a shared link/code (Phase 10); always an independent copy. */
    IMPORTED
}
