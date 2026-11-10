package io.github.thiagojosetj.gym.ui.templates.editor;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.databinding.FragmentTemplateEditorBinding;
import io.github.thiagojosetj.gym.domain.model.WeightUnit;
import io.github.thiagojosetj.gym.domain.template.TemplateDraft;
import io.github.thiagojosetj.gym.domain.template.TemplateRules;
import io.github.thiagojosetj.gym.ui.common.SafeNavigation;
import io.github.thiagojosetj.gym.ui.common.ViewModelFactories;
import io.github.thiagojosetj.gym.ui.library.ExerciseLibraryFragment;

/**
 * Create or edit a workout template (PRODUCT_SPEC TPL-01..04, ADR-0019): name and notes, exercises
 * from the library with 3 × 12 defaults, plan per exercise, drag-and-drop order, explicit save.
 */
public class TemplateEditorFragment extends Fragment implements EditorExerciseAdapter.Listener {

    private static final String ARG_TEMPLATE_ID = "templateId";
    private static final String PLAN_SHEET_TAG = "plan_sheet";
    private static final String GROUP_SHEET_TAG = "group_sheet";

    private FragmentTemplateEditorBinding binding;
    private TemplateEditorViewModel viewModel;
    private EditorHeaderAdapter headerAdapter;
    private EditorExerciseAdapter exerciseAdapter;
    private EditorFooterAdapter footerAdapter;
    private ItemTouchHelper touchHelper;
    private OnBackPressedCallback discardGuard;
    private AlertDialog discardDialog;
    /** Last "save enabled" state shown in the menu; the menu is only rebuilt when it changes. */
    private Boolean menuEditable;

    /** Navigation arguments: null id = new template (ADR-0018). */
    public static Bundle args(@Nullable String templateId) {
        Bundle bundle = new Bundle();
        bundle.putString(ARG_TEMPLATE_ID, templateId);
        return bundle;
    }

    /** Also used by {@link ExercisePlanSheet} to reach the same ViewModel instance. */
    ViewModelProvider.Factory viewModelFactory() {
        AppContainer app = ViewModelFactories.container(this);
        String templateId = getArguments() == null ? null : getArguments().getString(ARG_TEMPLATE_ID);
        return ViewModelFactories.of(TemplateEditorViewModel.class, () -> new TemplateEditorViewModel(
                app.templates, app.exercises, app.techniques, app.settings, app.ids, templateId));
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this, viewModelFactory()).get(TemplateEditorViewModel.class);
        // Exercises picked in the library come back through the Fragment Result API.
        getParentFragmentManager().setFragmentResultListener(ExerciseLibraryFragment.RESULT_REQUEST_KEY, this,
                (key, result) -> viewModel.addExercises(
                        result.getStringArrayList(ExerciseLibraryFragment.RESULT_EXERCISE_IDS)));
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTemplateEditorBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        headerAdapter = new EditorHeaderAdapter(viewModel::rename, viewModel::setDescription, viewModel::setNotes);
        // Units are kg for now; lb support is planned (docs/ROADMAP.md) and only needs this value.
        exerciseAdapter = new EditorExerciseAdapter(this, new PlanFormatter(getResources(), WeightUnit.KILOGRAM));
        footerAdapter = new EditorFooterAdapter(this::openPicker);
        binding.list.setAdapter(new ConcatAdapter(headerAdapter, exerciseAdapter, footerAdapter));

        touchHelper = new ItemTouchHelper(new DragCallback());
        touchHelper.attachToRecyclerView(binding.list);

