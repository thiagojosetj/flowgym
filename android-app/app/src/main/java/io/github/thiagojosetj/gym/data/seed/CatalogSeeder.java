package io.github.thiagojosetj.gym.data.seed;

import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.thiagojosetj.gym.data.local.AppDatabase;
import io.github.thiagojosetj.gym.data.local.entity.AppMetadataEntity;
import io.github.thiagojosetj.gym.data.local.entity.EquipmentEntity;
import io.github.thiagojosetj.gym.data.local.entity.ExerciseEntity;
import io.github.thiagojosetj.gym.data.local.entity.ExerciseEquipmentEntity;
import io.github.thiagojosetj.gym.data.local.entity.ExerciseMuscleEntity;
import io.github.thiagojosetj.gym.data.local.entity.MuscleEntity;
import io.github.thiagojosetj.gym.data.local.entity.SyncStatus;
import io.github.thiagojosetj.gym.domain.model.Laterality;
import io.github.thiagojosetj.gym.domain.model.LoadBasis;
import io.github.thiagojosetj.gym.domain.model.MuscleRole;
import io.github.thiagojosetj.gym.domain.model.TrackingType;
import io.github.thiagojosetj.gym.domain.util.TextNormalizer;

/**
 * Loads the bundled system catalog (assets/catalog/catalog.json) into the database (ADR-0016).
 *
 * <p>Runs on the disk thread at start-up. It only does work when the bundled catalog version is
 * newer than the one recorded in {@code app_metadata}, so normal launches cost one tiny query.
 */
public final class CatalogSeeder {

    public static final String ASSET_PATH = "catalog/catalog.json";

    /** Must equal "version" in the bundled JSON (enforced by CatalogSeederTest). Bump both together. */
    public static final int BUNDLED_VERSION = 1;

    /** Where the JSON comes from: app assets in production, test resources in tests. */
    @FunctionalInterface
    public interface Source {
        InputStream open() throws IOException;
    }

    private final AppDatabase database;
    private final Source source;
    private final Clock clock;

    public CatalogSeeder(AppDatabase database, Source source, Clock clock) {
        this.database = database;
        this.source = source;
        this.clock = clock;
    }

    /** @return true when the catalog was (re)written */
    @WorkerThread
    public boolean seedIfNeeded() throws IOException, JSONException {
        String stored = database.metadataDao().get(AppMetadataEntity.KEY_CATALOG_VERSION);
        int storedVersion = stored == null ? 0 : Integer.parseInt(stored);
        if (storedVersion >= BUNDLED_VERSION) {
            return false;
        }
        Parsed catalog = parse(readUtf8(source), clock.millis());
        if (catalog.version != BUNDLED_VERSION) {
            throw new IllegalStateException("catalog.json version " + catalog.version
                    + " != BUNDLED_VERSION " + BUNDLED_VERSION);
        }
        database.runInTransaction(() -> {
            database.catalogDao().replaceSystemCatalog(catalog.muscles, catalog.equipment, catalog.exercises,
                    catalog.exerciseMuscles, catalog.exerciseEquipment, catalog.now);
            database.metadataDao().put(new AppMetadataEntity(
                    AppMetadataEntity.KEY_CATALOG_VERSION, Integer.toString(catalog.version)));
        });
        return true;
    }

    /** Parsed catalog, ready to insert. Muscles are ordered parents-first. */
    @VisibleForTesting
    static final class Parsed {
        int version;
        long now;
        final List<MuscleEntity> muscles = new ArrayList<>();
        final List<EquipmentEntity> equipment = new ArrayList<>();
        final List<ExerciseEntity> exercises = new ArrayList<>();
        final List<ExerciseMuscleEntity> exerciseMuscles = new ArrayList<>();
        final List<ExerciseEquipmentEntity> exerciseEquipment = new ArrayList<>();
    }

