package io.github.thiagojosetj.gym.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.github.thiagojosetj.gym.core.AppExecutors;
import io.github.thiagojosetj.gym.data.local.AppDatabase;
import io.github.thiagojosetj.gym.data.local.dao.TemplateDao;
import io.github.thiagojosetj.gym.data.local.entity.SyncStatus;
import io.github.thiagojosetj.gym.data.local.entity.TemplateExerciseEntity;
import io.github.thiagojosetj.gym.data.local.entity.TemplateSetEntity;
import io.github.thiagojosetj.gym.data.local.entity.WorkoutTemplateEntity;
import io.github.thiagojosetj.gym.data.local.row.TemplateSummaryRow;
import io.github.thiagojosetj.gym.domain.template.TemplateDraft;
import io.github.thiagojosetj.gym.domain.template.TemplateOrigin;
import io.github.thiagojosetj.gym.domain.template.TemplateSummary;
import io.github.thiagojosetj.gym.domain.util.IdGenerator;

/** Workout templates of the current user. */
public final class TemplateRepository {

    /** Thrown when a template was deleted or never existed. */
    public static final class TemplateNotFoundException extends Exception {
        public TemplateNotFoundException(String templateId) {
            super("Template not found: " + templateId);
        }
    }

    private final AppDatabase database;
    private final TemplateDao dao;
    private final UserRepository users;
    private final AppExecutors executors;
    private final Clock clock;
    private final IdGenerator ids;

    public TemplateRepository(AppDatabase database, UserRepository users, AppExecutors executors,
                              Clock clock, IdGenerator ids) {
        this.database = database;
        this.dao = database.templateDao();
        this.users = users;
        this.executors = executors;
        this.clock = clock;
        this.ids = ids;
    }

    public LiveData<List<TemplateSummary>> observeTemplates() {
        return Transformations.map(dao.observeActive(), rows -> {
            List<TemplateSummary> result = new ArrayList<>(rows.size());
            for (TemplateSummaryRow row : rows) {
                result.add(TemplateMapper.toSummary(row));
            }
            return result;
        });
    }

    public LiveData<Integer> observeTemplateCount() {
        return dao.observeActiveCount();
    }

    public void loadDraft(String templateId, Consumer<TemplateDraft> onResult, Consumer<Throwable> onError) {
        executors.runOnDisk(() -> loadDraftNow(templateId), onResult, onError);
    }

    /**
     * Saves the draft as one atomic aggregate. The caller must have validated it
     * ({@link TemplateDraft#validate()}); an invalid draft is a programming error.
     *
     * <p>Threading: the draft belongs to the main thread (the editor keeps mutating it), so it is
     * converted into immutable entities <em>here, before</em> hopping to the disk thread. The disk
     * thread never reads the draft itself.
     */
    public void save(TemplateDraft draft, Consumer<String> onSaved, Consumer<Throwable> onError) {
        saveInternal(draft, TemplateOrigin.CREATED, null, onSaved, onError);
    }

    /**
     * Creates an independent copy. Its name is the original's plus {@code copySuffix} (e.g.
     * " (cópia)"), shortened by the domain to respect the name limit. Delivers the new template id.
     */
    public void duplicate(String templateId, String copySuffix, Consumer<String> onCreated,
                          Consumer<Throwable> onError) {
        executors.runOnDisk(() -> {
            TemplateDraft copy = loadDraftNow(templateId).duplicate(copySuffix);
            // Same guard as save(): never persist an aggregate that breaks its own rules.
            if (!copy.validate().isEmpty()) {
                throw new IllegalStateException("Duplicate is invalid: " + copy.validate());
            }
            persist(snapshot(copy), TemplateOrigin.DUPLICATED, templateId);
            return copy.id();
        }, onCreated, onError);
    }

    /** Logical delete: the template disappears from lists; sessions are untouched. */
    public void delete(String templateId, Runnable onDone, Consumer<Throwable> onError) {
        executors.runOnDisk(() -> dao.softDelete(templateId, clock.millis()), ignored -> onDone.run(), onError);
    }

    // ------------------------------------------------------------------ internals

    private void saveInternal(TemplateDraft draft, TemplateOrigin origin, String originTemplateId,
                              Consumer<String> onSaved, Consumer<Throwable> onError) {
        if (!draft.validate().isEmpty()) {
            throw new IllegalArgumentException("Draft is invalid: " + draft.validate());
        }
        Snapshot snapshot = snapshot(draft); // on the caller's (main) thread, see javadoc
        executors.runOnDisk(() -> {
            persist(snapshot, origin, originTemplateId);
            return snapshot.id;
        }, onSaved, onError);
    }

    private TemplateDraft loadDraftNow(String templateId) throws TemplateNotFoundException {
        WorkoutTemplateEntity root = dao.findById(templateId);
        if (root == null) {
            throw new TemplateNotFoundException(templateId);
        }
        return TemplateMapper.toDraft(root, dao.findExercises(templateId), dao.findSets(templateId), ids);
    }

    private void persist(Snapshot s, TemplateOrigin origin, String originTemplateId) {
        database.runInTransaction(() -> {
            long now = clock.millis();
            WorkoutTemplateEntity root;
            if (s.isNew) {
                root = new WorkoutTemplateEntity();
                root.id = s.id;
                root.ownerUserId = users.requireCurrentUserId();
                root.sortOrder = dao.maxSortOrder(root.ownerUserId) + 1;
                root.origin = origin;
                root.originTemplateId = originTemplateId;
                root.createdAt = now;
            } else {
                root = dao.findById(s.id);
                if (root == null) {
                    throw new IllegalStateException(new TemplateNotFoundException(s.id));
                }
            }
            root.name = s.name;
            root.description = s.description;
            root.notes = s.notes;
            root.updatedAt = now;
            root.syncStatus = SyncStatus.PENDING;
            dao.saveAggregate(root, s.isNew, s.exercises, s.sets);
        });
    }

    private static Snapshot snapshot(TemplateDraft draft) {
        return new Snapshot(draft.id(), draft.isNew(), draft.name(), draft.description(), draft.notes(),
                TemplateMapper.toExerciseEntities(draft), TemplateMapper.toSetEntities(draft));
    }

    /** Immutable copy of a draft, safe to hand to the disk thread. */
    private static final class Snapshot {
        final String id;
        final boolean isNew;
        final String name;
        final String description;
        final String notes;
        final List<TemplateExerciseEntity> exercises;
        final List<TemplateSetEntity> sets;

        Snapshot(String id, boolean isNew, String name, String description, String notes,
                 List<TemplateExerciseEntity> exercises, List<TemplateSetEntity> sets) {
            this.id = id;
            this.isNew = isNew;
            this.name = name;
            this.description = description;
            this.notes = notes;
            this.exercises = exercises;
            this.sets = sets;
        }
    }
}
