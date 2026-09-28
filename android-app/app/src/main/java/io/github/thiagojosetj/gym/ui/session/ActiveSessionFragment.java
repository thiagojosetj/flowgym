package io.github.thiagojosetj.gym.ui.session;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.databinding.FragmentActiveSessionBinding;
import io.github.thiagojosetj.gym.domain.session.ActiveSession;
import io.github.thiagojosetj.gym.domain.session.FinishReview;
import io.github.thiagojosetj.gym.domain.session.SessionSummary;
import io.github.thiagojosetj.gym.domain.model.WeightUnit;
import io.github.thiagojosetj.gym.domain.technique.TechniqueCatalog;
import io.github.thiagojosetj.gym.domain.technique.TechniqueScope;
import io.github.thiagojosetj.gym.domain.technique.TrainingTechnique;
import io.github.thiagojosetj.gym.ui.common.Durations;
import io.github.thiagojosetj.gym.ui.common.NumberInput;
import io.github.thiagojosetj.gym.ui.common.SafeNavigation;
import io.github.thiagojosetj.gym.ui.common.TechniqueDialogs;
import io.github.thiagojosetj.gym.ui.common.ViewModelFactories;

/**
 * The workout in progress (ACT-01 to ACT-09): every exercise on one screen, each set logged as it is
 * performed.
 *
 * <p>Only two things change every second - the workout clock and the rest countdown - and both live
 * in fixed views here, never inside the list.
 */
public class ActiveSessionFragment extends Fragment implements SessionRowAdapter.Callbacks {

    private static final String ARG_SESSION_ID = "sessionId";
    /** Seconds at which the rest is announced to a screen reader; every second would be noise. */
    private static final int[] ANNOUNCE_AT_SECONDS = {60, 30, 10, 0};

    private FragmentActiveSessionBinding binding;
    private ActiveSessionViewModel viewModel;
    private SessionRowAdapter adapter;
    @Nullable
    private AlertDialog openDialog;
    private int lastAnnouncedSecond = -1;

    public static Bundle args(String sessionId) {
        Bundle args = new Bundle();
        args.putString(ARG_SESSION_ID, sessionId);
        return args;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentActiveSessionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        AppContainer app = ViewModelFactories.container(this);
        String sessionId = requireArguments().getString(ARG_SESSION_ID);
        viewModel = new ViewModelProvider(this, ViewModelFactories.of(ActiveSessionViewModel.class,
                () -> new ActiveSessionViewModel(app.activeSessions, app.techniques, app.clock,
                        getResources(), sessionId))).get(ActiveSessionViewModel.class);

        adapter = new SessionRowAdapter(this, app.executors.diskIO());
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter);
        // A change animation cross-fades a copy of the row and steals the caret from a field.
        binding.list.setItemAnimator(null);

        binding.buttonPause.setOnClickListener(v -> {
            ActiveSession session = viewModel.session().getValue();
            if (session != null && session.header().isPaused()) {
                viewModel.resume();
            } else {
                viewModel.pause();
            }
        });
        binding.buttonFinish.setOnClickListener(v -> askToFinish());
        binding.restMinus.setOnClickListener(v -> viewModel.adjustRest(-15));
        binding.restPlus15.setOnClickListener(v -> viewModel.adjustRest(15));
        binding.restPlus30.setOnClickListener(v -> viewModel.adjustRest(30));
        binding.restSkip.setOnClickListener(v -> viewModel.skipRest());

