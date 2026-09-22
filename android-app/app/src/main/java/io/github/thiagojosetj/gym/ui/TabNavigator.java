package io.github.thiagojosetj.gym.ui;

import androidx.annotation.IdRes;

/**
 * Lets a screen switch bottom-navigation tabs the same way a tap on the tab would (keeping each
 * tab's back stack), without knowing about the activity's views.
 */
public interface TabNavigator {

    void selectTab(@IdRes int destinationId);
}
