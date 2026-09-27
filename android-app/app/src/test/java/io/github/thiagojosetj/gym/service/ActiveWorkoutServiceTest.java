package io.github.thiagojosetj.gym.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.core.content.ContextCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowService;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.GymApplication;
import io.github.thiagojosetj.gym.domain.library.ExerciseFilter;
import io.github.thiagojosetj.gym.domain.library.ExerciseSummary;
import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.template.ExerciseRef;
import io.github.thiagojosetj.gym.domain.template.TemplateDefaults;
import io.github.thiagojosetj.gym.domain.template.TemplateDraft;
import io.github.thiagojosetj.gym.testutil.LiveDataTestUtil;
import io.github.thiagojosetj.gym.testutil.TestGymApplication;

/**
 * What can honestly be proven about the workout service on the JVM: that it only exists while the
 * database says a workout does, that it promotes itself immediately, and that a notification action
 * changes the session.
 *
 * <p>What Robolectric does NOT enforce - the API 34+ foreground-service type rules - is exactly why
 * there is also a device test, and why the service is written so a failure to start costs only the
 * notification (docs/ROADMAP.md).
 */
@RunWith(AndroidJUnit4.class)
@Config(application = TestGymApplication.class, qualifiers = "pt-rBR")
public class ActiveWorkoutServiceTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AppContainer app;

    @Before
    public void setUp() {
        app = ((GymApplication) ApplicationProvider.getApplicationContext()).container();
    }

    @Test
    public void withNoWorkoutTheServiceStopsItself() {
        ServiceController<ActiveWorkoutService> controller =
                Robolectric.buildService(ActiveWorkoutService.class).create();

        controller.startCommand(0, 0);

        ShadowService shadow = org.robolectric.Shadows.shadowOf(controller.get());
        assertTrue("nothing to show: it must not linger", shadow.isStoppedBySelf());
        controller.destroy();
    }

    @Test
    public void whileAWorkoutRunsItShowsAnOngoingNotification() throws Exception {
        String sessionId = startSession("Push A");

        ServiceController<ActiveWorkoutService> controller =
                Robolectric.buildService(ActiveWorkoutService.class).create();
        controller.startCommand(0, 0);

        ShadowService shadow = org.robolectric.Shadows.shadowOf(controller.get());
        assertEquals(false, shadow.isStoppedBySelf());
        Notification notification = shadow.getLastForegroundNotification();
        assertNotNull("the service must promote itself immediately", notification);

        NotificationManager manager = ContextCompat.getSystemService(
                ApplicationProvider.getApplicationContext(), NotificationManager.class);
        assertNotNull(manager);
        assertNotNull("the channel must exist before posting",
                manager.getNotificationChannel(WorkoutNotifications.CHANNEL_ID));

        // Finishing the session elsewhere takes the service down with it.
        AtomicReference<Boolean> discarded = new AtomicReference<>(false);
        app.activeSessions.discard(sessionId, () -> discarded.set(true), this::fail);
        assertTrue(discarded.get());
        assertTrue(shadow.isStoppedBySelf());
        controller.destroy();
    }

    @Test
    public void theNotificationCanPauseAndResumeTheWorkout() throws Exception {
        String sessionId = startSession("Push A");
        ServiceController<ActiveWorkoutService> controller =
                Robolectric.buildService(ActiveWorkoutService.class).create();
        controller.startCommand(0, 0);

        controller.get().onStartCommand(new Intent(context(), ActiveWorkoutService.class)
                .setAction(ActiveWorkoutService.ACTION_PAUSE), 0, 0);
        assertTrue(LiveDataTestUtil.getOrAwaitValue(
                app.activeSessions.observeActiveHeader()).isPaused());

        controller.get().onStartCommand(new Intent(context(), ActiveWorkoutService.class)
                .setAction(ActiveWorkoutService.ACTION_RESUME), 0, 0);
        assertEquals(false, LiveDataTestUtil.getOrAwaitValue(
                app.activeSessions.observeActiveHeader()).isPaused());

        app.activeSessions.discard(sessionId, () -> {
        }, this::fail);
        controller.destroy();
    }

    // ------------------------------------------------------------------ helpers

    private static Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    private String startSession(String templateName) throws Exception {
        String exerciseId = null;
        for (ExerciseSummary summary
                : LiveDataTestUtil.getOrAwaitValue(app.exercises.observeLibrary(ExerciseFilter.none()))) {
            if (summary.name().equals("Supino reto com barra")) {
                exerciseId = summary.id();
            }
        }
        assertNotNull(exerciseId);
        AtomicReference<List<ExerciseRef>> refs = new AtomicReference<>();
        app.exercises.loadRefs(java.util.Collections.singletonList(exerciseId), refs::set, this::fail);
        TemplateDraft draft = TemplateDraft.newTemplate(app.ids);
        draft.rename(templateName);
        draft.addExercises(refs.get(), new TemplateDefaults(3, RepRange.exactly(12), 30, 90));
        AtomicReference<String> templateId = new AtomicReference<>();
        app.templates.save(draft, templateId::set, this::fail);

        AtomicReference<String> sessionId = new AtomicReference<>();
        app.activeSessions.startFromTemplate(templateId.get(), sessionId::set, this::fail);
        assertNotNull(sessionId.get());
        return sessionId.get();
    }

    private void fail(Throwable error) {
        throw new AssertionError(error);
    }
}
