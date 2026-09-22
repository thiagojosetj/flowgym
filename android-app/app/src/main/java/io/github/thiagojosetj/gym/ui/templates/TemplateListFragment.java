package io.github.thiagojosetj.gym.ui.templates;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.databinding.FragmentTemplateListBinding;
import io.github.thiagojosetj.gym.domain.template.TemplateSummary;
import io.github.thiagojosetj.gym.ui.common.ViewModelFactories;
import io.github.thiagojosetj.gym.ui.templates.editor.TemplateEditorFragment;

/** "Meus treinos": list, create, open, duplicate and delete templates. */
public class TemplateListFragment extends Fragment implements TemplateAdapter.Listener {

    private FragmentTemplateListBinding binding;
    private TemplateListViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTemplateListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        AppContainer app = ViewModelFactories.container(this);
        viewModel = new ViewModelProvider(this, ViewModelFactories.of(TemplateListViewModel.class,
                () -> new TemplateListViewModel(app.templates))).get(TemplateListViewModel.class);

        TemplateAdapter adapter = new TemplateAdapter(this);
        binding.list.setAdapter(adapter);
        binding.fabNew.setOnClickListener(v -> openEditor(null));

        viewModel.templates().observe(getViewLifecycleOwner(), templates -> {
            adapter.submitList(templates);
            binding.emptyState.setVisibility(templates.isEmpty() ? View.VISIBLE : View.GONE);
        });
        viewModel.messages().observe(getViewLifecycleOwner(), event -> {
            TemplateListViewModel.Message message = event.consume();
            if (message == null) {
                return;
            }
            int text = switch (message) {
                case DUPLICATED -> R.string.template_duplicated;
                case DELETED -> R.string.template_deleted;
                case FAILED -> R.string.template_operation_failed;
            };
            Snackbar.make(binding.getRoot(), text, Snackbar.LENGTH_SHORT).setAnchorView(binding.fabNew).show();
        });
    }

    @Override
    public void onOpen(TemplateSummary template) {
        openEditor(template.id());
    }

    @Override
    public void onMore(TemplateSummary template, View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.inflate(R.menu.menu_template_item);
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_edit) {
                openEditor(template.id());
            } else if (id == R.id.action_duplicate) {
                viewModel.duplicate(template.id(), getString(R.string.template_copy_name, template.name()));
            } else if (id == R.id.action_delete) {
                confirmDelete(template);
            } else {
                return false;
            }
            return true;
        });
        popup.show();
    }

    private void confirmDelete(TemplateSummary template) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.template_delete_title)
                .setMessage(getString(R.string.template_delete_message, template.name()))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> viewModel.delete(template.id()))
                .show();
    }

    private void openEditor(@Nullable String templateId) {
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_templates_to_editor, TemplateEditorFragment.args(templateId));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
