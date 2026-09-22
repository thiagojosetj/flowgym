package io.github.thiagojosetj.gym.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import io.github.thiagojosetj.gym.BuildConfig;
import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.data.prefs.UiPreferences;
import io.github.thiagojosetj.gym.data.prefs.UiPreferences.ThemeMode;
import io.github.thiagojosetj.gym.databinding.FragmentSettingsBinding;
import io.github.thiagojosetj.gym.ui.common.ViewModelFactories;

/**
 * Device preferences. Only the theme is configurable today; the other settings listed in
 * PRODUCT_SPEC §16 arrive with the features that use them.
 */
public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        UiPreferences preferences = ViewModelFactories.container(this).uiPreferences;
        binding.themeGroup.check(buttonFor(preferences.themeMode()));
        binding.themeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            ThemeMode mode = checkedId == R.id.theme_light ? ThemeMode.LIGHT
                    : checkedId == R.id.theme_dark ? ThemeMode.DARK
                    : ThemeMode.SYSTEM;
            if (mode != preferences.themeMode()) {
                preferences.setThemeMode(mode); // recreates the activity with the new theme
            }
        });
        binding.version.setText(getString(R.string.settings_version, BuildConfig.VERSION_NAME));
    }

    private static int buttonFor(ThemeMode mode) {
        return switch (mode) {
            case LIGHT -> R.id.theme_light;
            case DARK -> R.id.theme_dark;
            case SYSTEM -> R.id.theme_system;
        };
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
