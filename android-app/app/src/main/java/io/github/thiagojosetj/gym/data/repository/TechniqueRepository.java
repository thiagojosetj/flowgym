package io.github.thiagojosetj.gym.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.github.thiagojosetj.gym.core.AppExecutors;
import io.github.thiagojosetj.gym.data.local.dao.TechniqueDao;
import io.github.thiagojosetj.gym.data.local.entity.TrainingTechniqueEntity;
import io.github.thiagojosetj.gym.domain.technique.TechniqueCatalog;
import io.github.thiagojosetj.gym.domain.technique.TechniqueScope;
import io.github.thiagojosetj.gym.domain.technique.TrainingTechnique;

/** Training techniques (PRODUCT_SPEC §6.2). Content comes from the seeded catalog. */
public final class TechniqueRepository {

    private final TechniqueDao dao;
    private final AppExecutors executors;

    public TechniqueRepository(TechniqueDao dao, AppExecutors executors) {
        this.dao = dao;
        this.executors = executors;
    }

    /** Everything available, so the UI can also explain exercise- and group-level methods later. */
    public LiveData<TechniqueCatalog> observeCatalog() {
        return Transformations.map(dao.observeVisible(), TechniqueRepository::toCatalog);
    }

    /** Only the techniques that can be attached to a single set (warm-up, drop-set…). */
    public LiveData<List<TrainingTechnique>> observeSetTechniques() {
        return Transformations.map(dao.observeVisible(), entities -> {
            List<TrainingTechnique> result = new ArrayList<>();
            for (TrainingTechniqueEntity entity : entities) {
                if (entity.scope == TechniqueScope.SET) {
                    result.add(toDomain(entity));
                }
            }
            return result;
        });
    }

    public void loadCatalog(Consumer<TechniqueCatalog> onResult, Consumer<Throwable> onError) {
        executors.runOnDisk(() -> toCatalog(dao.findVisible()), onResult, onError);
    }

    private static TechniqueCatalog toCatalog(List<TrainingTechniqueEntity> entities) {
        List<TrainingTechnique> techniques = new ArrayList<>(entities.size());
        for (TrainingTechniqueEntity entity : entities) {
            techniques.add(toDomain(entity));
        }
        return new TechniqueCatalog(techniques);
    }

    private static TrainingTechnique toDomain(TrainingTechniqueEntity entity) {
        return new TrainingTechnique(entity.id, entity.code, entity.name, entity.scope,
                entity.countsAsWorkingSet, entity.description, entity.instructions);
    }
}
