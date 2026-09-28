package io.github.thiagojosetj.gym.ui.common;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;

/**
 * Navigation that ignores taps the current screen can no longer serve.
 *
 * <p>Two quick taps (two cards, or a card plus the FAB) both run before the first navigation
 * finishes. The second call would then ask the NEW destination to follow an action it does not
 * have, which throws {@code IllegalArgumentException} and crashes the app. Checking the action
 * against the current destination turns that race into a harmless ignored tap.
 */
public final class SafeNavigation {

    private SafeNavigation() {
    }

    public static void navigate(Fragment fragment, @IdRes int actionId, @Nullable android.os.Bundle args) {
        NavController controller = NavHostFragment.findNavController(fragment);
        NavDestination current = controller.getCurrentDestination();
        if (current != null && current.getAction(actionId) != null) {
            controller.navigate(actionId, args);
        }
    }

    public static void navigate(Fragment fragment, @IdRes int actionId) {
        navigate(fragment, actionId, null);
    }

    /**
     * Leaves {@code destinationId}, and only if that is still the screen on top. A screen can be
     * asked to leave from more than one place at once (a dialog dismissed, a database row
     * disappearing); without this check the second request would pop somebody else's screen.
     */
    public static void popFrom(Fragment fragment, @IdRes int destinationId) {
        NavController controller = NavHostFragment.findNavController(fragment);
        NavDestination current = controller.getCurrentDestination();
        if (current != null && current.getId() == destinationId) {
            controller.popBackStack();
        }
    }
}
