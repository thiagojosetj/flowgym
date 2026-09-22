package io.github.thiagojosetj.gym.ui.common;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.util.function.Supplier;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.GymApplication;

/**
 * Builds ViewModels with constructor arguments (manual DI, ADR-0005).
 *
 * <p>Android creates ViewModels through a factory so it can keep the same instance across
 * rotations. Our factory simply calls a constructor we choose, passing repositories from the
 * {@link AppContainer}.
 */
public final class ViewModelFactories {

    private ViewModelFactories() {
    }

    public static <VM extends ViewModel> ViewModelProvider.Factory of(Class<VM> type, Supplier<VM> creator) {
        return new ViewModelProvider.Factory() {
            @NonNull
            @Override
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                if (!modelClass.isAssignableFrom(type)) {
                    throw new IllegalArgumentException("Unexpected ViewModel " + modelClass.getName());
                }
                return modelClass.cast(creator.get());
            }
        };
    }

    /** The app's composition root, reachable from any fragment. */
    public static AppContainer container(Fragment fragment) {
        return ((GymApplication) fragment.requireContext().getApplicationContext()).container();
    }
}
