package io.github.thiagojosetj.gym.data.repository;

import androidx.annotation.WorkerThread;

import java.time.Clock;

import io.github.thiagojosetj.gym.data.local.AppDatabase;
import io.github.thiagojosetj.gym.data.local.entity.AppMetadataEntity;
import io.github.thiagojosetj.gym.data.local.entity.UserProfileEntity;
import io.github.thiagojosetj.gym.domain.util.IdGenerator;

/**
 * Who owns the data on this device. Before accounts exist (Phase 8) this is a local identity
 * created on first launch; it becomes linked to a server account later (docs/SYNC.md §7).
 */
public final class UserRepository {

    private final AppDatabase database;
    private final Clock clock;
    private final IdGenerator ids;

    public UserRepository(AppDatabase database, Clock clock, IdGenerator ids) {
        this.database = database;
        this.clock = clock;
        this.ids = ids;
    }

    /** Creates the local identity on first launch. Idempotent. Returns the current user id. */
    @WorkerThread
    public String ensureCurrentUser() {
        return database.runInTransaction(() -> {
            String current = database.metadataDao().get(AppMetadataEntity.KEY_CURRENT_USER_ID);
            if (current != null && database.userProfileDao().findById(current) != null) {
                return current;
            }
            UserProfileEntity user = new UserProfileEntity();
            user.id = ids.newId();
            user.isLocal = true;
            user.createdAt = clock.millis();
            user.updatedAt = user.createdAt;
            database.userProfileDao().insert(user);
            database.metadataDao().put(new AppMetadataEntity(AppMetadataEntity.KEY_CURRENT_USER_ID, user.id));
            return user.id;
        });
    }

    /** The current user id; start-up guarantees it exists before any write runs (single disk thread). */
    @WorkerThread
    public String requireCurrentUserId() {
        String current = database.metadataDao().get(AppMetadataEntity.KEY_CURRENT_USER_ID);
        if (current == null) {
            throw new IllegalStateException("No current user: start-up has not run");
        }
        return current;
    }
}