        discardGuard = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                confirmDiscard();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), discardGuard);
        addSaveMenu();

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.events().observe(getViewLifecycleOwner(), event -> {
            TemplateEditorViewModel.EditorEvent e = event.consume();
            if (e == null) {
                return;
            }
            switch (e) {
                case SAVED -> NavHostFragment.findNavController(this).popBackStack();
                case SAVE_FAILED -> snackbar(getString(R.string.editor_error_save));
                case ADD_FAILED, GROUP_FAILED ->
                        snackbar(getString(R.string.template_operation_failed));
                case GROUPED -> snackbar(getString(R.string.editor_grouped));
                case UNGROUPED -> snackbar(getString(R.string.editor_ungrouped));
                case GROUP_TOO_SMALL ->
                        snackbar(GroupExercisesSheet.tooSmallMessage(getResources()));
                case GROUP_NEEDS_SAVE -> snackbar(getString(R.string.editor_group_error_unsaved));
                case EXERCISE_LIMIT_REACHED ->
                        snackbar(getResources().getQuantityString(R.plurals.editor_error_too_many,
                                TemplateRules.MAX_EXERCISES, TemplateRules.MAX_EXERCISES));
            }
        });
    }

    private void render(TemplateEditorState state) {
        ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(state.isNew() ? R.string.title_template_new : R.string.title_template_edit);
        }
        boolean loading = state.status() == TemplateEditorState.Status.LOADING
                || state.status() == TemplateEditorState.Status.SAVING;
        binding.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        boolean failed = state.status() == TemplateEditorState.Status.LOAD_FAILED;
        binding.loadError.setVisibility(failed ? View.VISIBLE : View.GONE);
        binding.list.setVisibility(failed ? View.GONE : View.VISIBLE);

        headerAdapter.submit(new EditorHeaderAdapter.Header(state.name(), state.description(), state.notes(),
                nameError(state.errors()), state.editable()));
        exerciseAdapter.submit(state.exercises());
        footerAdapter.submit(state.exercises().isEmpty(), state.editable());
        // Not while saving: the draft is still "modified" until the write completes, and a discard
        // dialog opened then would outlive the screen when SAVED pops it.
        discardGuard.setEnabled(state.editable() && viewModel.hasUnsavedChanges());
        if (menuEditable == null || menuEditable != state.editable()) {
            menuEditable = state.editable();
            requireActivity().invalidateMenu();
        }
    }

    @Nullable
    private String nameError(List<TemplateDraft.Error> errors) {
        if (errors.contains(TemplateDraft.Error.NAME_BLANK)) {
            return getString(R.string.editor_error_name_blank);
        }
        if (errors.contains(TemplateDraft.Error.NAME_TOO_LONG)) {
            return getResources().getQuantityString(R.plurals.editor_error_name_too_long,
                    TemplateRules.MAX_NAME_LENGTH, TemplateRules.MAX_NAME_LENGTH);
        }
        return null;
    }

    private void addSaveMenu() {
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.menu_template_editor, menu);
            }

            @Override
            public void onPrepareMenu(@NonNull Menu menu) {
                TemplateEditorState state = viewModel.state().getValue();
                MenuItem save = menu.findItem(R.id.action_save);
                if (save != null) {
                    save.setEnabled(state != null && state.editable());
                }
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() == R.id.action_save) {
                    save();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    private void save() {
        viewModel.save();
        TemplateEditorState state = viewModel.state().getValue();
        if (state == null) {
            return;
        }
        if (state.errors().contains(TemplateDraft.Error.NO_EXERCISES)) {
            snackbar(getString(R.string.editor_error_no_exercises));
        } else if (state.errors().contains(TemplateDraft.Error.DESCRIPTION_TOO_LONG)
                || state.errors().contains(TemplateDraft.Error.NOTES_TOO_LONG)) {
            snackbar(getString(R.string.editor_error_text_too_long));
        }
        if (nameError(state.errors()) != null) {
            binding.list.smoothScrollToPosition(0);
        }
    }

    private void confirmDiscard() {
        discardDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.editor_discard_title)
                .setMessage(R.string.editor_discard_message)
                .setNegativeButton(R.string.editor_keep_editing, null)
                .setPositiveButton(R.string.editor_discard, (dialog, which) -> {
                    if (!isAdded()) {
                        return; // the screen is already gone (e.g. a save finished meanwhile)
                    }
                    discardGuard.setEnabled(false);
                    NavHostFragment.findNavController(this).popBackStack();
                })
                .show();
    }

    private void openPicker() {
        SafeNavigation.navigate(this, R.id.action_editor_to_picker);
    }

    private void snackbar(String text) {
        Snackbar.make(binding.getRoot(), text, Snackbar.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------------ exercise card actions

    @Override
    public void onEdit(TemplateExerciseItem item) {
        if (getChildFragmentManager().findFragmentByTag(PLAN_SHEET_TAG) == null) {
            ExercisePlanSheet.newInstance(item.id()).show(getChildFragmentManager(), PLAN_SHEET_TAG);
        }
    }

    @Override
    public void onMore(TemplateExerciseItem item, int position, View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.inflate(R.menu.menu_template_exercise);
        // Grouping and ungrouping are one slot: an exercise is either in a group or it is not.
        popup.getMenu().findItem(R.id.action_group).setVisible(item.group() == null);
        popup.getMenu().findItem(R.id.action_ungroup).setVisible(item.group() != null);
        popup.getMenu().findItem(R.id.action_move_up).setEnabled(position > 0);
        popup.getMenu().findItem(R.id.action_move_down).setEnabled(position < exerciseAdapter.size() - 1);
        popup.setOnMenuItemClickListener(menuItem -> {
            int id = menuItem.getItemId();
            if (id == R.id.action_edit_plan) {
                onEdit(item);
            } else if (id == R.id.action_group) {
                onGroupRequested(item);
            } else if (id == R.id.action_ungroup) {
                viewModel.removeGroup(item.group().groupId());
            } else if (id == R.id.action_move_up) {
                onMoveRequested(item, position, position - 1);
            } else if (id == R.id.action_move_down) {
                onMoveRequested(item, position, position + 1);
            } else if (id == R.id.action_remove) {
                viewModel.removeExercise(item.id());
            } else {
                return false;
            }
            return true;
        });
        popup.show();
    }

    /**
     * Opens "Agrupar com…" only when there is something to choose and it can be done: a group is
     * made in the database at once, so an exercise that exists only in this draft has to be saved
     * first. Otherwise the user is told which of the two it is, not shown a dead sheet.
     */
    private void onGroupRequested(TemplateExerciseItem item) {
        if (!viewModel.isSaved(item.id())) {
            snackbar(getString(R.string.editor_group_error_unsaved));
        } else if (viewModel.groupCandidates(item.id()).isEmpty()) {
            snackbar(GroupExercisesSheet.tooSmallMessage(getResources()));
        } else if (getChildFragmentManager().findFragmentByTag(GROUP_SHEET_TAG) == null) {
            GroupExercisesSheet.newInstance(item.id())
                    .show(getChildFragmentManager(), GROUP_SHEET_TAG);
        }
    }

    @Override
    public void onMoveRequested(TemplateExerciseItem item, int fromPosition, int toPosition) {
        viewModel.moveExercise(fromPosition, toPosition);
        // Dragging shows what happened; a move from the menu or from TalkBack does not, so confirm
        // it with a snackbar (which TalkBack also announces).
        snackbar(getString(R.string.editor_moved, item.name(), toPosition + 1));
    }

    @Override
    public void onStartDrag(RecyclerView.ViewHolder holder) {
        TemplateEditorState state = viewModel.state().getValue();
        if (state != null && state.editable()) {
            touchHelper.startDrag(holder);
        }
    }

    /** Only exercise cards can be dragged, and only among themselves (not onto header/footer). */
    private final class DragCallback extends ItemTouchHelper.SimpleCallback {

        private int dragFrom = RecyclerView.NO_POSITION;
        private int dragTo = RecyclerView.NO_POSITION;

        DragCallback() {
            super(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean isLongPressDragEnabled() {
            return false; // drag starts from the handle
        }

        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder holder) {
            return holder.getBindingAdapter() == exerciseAdapter ? super.getMovementFlags(recyclerView, holder) : 0;
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder source,
                              @NonNull RecyclerView.ViewHolder target) {
            if (target.getBindingAdapter() != exerciseAdapter) {
                return false;
            }
            int from = source.getBindingAdapterPosition();
            int to = target.getBindingAdapterPosition();
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
                return false;
            }
            if (dragFrom == RecyclerView.NO_POSITION) {
                dragFrom = from;
            }
            dragTo = to;
            exerciseAdapter.moveLocal(from, to);
            return true;
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder holder) {
            super.clearView(recyclerView, holder);
            if (dragFrom != RecyclerView.NO_POSITION && dragTo != RecyclerView.NO_POSITION) {
                viewModel.moveExercise(dragFrom, dragTo); // commit once, on drop
            }
            dragFrom = RecyclerView.NO_POSITION;
            dragTo = RecyclerView.NO_POSITION;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
            // Swipe is disabled (no swipe directions).
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Release the destroyed view hierarchy: adapters and ItemTouchHelper keep a reference to
        // the RecyclerView, so the fragment would hold the old views while it sits in the back stack.
        binding.list.setAdapter(null);
        touchHelper.attachToRecyclerView(null);
        if (discardDialog != null) {
            discardDialog.dismiss();
            discardDialog = null;
        }
        headerAdapter = null;
        exerciseAdapter = null;
        footerAdapter = null;
        touchHelper = null;
        binding = null;
        menuEditable = null;
    }
}
