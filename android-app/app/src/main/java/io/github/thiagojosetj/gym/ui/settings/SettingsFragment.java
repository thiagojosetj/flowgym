package io.github.thiagojosetj.gym.ui.settings;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.chip.Chip;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.BuildConfig;
import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.data.prefs.UiPreferences;
import io.github.thiagojosetj.gym.data.prefs.UiPreferences.ThemeMode;
import io.github.thiagojosetj.gym.databinding.FragmentSettingsBinding;
import io.github.thiagojosetj.gym.domain.settings.AppSettings;
import io.github.thiagojosetj.gym.domain.template.TemplateRules;
import io.github.thiagojosetj.gym.ui.common.NumberInput;
import io.github.thiagojosetj.gym.ui.common.ViewModelFactories;

/**
 * Preferences. The theme belongs to this device (SharedPreferences); the workout values belong to the
 * account and live in the database, so they will follow the user to another phone (docs/DATABASE.md).
 */
public class SettingsFragment extends Fragment {

    private static final int[] REST_SHORTCUTS = {60, 90, 120, 180};

    private FragmentSettingsBinding binding;
    private SettingsViewModel viewModel;
    /** True while fields are filled from the stored settings, so watchers don't write them back. */
    private boolean updatingViews;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        AppContainer app = ViewModelFactories.container(this);
        viewModel = new ViewModelProvider(this, ViewModelFactories.of(SettingsViewModel.class,
                () -> new SettingsViewModel(app.settings))).get(SettingsViewModel.class);

        setUpTheme(app.uiPreferences);
        setUpWorkoutSettings();
        binding.version.setText(getString(R.string.settings_version, BuildConfig.VERSION_NAME));

        viewModel.settings().observe(getViewLifecycleOwner(), this::render);
    }

    private void setUpTheme(UiPreferences preferences) {
        binding.themeGroup.check(buttonFor(preferences.themeMode()));
        binding.themeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            ThemeMode mode = checkedId == R.id.theme_light ? ThemeMode.LIGHT
                    : checkedId == R.id.theme_dark ? ThemeMode.DARK
                    : ThemeMode.SYSTEM;
            if (mode != preferences.themeMode()) {
                preferences.setThemeMode(mode); // recreates the activity with the new theme
            }
        });
    }

    private void setUpWorkoutSettings() {
        for (int seconds : REST_SHORTCUTS) {
            Chip chip = new Chip(requireContext());
            chip.setText(getString(R.string.duration_seconds, seconds));
            chip.setOnClickListener(v -> binding.restInput.setText(String.valueOf(seconds)));
            binding.restChips.addView(chip);
        }
        binding.restInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (updatingViews) {
                    return;
                }
                saveRestSeconds(s.toString());
            }
        });
        binding.switchSound.setOnCheckedChangeListener((button, checked) -> {
            if (!updatingViews) {
                viewModel.setRestSoundEnabled(checked);
            }
        });
        binding.switchVibration.setOnCheckedChangeListener((button, checked) -> {
            if (!updatingViews) {
                viewModel.setRestVibrationEnabled(checked);
            }
        });
    }

    private void saveRestSeconds(String text) {
        Integer seconds;
        try {
            seconds = NumberInput.parseWholeNumber(text);
        } catch (NumberFormatException e) {
            seconds = null;
        }
        if (seconds == null || seconds > TemplateRules.MAX_REST_SECONDS) {
            // Blank counts as invalid here: an empty field is a moment of typing, not a value.
            binding.restLayout.setError(getString(R.string.settings_rest_invalid, TemplateRules.MAX_REST_SECONDS));
            return;
        }
        binding.restLayout.setError(null);
        viewModel.setDefaultRestSeconds(seconds);
    }

    private void render(AppSettings settings) {
        updatingViews = true;
        String rest = String.valueOf(settings.defaultRestSeconds());
        if (!rest.equals(text(binding.restInput.getText()))) {
            binding.restInput.setText(rest);
        }
        binding.switchSound.setChecked(settings.restSoundEnabled());
        binding.switchVibration.setChecked(settings.restVibrationEnabled());
        updatingViews = false;
    }

    private static String text(@Nullable CharSequence value) {
        return value == null ? "" : value.toString();
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
