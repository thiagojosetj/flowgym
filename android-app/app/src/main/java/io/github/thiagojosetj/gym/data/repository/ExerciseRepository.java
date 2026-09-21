package io.github.thiagojosetj.gym.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.github.thiagojosetj.gym.core.AppExecutors;
import io.github.thiagojosetj.gym.data.local.SqlLike;
import io.github.thiagojosetj.gym.data.local.dao.CatalogDao;
import io.github.thiagojosetj.gym.data.local.dao.ExerciseDao;
import io.github.thiagojosetj.gym.data.local.entity.EquipmentEntity;
import io.github.thiagojosetj.gym.data.local.entity.ExerciseEntity;
import io.github.thiagojosetj.gym.data.local.entity.MuscleEntity;
import io.github.thiagojosetj.gym.data.local.row.ExerciseListRow;
import io.github.thiagojosetj.gym.data.local.row.ExerciseMuscleRow;
import io.github.thiagojosetj.gym.data.local.row.ExerciseRefRow;
import io.github.thiagojosetj.gym.domain.library.Equipment;
import io.github.thiagojosetj.gym.domain.library.ExerciseDetail;
import io.github.thiagojosetj.gym.domain.library.ExerciseFilter;
import io.github.thiagojosetj.gym.domain.library.ExerciseSummary;
import io.github.thiagojosetj.gym.domain.library.MuscleGroup;
import io.github.thiagojosetj.gym.domain.library.MuscleLink;
import io.github.thiagojosetj.gym.domain.library.MuscleNode;
import io.github.thiagojosetj.gym.domain.model.MuscleRole;
import io.github.thiagojosetj.gym.domain.template.ExerciseRef;

/** The exercise library: the only way UI code reads exercises, muscles and equipment. */
public final class ExerciseRepository {

    private final ExerciseDao exerciseDao;
    private final CatalogDao catalogDao;
    private final AppExecutors executors;

    public ExerciseRepository(ExerciseDao exerciseDao, CatalogDao catalogDao, AppExecutors executors) {
        this.exerciseDao = exerciseDao;
        this.catalogDao = catalogDao;
        this.executors = executors;
    }

    public LiveData<List<MuscleGroup>> observeMuscleGroups() {
        return Transformations.map(catalogDao.observeMuscles(), entities -> {
            List<MuscleNode> nodes = new ArrayList<>(entities.size());
            for (MuscleEntity m : entities) {
                nodes.add(new MuscleNode(m.id, m.code, m.name, m.parentId, m.sortOrder));
            }
            return MuscleGroup.fromNodes(nodes);
        });
    }

    public LiveData<List<Equipment>> observeEquipment() {
        return Transformations.map(catalogDao.observeEquipment(), entities -> {
            List<Equipment> result = new ArrayList<>(entities.size());
            for (EquipmentEntity e : entities) {
                result.add(new Equipment(e.id, e.code, e.name));
            }
            return result;
        });
    }

    /** Re-emits automatically whenever the catalog (or the user's custom exercises) change. */
    public LiveData<List<ExerciseSummary>> observeLibrary(ExerciseFilter filter) {
        List<String> equipmentIds = new ArrayList<>(filter.equipmentIds());
        LiveData<List<ExerciseListRow>> rows = exerciseDao.observeLibrary(
                SqlLike.escape(filter.normalizedQuery()),
                filter.effectiveMuscleId(),
                filter.roleScope().name(),
                equipmentIds,
                equipmentIds.size());
        return Transformations.map(rows, list -> {
            List<ExerciseSummary> result = new ArrayList<>(list.size());
            for (ExerciseListRow r : list) {
                result.add(new ExerciseSummary(r.id, r.name, r.primaryMuscleName, r.primaryGroupName,
                        r.primaryEquipmentName));
            }
            return result;
        });
    }

    /** Loads one exercise; delivers null when it does not exist (or is not visible to this user). */
    public void loadDetail(String exerciseId, Consumer<ExerciseDetail> onResult, Consumer<Throwable> onError) {
        executors.runOnDisk(() -> {
            ExerciseEntity e = exerciseDao.findVisibleById(exerciseId);
            if (e == null) {
                return null;
            }
            List<MuscleLink> primary = new ArrayList<>();
            List<MuscleLink> secondary = new ArrayList<>();
            for (ExerciseMuscleRow row : exerciseDao.findMuscles(exerciseId)) {
                MuscleLink link = new MuscleLink(row.muscleId, row.muscleName, row.groupName);
                (row.role == MuscleRole.PRIMARY ? primary : secondary).add(link);
            }
            return new ExerciseDetail(e.id, e.name, e.description, ExerciseDetail.splitSteps(e.instructions),
                    e.tips, e.commonMistakes, e.notes, e.trackingType, e.loadBasis, e.implementCount,
                    e.laterality, primary, secondary, exerciseDao.findEquipmentNames(exerciseId),
                    e.ownerUserId != null);
        }, onResult, onError);
    }

    /**
     * Loads template references for the given exercises, in the requested order. Ids that no
     * longer exist are skipped.
     */
    public void loadRefs(List<String> exerciseIds, Consumer<List<ExerciseRef>> onResult,
                         Consumer<Throwable> onError) {
        List<String> requested = new ArrayList<>(exerciseIds);
        executors.runOnDisk(() -> {
            Map<String, ExerciseRef> byId = new HashMap<>();
            for (ExerciseRefRow row : exerciseDao.findRefs(requested)) {
                byId.put(row.id, TemplateMapper.toRef(row));
            }
            List<ExerciseRef> ordered = new ArrayList<>(requested.size());
            for (String id : requested) {
                ExerciseRef ref = byId.get(id);
                if (ref != null) {
                    ordered.add(ref);
                }
            }
            return ordered;
        }, onResult, onError);
    }
}
