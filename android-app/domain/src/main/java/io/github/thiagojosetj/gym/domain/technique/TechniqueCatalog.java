package io.github.thiagojosetj.gym.domain.technique;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The techniques currently available, so pure rules can validate ids without touching the database. */
public final class TechniqueCatalog {

    private static final TechniqueCatalog EMPTY = new TechniqueCatalog(Collections.emptyList());

    private final Map<String, TrainingTechnique> byId;

    public TechniqueCatalog(List<TrainingTechnique> techniques) {
        Map<String, TrainingTechnique> map = new LinkedHashMap<>();
        for (TrainingTechnique technique : techniques) {
            map.put(technique.id(), technique);
        }
        this.byId = Collections.unmodifiableMap(map);
    }

    public static TechniqueCatalog empty() {
        return EMPTY;
    }

    /** @return the technique, or null when the id is unknown (e.g. removed from the catalog) */
    public TrainingTechnique byId(String id) {
        return id == null ? null : byId.get(id);
    }

    public List<TrainingTechnique> all() {
        // Not List.copyOf: that is API 30+ on Android (ADR-0004).
        return Collections.unmodifiableList(new ArrayList<>(byId.values()));
    }
}
