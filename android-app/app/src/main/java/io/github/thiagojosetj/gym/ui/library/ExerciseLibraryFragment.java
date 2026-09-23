package io.github.thiagojosetj.gym.ui.library;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.databinding.FragmentExerciseLibraryBinding;
import io.github.thiagojosetj.gym.domain.library.Equipment;
import io.github.thiagojosetj.gym.domain.library.ExerciseFilter;
import io.github.thiagojosetj.gym.domain.library.ExerciseSummary;
import io.github.thiagojosetj.gym.domain.library.MuscleGroup;
import io.github.thiagojosetj.gym.domain.library.MuscleNode;
import io.github.thiagojosetj.gym.domain.library.MuscleRoleScope;
import io.github.thiagojosetj.gym.ui.common.SafeNavigation;
import io.github.thiagojosetj.gym.ui.common.ViewModelFactories;

/**
 * Exercise library (PRODUCT_SPEC LIB-01..04). Two modes:
 * <ul>
 *   <li>browse (bottom-navigation tab): tap opens the detail;</li>
 *   <li>select (opened by the template editor): tap toggles selection and the picked ids are
 *       returned through the Fragment Result API.</li>
 * </ul>
 * Muscle groups and equipment come from the database: no anatomical names are hard-coded here.
 */
public class ExerciseLibraryFragment extends Fragment implements ExerciseAdapter.Listener {

    public static final String RESULT_REQUEST_KEY = "exercise_picker";
    public static final String RESULT_EXERCISE_IDS = "exercise_ids";
    private static final String ARG_SELECT_MODE = "selectMode";
    private static final long SEARCH_DEBOUNCE_MS = 250;

    private FragmentExerciseLibraryBinding binding;
    private ExerciseLibraryViewModel viewModel;
    private ExerciseAdapter adapter;
    private boolean selectMode;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    /** Chip view id → muscle id (null = "all"). Rebuilt with the chips. */
    private final Map<Integer, String> groupChipIds = new HashMap<>();
    private final Map<Integer, String> subgroupChipIds = new HashMap<>();
    private List<MuscleGroup> groups = new ArrayList<>();
    private List<Equipment> equipment = new ArrayList<>();
    /** True while chips are updated from the ViewModel, so listeners don't echo changes back. */
    private boolean syncingChips;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentExerciseLibraryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        selectMode = getArguments() != null && getArguments().getBoolean(ARG_SELECT_MODE, false);
        AppContainer app = ViewModelFactories.container(this);
        viewModel = new ViewModelProvider(this, ViewModelFactories.of(ExerciseLibraryViewModel.class,
                () -> new ExerciseLibraryViewModel(app.exercises))).get(ExerciseLibraryViewModel.class);

        adapter = new ExerciseAdapter(selectMode, this);
        binding.list.setAdapter(adapter);
        binding.list.setHasFixedSize(true);

        setUpSearch();
        setUpFilters();
        setUpSelectMode();

