package io.github.thiagojosetj.gym.domain.template;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One card of "Meus treinos". */
public record TemplateSummary(
        String id,
        String name,
        String description,
        int exerciseCount,
        int setCount,
        List<String> muscleGroups) {

    public TemplateSummary {
        muscleGroups = muscleGroups == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(muscleGroups));
    }
}
