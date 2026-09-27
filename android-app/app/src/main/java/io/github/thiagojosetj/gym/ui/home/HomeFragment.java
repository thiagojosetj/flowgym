package io.github.thiagojosetj.gym.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.databinding.FragmentHomeBinding;
import io.github.thiagojosetj.gym.ui.TabNavigator;
import io.github.thiagojosetj.gym.ui.common.SafeNavigation;
import io.github.thiagojosetj.gym.ui.common.ViewModelFactories;
import io.github.thiagojosetj.gym.ui.session.ActiveSessionFragment;
import io.github.thiagojosetj.gym.ui.templates.editor.TemplateEditorFragment;

/** Start screen: what to train next and shortcuts to templates and the library. */
public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        AppContainer app = ViewModelFactories.container(this);
        HomeViewModel viewModel = new ViewModelProvider(this,
                ViewModelFactories.of(HomeViewModel.class,
                        () -> new HomeViewModel(app.templates, app.activeSessions)))
                .get(HomeViewModel.class);

        viewModel.templateCount().observe(getViewLifecycleOwner(), count -> {
            int value = count == null ? 0 : count;
            binding.templatesCount.setText(value == 0
                    ? getString(R.string.home_templates_empty)
                    : getResources().getQuantityString(R.plurals.home_templates_count, value, value));
        });

        viewModel.activeSession().observe(getViewLifecycleOwner(), session -> {
            binding.sessionBanner.setVisibility(session == null ? View.GONE : View.VISIBLE);
            if (session != null) {
                binding.bannerName.setText(session.name());
                binding.bannerAction.setOnClickListener(v -> SafeNavigation.navigate(this,
                        R.id.action_home_to_session, ActiveSessionFragment.args(session.id())));
            }
        });

        binding.buttonTemplates.setOnClickListener(v -> tabs().selectTab(R.id.templateListFragment));
        binding.buttonLibrary.setOnClickListener(v -> tabs().selectTab(R.id.exerciseLibraryFragment));
        binding.buttonCreateTemplate.setOnClickListener(v ->
                SafeNavigation.navigate(this, R.id.action_home_to_editor, TemplateEditorFragment.args(null)));

        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.menu_home, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() == R.id.action_settings) {
                    SafeNavigation.navigate(HomeFragment.this, R.id.action_home_to_settings);
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    private TabNavigator tabs() {
        return (TabNavigator) requireActivity();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
