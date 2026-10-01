package io.github.thiagojosetj.gym.data.repository;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.github.thiagojosetj.gym.core.AppExecutors;
import io.github.thiagojosetj.gym.data.local.AppDatabase;
import io.github.thiagojosetj.gym.data.local.dao.TemplateDao;
import io.github.thiagojosetj.gym.data.local.entity.SyncStatus;
import io.github.thiagojosetj.gym.data.local.entity.TemplateExerciseEntity;
import io.github.thiagojosetj.gym.data.local.entity.TemplateExerciseGroupEntity;
import io.github.thiagojosetj.gym.data.local.entity.TemplateSetEntity;
import io.github.thiagojosetj.gym.data.local.entity.TrainingTechniqueEntity;
import io.github.thiagojosetj.gym.data.local.entity.WorkoutTemplateEntity;
import io.github.thiagojosetj.gym.data.local.row.TemplateGroupRow;
import io.github.thiagojosetj.gym.data.local.row.TemplateSummaryRow;
import io.github.thiagojosetj.gym.domain.technique.TechniqueScope;
import io.github.thiagojosetj.gym.domain.template.ExerciseGroup;
import io.github.thiagojosetj.gym.domain.template.TemplateDraft;
import io.github.thiagojosetj.gym.domain.template.TemplateOrigin;
import io.github.thiagojosetj.gym.domain.template.TemplateRules;
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
            // One transaction, so a copy that exists has its groups too and never half of them.
            database.runInTransaction(() -> {
                persist(snapshot(copy), TemplateOrigin.DUPLICATED, templateId);
                copyGroups(templateId, copy);
            });
            return copy.id();
        }, onCreated, onError);
    }

    /** Logical delete: the template disappears from lists; sessions are untouched. */
    public void delete(String templateId, Runnable onDone, Consumer<Throwable> onError) {
        executors.runOnDisk(() -> dao.softDelete(templateId, clock.millis()), ignored -> onDone.run(), onError);
    }

    // ------------------------------------------------------------------ groups (PRODUCT_SPEC 6.3)

    /**
     * Which group each exercise of the template is in, keyed by template exercise id and listed in
     * the order of the exercises. An exercise that stands alone has no entry. The editor writes A1
     * and A2 by walking its exercises: the label of the group, then how many of them it has seen.
     *
     * <p>Beside {@link #loadDraft}, not inside it: the draft is the plan the editor mutates, and a
     * group is not part of that plan - it is created and removed here, immediately, on exercises
     * that are already saved.
     */
    public void loadGroups(String templateId, Consumer<Map<String, ExerciseGroup>> onResult,
                           Consumer<Throwable> onError) {
        executors.runOnDisk(() -> {
            requireTemplate(templateId);
            return TemplateMapper.toGroupsByExercise(dao.findExerciseEntities(templateId),
                    dao.findGroups(templateId));
        }, onResult, onError);
    }

    /**
     * Groups exercises of a saved template (superset, bi-set, tri-set, giant set). Written at once,
     * in one transaction, and the template is marked for sync like any other change to it.
     *
     * <p>The label ("A", "B"...) is not an input. Groups are lettered in the order they appear in
     * the template, and the whole template is relettered whenever that changes, so a new group over
     * the first exercises becomes A and the old A becomes B.
     *
     * @param templateExerciseIds the {@code template_exercise} ids to put together: at least
     *                            {@link TemplateRules#MIN_GROUP_SIZE}, all of this template, and
     *                            none in a group already. A repeated id counts once.
     * @param techniqueId         a group-scope technique (SS, BI, TRI, GS), or null for a plain
     *                            grouping. Nothing checks it against the number of exercises: that
     *                            is the editor's suggestion to make, not a rule of the data.
     * @param restAfterRoundSeconds rest that starts when the ROUND is over, 0 for none
     * @param onCreated           receives the id of the new group
     * @param onError             receives {@link TemplateNotFoundException}, or
     *                            {@link IllegalArgumentException} when an argument breaks a rule
     *                            above; nothing is written in either case
     */
    public void createGroup(String templateId, List<String> templateExerciseIds,
                            @Nullable String techniqueId, int restAfterRoundSeconds,
                            Consumer<String> onCreated, Consumer<Throwable> onError) {
        // Copied here, on the caller's thread: the list may belong to the screen that built it.
        List<String> members = new ArrayList<>(new LinkedHashSet<>(templateExerciseIds));
        executors.runOnDisk(() -> {
            requireTemplate(templateId);
            requireValidGroupDetails(techniqueId, restAfterRoundSeconds);
            requireFreeExercisesOf(templateId, members);
            TemplateExerciseGroupEntity group = new TemplateExerciseGroupEntity();
            group.id = ids.newId();
            group.templateId = templateId;
            group.techniqueId = techniqueId;
            group.restAfterRoundSeconds = restAfterRoundSeconds;
            dao.addGroup(group, members, clock.millis());
            return group.id;
        }, onCreated, onError);
    }

    /**
     * Changes the technique and the rest of a group; who is in it and its label do not move. An
     * unknown group changes nothing and still reports done: the screen may be a step behind.
     * Sessions already started are untouched, they hold their own copy.
     */
    public void updateGroup(String templateId, String groupId, @Nullable String techniqueId,
                            int restAfterRoundSeconds, Runnable onDone,
                            Consumer<Throwable> onError) {
        executors.runOnDisk(() -> {
            requireTemplate(templateId);
            requireValidGroupDetails(techniqueId, restAfterRoundSeconds);
            if (dao.findGroup(templateId, groupId) != null) {
                dao.updateGroup(templateId, groupId, techniqueId, restAfterRoundSeconds,
                        clock.millis());
            }
            return null;
        }, ignored -> onDone.run(), onError);
    }

    /**
     * Ungroups: deletes the group and nothing else. Its exercises stay in the template with no
     * group - the foreign key sets their group_id to null instead of deleting them with it - and
     * the groups that are left are relettered. An unknown group changes nothing and still reports
     * done, so a double tap is not an error. Sessions already started keep their copy.
     */
    public void removeGroup(String templateId, String groupId, Runnable onDone,
                            Consumer<Throwable> onError) {
        executors.runOnDisk(() -> {
            requireTemplate(templateId);
            if (dao.findGroup(templateId, groupId) != null) {
                dao.removeGroup(templateId, groupId, clock.millis());
            }
            return null;
        }, ignored -> onDone.run(), onError);
    }

    // ------------------------------------------------------------------ internals

    private void requireTemplate(String templateId) throws TemplateNotFoundException {
        if (dao.findById(templateId) == null) {
            throw new TemplateNotFoundException(templateId);
        }
    }

    private void requireValidGroupDetails(@Nullable String techniqueId, int restAfterRoundSeconds) {
        if (restAfterRoundSeconds < 0 || restAfterRoundSeconds > TemplateRules.MAX_REST_SECONDS) {
            throw new IllegalArgumentException("Rest after the round out of range: "
                    + restAfterRoundSeconds);
        }
        if (techniqueId == null) {
            return;
        }
        // A set-scope badge such as AQ on a superset would be copied into every session started
        // from it, so the scope is checked here instead of trusting every caller to filter.
        TrainingTechniqueEntity technique = dao.findVisibleTechnique(techniqueId);
        if (technique == null || technique.scope != TechniqueScope.GROUP) {
            throw new IllegalArgumentException("Not a group technique: " + techniqueId);
        }
    }

    private void requireFreeExercisesOf(String templateId, List<String> members) {
        if (members.size() < TemplateRules.MIN_GROUP_SIZE) {
            throw new IllegalArgumentException("A group needs at least "
                    + TemplateRules.MIN_GROUP_SIZE + " different exercises, got " + members.size());
        }
        Map<String, TemplateExerciseEntity> inTemplate = new HashMap<>();
        for (TemplateExerciseEntity exercise : dao.findExerciseEntities(templateId)) {
            inTemplate.put(exercise.id, exercise);
        }
        for (String id : members) {
            TemplateExerciseEntity exercise = inTemplate.get(id);
            if (exercise == null) {
                throw new IllegalArgumentException("Not an exercise of template " + templateId
                        + ": " + id);
            }
            if (exercise.groupId != null) {
                // Moving it would quietly shrink, and maybe dissolve, the group it is leaving.
                throw new IllegalArgumentException("Already in a group: " + id);
            }
        }
    }

    /**
     * The draft carries no groups, so a copy built from it comes out with every superset undone.
     * They are copied here, onto the copy's own exercises: the i-th exercise of the original became
     * the i-th of the copy, because {@link TemplateDraft#duplicate} keeps the order.
     */
    private void copyGroups(String sourceTemplateId, TemplateDraft copy) {
        List<TemplateGroupRow> groups = dao.findGroups(sourceTemplateId);
        if (groups.isEmpty()) {
            return;
        }
        List<TemplateExerciseEntity> original = dao.findExerciseEntities(sourceTemplateId);
        if (original.size() != copy.exercises().size()) {
            throw new IllegalStateException("The copy has " + copy.exercises().size()
                    + " exercises and the original " + original.size());
        }
        long now = clock.millis();
        for (TemplateGroupRow source : groups) {
            List<String> members = new ArrayList<>();
            for (int i = 0; i < original.size(); i++) {
                if (source.id.equals(original.get(i).groupId)) {
                    members.add(copy.exercises().get(i).id());
                }
            }
            TemplateExerciseGroupEntity group = new TemplateExerciseGroupEntity();
            group.id = ids.newId();
            group.templateId = copy.id();
            group.techniqueId = source.techniqueId;
            group.restAfterRoundSeconds = source.restAfterRoundSeconds;
            dao.addGroup(group, members, now);
        }
    }

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