    @VisibleForTesting
    static Parsed parse(String json, long now) throws JSONException {
        JSONObject root = new JSONObject(json);
        Parsed out = new Parsed();
        out.version = root.getInt("version");
        out.now = now;

        Map<String, String> muscleIdByCode = new HashMap<>();
        int order = 0;
        JSONArray groups = root.getJSONArray("muscles");
        for (int g = 0; g < groups.length(); g++) {
            JSONObject group = groups.getJSONObject(g);
            MuscleEntity groupEntity = muscle(group, null, order++);
            out.muscles.add(groupEntity);
            muscleIdByCode.put(groupEntity.code, groupEntity.id);
            JSONArray subgroups = group.optJSONArray("subgroups");
            for (int s = 0; subgroups != null && s < subgroups.length(); s++) {
                MuscleEntity sub = muscle(subgroups.getJSONObject(s), groupEntity.id, order++);
                out.muscles.add(sub);
                muscleIdByCode.put(sub.code, sub.id);
            }
        }

        Map<String, String> equipmentIdByCode = new HashMap<>();
        JSONArray equipment = root.getJSONArray("equipment");
        for (int i = 0; i < equipment.length(); i++) {
            JSONObject item = equipment.getJSONObject(i);
            EquipmentEntity entity = new EquipmentEntity();
            entity.id = item.getString("id");
            entity.code = item.getString("code");
            entity.name = item.getString("name");
            entity.sortOrder = i;
            out.equipment.add(entity);
            equipmentIdByCode.put(entity.code, entity.id);
        }

        JSONArray exercises = root.getJSONArray("exercises");
        for (int i = 0; i < exercises.length(); i++) {
            JSONObject item = exercises.getJSONObject(i);
            ExerciseEntity e = new ExerciseEntity();
            e.id = item.getString("id");
            e.ownerUserId = null;
            e.code = item.getString("code");
            e.name = item.getString("name");
            List<String> aliases = strings(item.optJSONArray("aliases"));
            e.aliases = aliases.isEmpty() ? null : String.join(", ", aliases);
            e.searchText = TextNormalizer.normalize(e.name + " " + String.join(" ", aliases));
            e.description = item.optString("description", null);
            List<String> steps = strings(item.optJSONArray("instructions"));
            e.instructions = steps.isEmpty() ? null : String.join("\n", steps);
            e.tips = item.optString("tips", null);
            e.commonMistakes = item.optString("commonMistakes", null);
            e.trackingType = TrackingType.valueOf(item.optString("tracking", TrackingType.WEIGHT_REPS.name()));
            e.loadBasis = LoadBasis.valueOf(item.optString("loadBasis", LoadBasis.TOTAL.name()));
            e.implementCount = item.optInt("implements", 1);
            e.laterality = Laterality.valueOf(item.optString("laterality", Laterality.BILATERAL.name()));
            e.isActive = true;
            e.createdAt = now;
            e.updatedAt = now;
            e.syncStatus = SyncStatus.SYNCED; // server-authoritative catalog: never uploaded
            out.exercises.add(e);

            Set<String> linked = new HashSet<>();
            int sort = 0;
            for (String code : strings(item.getJSONArray("primary"))) {
                out.exerciseMuscles.add(link(e, code, MuscleRole.PRIMARY, sort++, muscleIdByCode, linked));
            }
            for (String code : strings(item.optJSONArray("secondary"))) {
                out.exerciseMuscles.add(link(e, code, MuscleRole.SECONDARY, sort++, muscleIdByCode, linked));
            }
            List<String> equipmentCodes = strings(item.optJSONArray("equipment"));
            for (int k = 0; k < equipmentCodes.size(); k++) {
                String equipmentId = equipmentIdByCode.get(equipmentCodes.get(k));
                if (equipmentId == null) {
                    throw new JSONException("Unknown equipment '" + equipmentCodes.get(k) + "' in " + e.code);
                }
                out.exerciseEquipment.add(new ExerciseEquipmentEntity(e.id, equipmentId, k == 0));
            }
        }
        return out;
    }

    private static MuscleEntity muscle(JSONObject item, String parentId, int order) throws JSONException {
        MuscleEntity entity = new MuscleEntity();
        entity.id = item.getString("id");
        entity.code = item.getString("code");
        entity.name = item.getString("name");
        entity.parentId = parentId;
        entity.sortOrder = order;
        return entity;
    }

    private static ExerciseMuscleEntity link(ExerciseEntity exercise, String muscleCode, MuscleRole role, int sort,
                                             Map<String, String> muscleIdByCode, Set<String> linked)
            throws JSONException {
        String muscleId = muscleIdByCode.get(muscleCode);
        if (muscleId == null) {
            throw new JSONException("Unknown muscle '" + muscleCode + "' in " + exercise.code);
        }
        if (!linked.add(muscleId)) {
            throw new JSONException("Muscle '" + muscleCode + "' linked twice in " + exercise.code);
        }
        return new ExerciseMuscleEntity(exercise.id, muscleId, role, sort);
    }

    private static List<String> strings(JSONArray array) throws JSONException {
        List<String> result = new ArrayList<>();
        for (int i = 0; array != null && i < array.length(); i++) {
            result.add(array.getString(i));
        }
        return result;
    }

    private static String readUtf8(Source source) throws IOException {
        try (InputStream in = source.open(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