        viewModel.rows().observe(getViewLifecycleOwner(), rows -> adapter.submitList(rows));
        viewModel.session().observe(getViewLifecycleOwner(), this::render);
        viewModel.ticker().observe(getViewLifecycleOwner(), now -> renderTime());
        viewModel.events().observe(getViewLifecycleOwner(), event -> {
            ActiveSessionViewModel.SessionEvent value = event.consume();
            if (value != null) {
                onEvent(value);
            }
        });

    }

    @Override
    public void onStop() {
        super.onStop();
        // Whatever was typed and not confirmed is persisted before the screen goes away.
        viewModel.flushAllDrafts();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (openDialog != null) {
            openDialog.dismiss();
            openDialog = null;
        }
        binding.list.setAdapter(null);
        adapter = null;
        binding = null;
    }

    // ------------------------------------------------------------------ rendering

    private void render(@Nullable ActiveSession session) {
        if (binding == null) {
            return;
        }
        if (session == null) {
            // Really gone (deleted, or discarded from somewhere else). The repository never reports
            // null while the query is still loading, so this is not a race any more.
            leave();
            return;
        }
        if (!session.header().isActive()) {
            // Finished or discarded: show the summary once, then get out. Without this the screen
            // kept offering Finalizar on a closed session, which rewrote its duration.
            binding.buttonPause.setEnabled(false);
            binding.buttonFinish.setEnabled(false);
            if (viewModel.hasUnshownSummary()) {
                showSummary(viewModel.summary());
            } else if (openDialog == null || !openDialog.isShowing()) {
                leave();
            }
            return;
        }
        binding.buttonPause.setEnabled(true);
        binding.buttonFinish.setEnabled(true);
        binding.sessionName.setText(session.header().name());
        binding.progress.setText(getResources().getQuantityString(R.plurals.session_progress,
                session.totalSets(), session.completedSets(), session.totalSets()));
        boolean paused = session.header().isPaused();
        binding.pausedLabel.setVisibility(paused ? View.VISIBLE : View.GONE);
        binding.buttonPause.setText(paused ? R.string.session_resume : R.string.session_pause);
        renderTime();
    }

    /** Called by the ticker: touches only the time views, never the list. */
    private void renderTime() {
        ActiveSession session = viewModel.session().getValue();
        if (binding == null || session == null) {
            return;
        }
        long now = viewModel.now();
        long elapsed = session.header().clock().effectiveMs(now);
        binding.elapsed.setText(Durations.clock(elapsed));
        binding.elapsed.setContentDescription(getString(R.string.session_elapsed_label) + ": "
                + Durations.spoken(elapsed));

        long restMs = session.header().restRemainingMs(now);
        boolean hasRest = session.header().restSetLogId() != null;
        int seconds = (int) ((restMs + 999L) / 1000L);
        if (!hasRest) {
            binding.restContainer.setVisibility(View.GONE);
            lastAnnouncedSecond = -1;
            return;
        }
        binding.restContainer.setVisibility(View.VISIBLE);
        if (restMs == 0) {
            // The bar stays until the service clears the rest, so the end can actually be announced;
            // treating zero as "not resting" made the "Descanso terminado" message dead code.
            announceRest(0);
        }
        binding.restRemaining.setText(Durations.clock(restMs));
        binding.restRemaining.setContentDescription(
                getString(R.string.session_rest_spoken, Durations.spoken(restMs)));
        announceRest(seconds);
    }

    /**
     * Milestones only: announcing every second would be unusable with TalkBack. Anchored above the
     * rest bar, because a Snackbar over it would swallow the taps on +30 s.
     */
    private void announceRest(int seconds) {
        for (int milestone : ANNOUNCE_AT_SECONDS) {
            if (seconds == milestone && lastAnnouncedSecond != milestone) {
                lastAnnouncedSecond = milestone;
                Snackbar.make(binding.getRoot(), milestone == 0
                                ? getString(R.string.session_rest_over)
                                : getString(R.string.session_rest_spoken, Durations.spoken(seconds * 1000L)),
                        Snackbar.LENGTH_SHORT).setAnchorView(binding.restContainer).show();
                return;
            }
        }
    }

    private void onEvent(ActiveSessionViewModel.SessionEvent event) {
        switch (event) {
            case FINISHED -> {
                // The session row is now COMPLETED, so render() shows the summary; nothing to do
                // here beyond letting that happen.
            }
            case DISCARDED -> NavHostFragment.findNavController(this).popBackStack();
            case ACTION_FAILED -> snackbar(getString(R.string.session_action_failed));
            case FINISH_FAILED -> snackbar(getString(R.string.finish_failed));
        }
    }

    // ------------------------------------------------------------------ row callbacks

    @Override
    public void onToggleExercise(String sessionExerciseId) {
        viewModel.toggleCollapsed(sessionExerciseId);
    }

    @Override
    public void onWeightTyped(String setId, String text) {
        viewModel.onWeightTyped(setId, text);
    }

    @Override
    public void onRepsTyped(String setId, String text) {
        viewModel.onRepsTyped(setId, text);
    }

    @Override
    public void onFieldDone(String setId) {
        viewModel.flushDraft(setId);
    }

    @Override
    public void onConfirm(String setId) {
        viewModel.confirmSet(setId);
    }

    @Override
    public void onUndo(String setId) {
        viewModel.undoSet(setId);
    }

    @Override
    public void onRemove(String sessionExerciseId, String setId) {
        viewModel.removeSet(sessionExerciseId, setId);
    }

    @Override
    public void onTechnique(String setId) {
        TechniqueCatalog catalog = viewModel.techniques().getValue();
        if (catalog == null) {
            snackbar(getString(R.string.plan_error_technique_unavailable));
            return;
        }
        List<TrainingTechnique> options = catalog.ofScope(TechniqueScope.SET);
        if (options.isEmpty()) {
            snackbar(getString(R.string.plan_error_technique_unavailable));
            return;
        }
        ActiveSession session = viewModel.session().getValue();
        String current = session == null || session.setById(setId) == null
                ? null
                : session.setById(setId).techniqueId();
        openDialog = TechniqueDialogs.showPicker(requireContext(), options, current,
                techniqueId -> viewModel.setTechnique(setId, techniqueId));
    }

    @Override
    public void onAddSet(String sessionExerciseId) {
        viewModel.addSet(sessionExerciseId);
    }

    // ------------------------------------------------------------------ finishing

    /** Shows exactly what finishing will do before it does anything (PRODUCT_SPEC section 8). */
    private void askToFinish() {
        FinishReview review = viewModel.finishReview();
        if (review == null) {
            return;
        }
        if (!review.needsConfirmation()) {
            // Everything is confirmed: there is nothing to warn about, so do not open a dialog with
            // an empty body - just finish.
            viewModel.finish();
            return;
        }
        List<String> lines = new ArrayList<>();
        if (review.wouldRecordNothing()) {
            lines.add(getString(R.string.finish_nothing_recorded));
        } else if (review.alreadyCompleted() > 0) {
            lines.add(getResources().getQuantityString(R.plurals.finish_already_done,
                    review.alreadyCompleted(), review.alreadyCompleted()));
        }
        if (!review.readyToComplete().isEmpty()) {
            lines.add(getResources().getQuantityString(R.plurals.finish_will_complete,
                    review.readyToComplete().size(), review.readyToComplete().size()));
        }
        if (!review.partiallyFilled().isEmpty()) {
            lines.add(getResources().getQuantityString(R.plurals.finish_will_skip_partial,
                    review.partiallyFilled().size(), review.partiallyFilled().size()));
        }
        if (!review.empty().isEmpty()) {
            lines.add(getResources().getQuantityString(R.plurals.finish_will_skip_empty,
                    review.empty().size(), review.empty().size()));
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.finish_title)
                .setMessage(String.join("\n\n", lines))
                .setNegativeButton(R.string.finish_back, null)
                .setPositiveButton(R.string.finish_confirm, (dialog, which) -> viewModel.finish());
        if (review.wouldRecordNothing()) {
            builder.setNeutralButton(R.string.finish_discard, (dialog, which) -> confirmDiscard());
        }
        openDialog = builder.show();
    }

    private void confirmDiscard() {
        openDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.finish_discard_title)
                .setMessage(R.string.finish_discard_message)
                .setNegativeButton(R.string.finish_back, null)
                .setPositiveButton(R.string.finish_discard, (dialog, which) -> viewModel.discard())
                .show();
    }

    private void showSummary(@Nullable SessionSummary summary) {
        if (summary == null) {
            leave();
            return;
        }
        viewModel.onSummaryShown();
        List<String> lines = new ArrayList<>();
        lines.add(getString(R.string.summary_duration, Durations.clock(summary.totalMs()),
                Durations.clock(summary.effectiveMs())));
        lines.add(getString(R.string.summary_sets, summary.performedSets(), summary.totalReps()));
        if (summary.hasVolume()) {
            String value = NumberInput.formatDecimal(
                    WeightUnit.KILOGRAM.fromGrams(summary.volumeGrams()),
                    getResources().getConfiguration().getLocales().get(0));
            lines.add(getString(R.string.summary_volume,
                    getString(R.string.session_weight_value, value, WeightUnit.KILOGRAM.symbol())));
        }
        if (summary.setsOutsideVolume() > 0) {
            // Transparency required by PRODUCT_SPEC section 9: say what is not in the total.
            lines.add(getResources().getQuantityString(R.plurals.summary_outside_volume,
                    summary.setsOutsideVolume(), summary.setsOutsideVolume()));
        }
        openDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.summary_title)
                .setMessage(String.join("\n", lines))
                .setPositiveButton(R.string.summary_close, null)
                // Dismissing it with back leaves too: staying on a finished session is what let the
                // user finish it twice.
                .setOnDismissListener(dialog -> leave())
                .show();
    }

    /** Leaves the workout screen, if it is still the one on screen. */
    private void leave() {
        if (isAdded()) {
            SafeNavigation.popFrom(this, R.id.activeSessionFragment);
        }
    }

    private void snackbar(String message) {
        if (binding != null) {
            Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
        }
    }
}
