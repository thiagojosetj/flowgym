package io.github.thiagojosetj.gym.ui.templates;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.databinding.FragmentTemplateListBinding;
import io.github.thiagojosetj.gym.domain.template.TemplateSummary;
import io.github.thiagojosetj.gym.ui.common.SafeNavigation;
import io.github.thiagojosetj.gym.ui.session.ActiveSessionFragment;
import io.github.thiagojosetj.gym.ui.common.ViewModelFactories;
import io.github.thiagojosetj.gym.ui.templates.editor.TemplateEditorFragment;

/**
 * "Meus treinos": list, create, open, duplicate, archive and delete templates.
 *
 * <p>The screen shows one of two lists - the templates in use, or the ones put away. Archiving is
 * the only way a card leaves the list without being deleted, so it is always announced (snackbar
 * with undo) and the other list is always one tap away in the toolbar.
 */
public class TemplateListFragment extends Fragment implements TemplateAdapter.Listener {

    private FragmentTemplateListBinding binding;
    private TemplateListViewModel viewModel;
    /** How many templates are put away, for the toolbar label. Zero until the first read. */
    private int archivedCount;

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
                () -> new TemplateListViewModel(app.templates, app.activeSessions)))
                .get(TemplateListViewModel.class);

        TemplateAdapter adapter = new TemplateAdapter(this, app.executors.diskIO());
        binding.list.setAdapter(adapter);
        binding.fabNew.setOnClickListener(v -> openEditor(null));

        viewModel.templates().observe(getViewLifecycleOwner(), templates -> {
            adapter.submitList(templates);
            binding.emptyState.setVisibility(templates.isEmpty() ? View.VISIBLE : View.GONE);
        });
        viewModel.mode().observe(getViewLifecycleOwner(), this::applyMode);
        viewModel.archivedCount().observe(getViewLifecycleOwner(), count -> {
            archivedCount = count == null ? 0 : count;
            requireActivity().invalidateMenu();
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
                case ALREADY_TRAINING -> R.string.session_already_active;
                case START_FAILED -> R.string.session_start_failed;
            };
            snack(getString(text), null);
        });
        viewModel.archiveChanges().observe(getViewLifecycleOwner(), event -> {
            TemplateListViewModel.ArchiveChange change = event.consume();
            if (change == null) {
                return;
            }
            // The card has just left the list on screen. Say where it went, and offer the way back.
            String text = getString(change.archived()
                    ? R.string.template_archived : R.string.template_unarchived);
            snack(text, change.undoable()
                    ? v -> viewModel.setArchived(change.templateId(), !change.archived(), false)
                    : null);
        });

        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.menu_template_list, menu);
            }

            @Override
            public void onPrepareMenu(@NonNull Menu menu) {
                MenuItem item = menu.findItem(R.id.action_toggle_archived);
                if (item == null) {
                    return;
                }
                boolean archived = viewModel.showingArchived();
                // Nothing put away and nothing to go back to: no entry at all. The entry appears
                // the moment there is something behind it, which is the moment it is needed.
                item.setVisible(archived || archivedCount > 0);
                item.setTitle(archived
                        ? getString(R.string.templates_active_menu)
                        : getResources().getQuantityString(R.plurals.templates_archived_menu,
                                archivedCount, archivedCount));
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() == R.id.action_toggle_archived) {
                    viewModel.showArchived(!viewModel.showingArchived());
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        viewModel.startedSession().observe(getViewLifecycleOwner(), event -> {
            String sessionId = event.consume();
            if (sessionId != null) {
                SafeNavigation.navigate(this, R.id.action_templates_to_session,
                        ActiveSessionFragment.args(sessionId));
            }
        });
    }

    @Override
    public void onOpen(TemplateSummary template) {
        openEditor(template.id());
    }

    @Override
    public void onMore(TemplateSummary template, View anchor) {
        boolean archived = viewModel.showingArchived();
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.inflate(R.menu.menu_template_item);
        Menu menu = popup.getMenu();
        // An archived template is out of the rotation: to train it, edit it or copy it, bring it
        // back first. Offering "duplicar" here would create an ACTIVE copy while the user is
        // looking at the archived list, and the copy would appear nowhere - exactly the silent
        // disappearance archiving exists to avoid.
        menu.findItem(R.id.action_start).setVisible(!archived);
        menu.findItem(R.id.action_edit).setVisible(!archived);
        menu.findItem(R.id.action_duplicate).setVisible(!archived);
        menu.findItem(R.id.action_archive).setVisible(!archived);
        menu.findItem(R.id.action_unarchive).setVisible(archived);
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_start) {
                viewModel.startSession(template.id());
            } else if (id == R.id.action_edit) {
                openEditor(template.id());
            } else if (id == R.id.action_duplicate) {
                viewModel.duplicate(template.id(), getString(R.string.template_copy_suffix));
            } else if (id == R.id.action_archive) {
                viewModel.setArchived(template.id(), true, true);
            } else if (id == R.id.action_unarchive) {
                viewModel.setArchived(template.id(), false, true);
            } else if (id == R.id.action_delete) {
                confirmDelete(template);
            } else {
                return false;
            }
            return true;
        });
        popup.show();
    }

    /** Swaps the chrome between the two lists: banner, FAB and the words of the empty state. */
    private void applyMode(TemplateListViewModel.Mode mode) {
        boolean archived = mode == TemplateListViewModel.Mode.ARCHIVED;
        binding.archivedBanner.setVisibility(archived ? View.VISIBLE : View.GONE);
        // "Novo treino" would land in the list the user is not looking at.
        binding.fabNew.setVisibility(archived ? View.GONE : View.VISIBLE);
        binding.emptyTitle.setText(archived
                ? R.string.templates_archived_empty_title : R.string.templates_empty_title);
        binding.emptyBody.setText(archived
                ? R.string.templates_archived_empty_body : R.string.templates_empty_body);
        requireActivity().invalidateMenu();
    }

    /** One snackbar helper: the FAB is the anchor, and it is gone on the archived list. */
    private void snack(String text, @Nullable View.OnClickListener undo) {
        Snackbar bar = Snackbar.make(binding.getRoot(), text,
                undo == null ? Snackbar.LENGTH_SHORT : Snackbar.LENGTH_LONG);
        if (binding.fabNew.getVisibility() == View.VISIBLE) {
            bar.setAnchorView(binding.fabNew);
        }
        if (undo != null) {
            bar.setAction(R.string.action_undo, undo);
        }
        bar.show();
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
        SafeNavigation.navigate(this, R.id.action_templates_to_editor, TemplateEditorFragment.args(templateId));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