        viewModel.exercises().observe(getViewLifecycleOwner(), this::showExercises);
        viewModel.muscleGroups().observe(getViewLifecycleOwner(), list -> {
            groups = list;
            buildGroupChips();
            renderFilter(viewModel.currentFilter());
        });
        viewModel.equipment().observe(getViewLifecycleOwner(), list -> {
            equipment = list;
            renderFilter(viewModel.currentFilter());
        });
        viewModel.filter().observe(getViewLifecycleOwner(), this::renderFilter);
    }

    // ------------------------------------------------------------------ search

    private void setUpSearch() {
        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Debounce: query once the user pauses typing, not on every keystroke.
                if (pendingSearch != null) {
                    handler.removeCallbacks(pendingSearch);
                }
                String text = s.toString();
                pendingSearch = () -> viewModel.setQuery(text);
                handler.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MS);
            }
        });
    }

    // ------------------------------------------------------------------ filters

    private void setUpFilters() {
        binding.groupChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!syncingChips && !checkedIds.isEmpty()) {
                viewModel.selectGroup(groupChipIds.get(checkedIds.get(0)));
            }
        });
        binding.subgroupChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!syncingChips && !checkedIds.isEmpty()) {
                viewModel.selectSubgroup(subgroupChipIds.get(checkedIds.get(0)));
            }
        });
        binding.chipEquipment.setOnClickListener(v -> showEquipmentDialog());
        binding.chipRole.setOnClickListener(v -> showRoleDialog());
        binding.buttonClearFilters.setOnClickListener(v -> viewModel.clearStructuredFilters());
    }

    private void buildGroupChips() {
        syncingChips = true;
        binding.groupChips.removeAllViews();
        groupChipIds.clear();
        addChip(binding.groupChips, groupChipIds, getString(R.string.library_filter_all_groups), null);
        for (MuscleGroup group : groups) {
            addChip(binding.groupChips, groupChipIds, group.group().name(), group.group().id());
        }
        syncingChips = false;
    }

    private void buildSubgroupChips(MuscleGroup group) {
        binding.subgroupChips.removeAllViews();
        subgroupChipIds.clear();
        addChip(binding.subgroupChips, subgroupChipIds, getString(R.string.library_filter_whole_group), null);
        for (MuscleNode node : group.subgroups()) {
            addChip(binding.subgroupChips, subgroupChipIds, node.name(), node.id());
        }
        binding.subgroupChips.setTag(group.group().id());
    }

    private void addChip(ChipGroup parent, Map<Integer, String> ids, String text, @Nullable String muscleId) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.view_filter_chip, parent, false);
        chip.setId(View.generateViewId());
        chip.setText(text);
        ids.put(chip.getId(), muscleId);
        parent.addView(chip);
    }

    /** Makes every filter control reflect the ViewModel's filter (single source of truth). */
    private void renderFilter(ExerciseFilter filter) {
        if (binding == null) {
            return;
        }
        syncingChips = true;
        checkChipFor(binding.groupChips, groupChipIds, filter.muscleGroupId());

        MuscleGroup selectedGroup = findGroup(filter.muscleGroupId());
        boolean showSubgroups = selectedGroup != null && !selectedGroup.subgroups().isEmpty();
        binding.subgroupScroll.setVisibility(showSubgroups ? View.VISIBLE : View.GONE);
        if (showSubgroups) {
            if (!selectedGroup.group().id().equals(binding.subgroupChips.getTag())) {
                buildSubgroupChips(selectedGroup);
            }
            checkChipFor(binding.subgroupChips, subgroupChipIds, filter.muscleSubgroupId());
        }
        syncingChips = false;

        int equipmentCount = filter.equipmentIds().size();
        binding.chipEquipment.setText(equipmentCount == 0
                ? getString(R.string.library_filter_equipment)
                : getString(R.string.library_filter_equipment_selected, equipmentCount));
        binding.chipRole.setVisibility(filter.muscleGroupId() != null ? View.VISIBLE : View.GONE);
        binding.chipRole.setText(roleLabel(filter.roleScope()));
        binding.buttonClearFilters.setVisibility(filter.hasStructuredFilters() ? View.VISIBLE : View.GONE);
    }

    private static void checkChipFor(ChipGroup group, Map<Integer, String> ids, @Nullable String muscleId) {
        for (Map.Entry<Integer, String> entry : ids.entrySet()) {
            boolean match = muscleId == null ? entry.getValue() == null : muscleId.equals(entry.getValue());
            if (match) {
                if (group.getCheckedChipId() != entry.getKey()) {
                    group.check(entry.getKey());
                }
                return;
            }
        }
    }

    @Nullable
    private MuscleGroup findGroup(@Nullable String groupId) {
        if (groupId == null) {
            return null;
        }
        for (MuscleGroup g : groups) {
            if (g.group().id().equals(groupId)) {
                return g;
            }
        }
        return null;
    }

    private String roleLabel(MuscleRoleScope scope) {
        return getString(switch (scope) {
            case PRIMARY -> R.string.library_role_primary;
            case SECONDARY -> R.string.library_role_secondary;
            case ANY -> R.string.library_role_any;
        });
    }

    private void showEquipmentDialog() {
        if (equipment.isEmpty()) {
            return;
        }
        String[] names = new String[equipment.size()];
        boolean[] checked = new boolean[equipment.size()];
        Set<String> current = viewModel.currentFilter().equipmentIds();
        for (int i = 0; i < equipment.size(); i++) {
            names[i] = equipment.get(i).name();
            checked[i] = current.contains(equipment.get(i).id());
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.library_equipment_dialog_title)
                .setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_apply, (dialog, which) -> {
                    Set<String> ids = new HashSet<>();
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) {
                            ids.add(equipment.get(i).id());
                        }
                    }
                    viewModel.setEquipment(ids);
                })
                .show();
    }

    private void showRoleDialog() {
        MuscleRoleScope[] scopes = MuscleRoleScope.values();
        String[] labels = new String[scopes.length];
        int checked = 0;
        for (int i = 0; i < scopes.length; i++) {
            labels[i] = roleLabel(scopes[i]);
            if (scopes[i] == viewModel.currentFilter().roleScope()) {
                checked = i;
            }
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.library_role_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    viewModel.setRoleScope(scopes[which]);
                    dialog.dismiss();
                })
                .show();
    }

    // ------------------------------------------------------------------ list & selection

    private void showExercises(List<ExerciseSummary> list) {
        adapter.submitList(list);
        // Explicit text for zero: Portuguese plural rules treat 0 as singular ("0 exercício").
        binding.resultCount.setText(list.isEmpty()
                ? getString(R.string.library_result_none)
                : getResources().getQuantityString(R.plurals.library_result_count, list.size(), list.size()));
        binding.empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void setUpSelectMode() {
        binding.selectHint.setVisibility(selectMode ? View.VISIBLE : View.GONE);
        binding.buttonAddSelected.setVisibility(selectMode ? View.VISIBLE : View.GONE);
        if (!selectMode) {
            return;
        }
        viewModel.selection().observe(getViewLifecycleOwner(), ids -> {
            adapter.setSelected(ids);
            binding.buttonAddSelected.setEnabled(!ids.isEmpty());
            binding.buttonAddSelected.setText(ids.isEmpty()
                    ? getString(R.string.editor_add_exercises)
                    : getResources().getQuantityString(R.plurals.library_add_selected, ids.size(), ids.size()));
        });
        binding.buttonAddSelected.setOnClickListener(v -> {
            Bundle result = new Bundle();
            result.putStringArrayList(RESULT_EXERCISE_IDS, new ArrayList<>(viewModel.selection().getValue()));
            getParentFragmentManager().setFragmentResult(RESULT_REQUEST_KEY, result);
            NavHostFragment.findNavController(this).popBackStack();
        });
    }

    @Override
    public void onClick(ExerciseSummary exercise) {
        if (selectMode) {
            viewModel.toggleSelection(exercise.id());
        } else {
            openDetail(exercise);
        }
    }

    @Override
    public void onInfo(ExerciseSummary exercise) {
        openDetail(exercise);
    }

    private void openDetail(ExerciseSummary exercise) {
        SafeNavigation.navigate(this, selectMode ? R.id.action_picker_to_detail : R.id.action_library_to_detail,
                ExerciseDetailFragment.args(exercise.id()));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (pendingSearch != null) {
            handler.removeCallbacks(pendingSearch);
        }
        // Detach the adapter so the fragment does not keep the destroyed list alive in the back stack.
        binding.list.setAdapter(null);
        adapter = null;
        binding = null;
    }
}
