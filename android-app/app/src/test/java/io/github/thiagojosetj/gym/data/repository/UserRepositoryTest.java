package io.github.thiagojosetj.gym.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.github.thiagojosetj.gym.data.local.AppDatabase;
import io.github.thiagojosetj.gym.data.local.entity.UserProfileEntity;
import io.github.thiagojosetj.gym.domain.util.IdGenerator;
import io.github.thiagojosetj.gym.testutil.TestContainers;

@RunWith(AndroidJUnit4.class)
public class UserRepositoryTest {

    private AppDatabase database;
    private UserRepository users;

    @Before
    public void setUp() {
        database = TestContainers.inMemoryDatabase();
        users = new UserRepository(database, TestContainers.FIXED_CLOCK, IdGenerator.UUID_V7);
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void createsOneLocalIdentityAndReusesIt() {
        String first = users.ensureCurrentUser();
        String second = users.ensureCurrentUser();

        assertEquals(first, second);
        assertEquals(first, users.requireCurrentUserId());
        UserProfileEntity profile = database.userProfileDao().findById(first);
        assertNotNull(profile);
        assertTrue(profile.isLocal);
        assertEquals(TestContainers.FIXED_CLOCK.millis(), profile.createdAt);
    }
}
