package io.github.thiagojosetj.gym.ui.library;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.github.thiagojosetj.gym.data.repository.ExerciseRepository;
import io.github.thiagojosetj.gym.domain.library.Equipment;
import io.github.thiagojosetj.gym.domain.library.ExerciseFilter;
import io.github.thiagojosetj.gym.domain.library.ExerciseSummary;
import io.github.thiagojosetj.gym.domain.library.MuscleGroup;
import io.github.thiagojosetj.gym.domain.library.MuscleRoleScope;

/**
 * State of the library screen: the current filter, the filtered list and (in select mode) the
 * picked exercises in tap order. Survives rotation; the fragment only renders it.
 */
public final class ExerciseLibraryViewModel extends ViewModel {

    private final MutableLiveData<ExerciseFilter> filter = new MutableLiveData<>(ExerciseFilter.none());
    private final LiveData<List<ExerciseSummary>> exercises;
    private final LiveData<List<MuscleGroup>> muscleGroups;
    private final LiveData<List<Equipment>> equipment;
    private final LinkedHashSet<String> selectedIds = new LinkedHashSet<>();
    private final MutableLiveData<List<String>> selection = new MutableLiveData<>(Collections.emptyList());

    public ExerciseLibraryViewModel(ExerciseRepository repository) {
        // Each new filter swaps the underlying Room query; the old one stops being observed.
        this.exercises = Transformations.switchMap(filter, repository::observeLibrary);
        this.muscleGroups = repository.observeMuscleGroups();
        this.equipment = repository.observeEquipment();
    }

    public LiveData<ExerciseFilter> filter() {
        return filter;
    }

    public LiveData<List<ExerciseSummary>> exercises() {
        return exercises;
    }

    public LiveData<List<MuscleGroup>> muscleGroups() {
        return muscleGroups;
    }

    public LiveData<List<Equipment>> equipment() {
        return equipment;
    }

    /** Selected exercise ids, in the order they were tapped. */
    public LiveData<List<String>> selection() {
        return selection;
    }

    public ExerciseFilter currentFilter() {
        return Objects.requireNonNull(filter.getValue());
    }

    public void setQuery(String query) {
        update(currentFilter().withQuery(query));
    }

    public void selectGroup(String groupId) {
        update(currentFilter().withMuscleGroup(groupId));
    }

    public void selectSubgroup(String subgroupId) {
        update(currentFilter().withMuscleSubgroup(subgroupId));
    }

    public void setRoleScope(MuscleRoleScope scope) {
        update(currentFilter().withRoleScope(scope));
    }

    public void setEquipment(Set<String> equipmentIds) {
        update(currentFilter().withEquipment(equipmentIds));
    }

    public void clearStructuredFilters() {
        update(currentFilter().clearStructuredFilters());
    }

    public void toggleSelection(String exerciseId) {
        if (!selectedIds.remove(exerciseId)) {
            selectedIds.add(exerciseId);
        }
        selection.setValue(Collections.unmodifiableList(new ArrayList<>(selectedIds)));
    }

    /** Avoids re-running the query (and flickering the list) when nothing actually changed. */
    private void update(ExerciseFilter next) {
        if (!next.equals(filter.getValue())) {
            filter.setValue(next);
        }
    }
}
