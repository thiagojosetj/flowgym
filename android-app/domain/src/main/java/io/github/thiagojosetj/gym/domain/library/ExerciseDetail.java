package io.github.thiagojosetj.gym.domain.library;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.thiagojosetj.gym.domain.model.Laterality;
import io.github.thiagojosetj.gym.domain.model.LoadBasis;
import io.github.thiagojosetj.gym.domain.model.TrackingType;

/**
 * Everything the exercise detail screen shows (PRODUCT_SPEC LIB-04).
 *
 * @param instructionSteps  one entry per step, already split and trimmed
 * @param primaryMuscles    first entry is the highlighted one
 * @param custom            true for exercises created by the user
 */
public record ExerciseDetail(
        String id,
        String name,
        String description,
        List<String> instructionSteps,
        String tips,
        String commonMistakes,
        String notes,
        TrackingType trackingType,
        LoadBasis loadBasis,
        int implementCount,
        Laterality laterality,
        List<MuscleLink> primaryMuscles,
        List<MuscleLink> secondaryMuscles,
        List<String> equipmentNames,
        boolean custom) {

    public ExerciseDetail {
        instructionSteps = copy(instructionSteps);
        primaryMuscles = copy(primaryMuscles);
        secondaryMuscles = copy(secondaryMuscles);
        equipmentNames = copy(equipmentNames);
    }

    private static <T> List<T> copy(List<T> list) {
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(list));
    }

    /** Splits stored instructions ("one step per line") into non-blank, trimmed steps. */
    public static List<String> splitSteps(String instructions) {
        List<String> steps = new ArrayList<>();
        if (instructions == null) {
            return steps;
        }
        for (String line : instructions.split("\\r\\n|\\r|\\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                steps.add(trimmed);
            }
        }
        return steps;
    }
}
