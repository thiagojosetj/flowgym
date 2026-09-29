package io.github.thiagojosetj.gym.ui.session;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.concurrent.Executor;

import java.util.Objects;

import io.github.thiagojosetj.gym.ui.common.ListDiffing;
import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.databinding.ItemSessionAddSetBinding;
import io.github.thiagojosetj.gym.databinding.ItemSessionExerciseBinding;
import io.github.thiagojosetj.gym.databinding.ItemSessionSegmentBinding;
import io.github.thiagojosetj.gym.databinding.ItemSessionSetBinding;

/**
 * The active-workout list: exercise headers, set rows, the drops under a set and an "add set"
 * footer, in one flat adapter.
 *
 * <p>The delicate part is the text fields. A gym app rebinds rows while a sweaty thumb is typing, so:
 * the row never writes into a field that has focus, the TextWatcher is installed once and guarded by
 * a flag while binding, and change animations are off - they cross-fade a copy of the row and steal
 * the caret. The typed text itself lives in the ViewModel, never in the view holder.
 */
final class SessionRowAdapter extends ListAdapter<SessionRow, RecyclerView.ViewHolder> {

    /** What the screen can do to a row. */
    interface Callbacks {
        void onToggleExercise(String sessionExerciseId);

        // The typed, focus, confirm and undo callbacks are keyed by the id of whichever ROW holds
        // the values. A drop is a set_log row like a set, so its id goes through these very
        // callbacks, into the same draft map and the same repository writes; a second family of
        // them for drops would only be one more place to forget to keep in step.
        void onWeightTyped(String setId, String text);

        void onRepsTyped(String setId, String text);

        void onRepsLeftTyped(String setId, String text);

        void onRepsRightTyped(String setId, String text);

        void onFieldDone(String setId);

        void onConfirm(String setId);

        void onUndo(String setId);

        void onRemove(String sessionExerciseId, String setId);

        void onTechnique(String setId);

        void onAddSet(String sessionExerciseId);

        /** Adds a drop to the SET with this id. */
        void onAddSegment(String setId);

        /** Removes ONE drop, by its own id: the set it belongs to and its other drops stay. */
        void onRemoveSegment(String segmentId);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_SET = 1;
    private static final int TYPE_ADD = 2;
    private static final int TYPE_SEGMENT = 3;

    private final Callbacks callbacks;

    SessionRowAdapter(Callbacks callbacks, Executor diffExecutor) {
        super(ListDiffing.config(DIFF, diffExecutor));
        this.callbacks = callbacks;
    }

    @Override
    public int getItemViewType(int position) {
        SessionRow row = getItem(position);
        if (row instanceof SessionRow.ExerciseHeader) {
            return TYPE_HEADER;
        }
        if (row instanceof SessionRow.SetRow) {
            return TYPE_SET;
        }
        return row instanceof SessionRow.Segment ? TYPE_SEGMENT : TYPE_ADD;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(ItemSessionExerciseBinding.inflate(inflater, parent, false));
        }
        if (viewType == TYPE_SET) {
            return new SetHolder(ItemSessionSetBinding.inflate(inflater, parent, false), callbacks);
        }
        if (viewType == TYPE_SEGMENT) {
            return new SegmentHolder(
                    ItemSessionSegmentBinding.inflate(inflater, parent, false), callbacks);
        }
        return new AddHolder(ItemSessionAddSetBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        SessionRow row = getItem(position);
        if (holder instanceof HeaderHolder && row instanceof SessionRow.ExerciseHeader header) {
            ((HeaderHolder) holder).bind(header, callbacks);
        } else if (holder instanceof SetHolder && row instanceof SessionRow.SetRow set) {
            ((SetHolder) holder).bind(set);
        } else if (holder instanceof SegmentHolder && row instanceof SessionRow.Segment segment) {
            ((SegmentHolder) holder).bind(segment);
        } else if (holder instanceof AddHolder && row instanceof SessionRow.AddSet add) {
            ((AddHolder) holder).bind(add, callbacks);
        }
    }

    // ------------------------------------------------------------------ holders

    static final class HeaderHolder extends RecyclerView.ViewHolder {
        private final ItemSessionExerciseBinding views;

        HeaderHolder(ItemSessionExerciseBinding views) {
            super(views.getRoot());
            this.views = views;
        }

        void bind(SessionRow.ExerciseHeader header, Callbacks callbacks) {
            views.name.setText(header.name());
            String progress = views.getRoot().getContext().getString(R.string.session_exercise_progress,
                    header.completedSets(), header.totalSets());
            String separator = views.getRoot().getContext().getString(R.string.separator_dot);
            views.meta.setText(header.restLabel() == null
                    ? progress
                    : progress + separator + views.getRoot().getContext()
                    .getString(R.string.session_rest_title) + " " + header.restLabel());
            views.permanentNotes.setText(header.permanentNotes());
            views.permanentNotes.setVisibility(header.permanentNotes() == null ? View.GONE : View.VISIBLE);
            views.collapseIcon.setRotation(header.collapsed() ? 0f : 180f);
            // The action is spoken, not inferred from an icon rotation.
            views.header.setContentDescription(views.getRoot().getContext().getString(
                    header.collapsed() ? R.string.session_expand : R.string.session_collapse,
                    header.name()));
            String exerciseId = header.id().substring("header:".length());
            views.header.setOnClickListener(v -> callbacks.onToggleExercise(exerciseId));
        }
    }

    static final class SetHolder extends RecyclerView.ViewHolder {
        private final ItemSessionSetBinding views;
        private final Callbacks callbacks;
        private boolean binding;
        @Nullable
        private String setId;
        @Nullable
        private String sessionExerciseId;

        SetHolder(ItemSessionSetBinding views, Callbacks callbacks) {
            super(views.getRoot());
            this.views = views;
            this.callbacks = callbacks;
            // Installed once: a watcher added on every bind would fire for the previous row's text.
            views.weightInput.addTextChangedListener(new SimpleWatcher(text -> {
                if (!binding && setId != null) {
                    callbacks.onWeightTyped(setId, text);
                }
            }));
            views.repsInput.addTextChangedListener(new SimpleWatcher(text -> {
                if (!binding && setId != null) {
                    callbacks.onRepsTyped(setId, text);
                }
            }));
            views.weightInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus && setId != null) {
                    callbacks.onFieldDone(setId);
                }
            });
            views.repsInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus && setId != null) {
                    callbacks.onFieldDone(setId);
                }
            });
            views.repsLeftInput.addTextChangedListener(new SimpleWatcher(text -> {
                if (!binding && setId != null) {
                    callbacks.onRepsLeftTyped(setId, text);
                }
            }));
            views.repsRightInput.addTextChangedListener(new SimpleWatcher(text -> {
                if (!binding && setId != null) {
                    callbacks.onRepsRightTyped(setId, text);
                }
            }));
            views.repsLeftInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus && setId != null) {
                    callbacks.onFieldDone(setId);
                }
            });
            views.repsRightInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus && setId != null) {
                    callbacks.onFieldDone(setId);
                }
            });
            views.buttonDone.setOnClickListener(v -> {
                if (setId == null) {
                    return;
                }
                if (views.buttonDone.isSelected()) {
                    callbacks.onUndo(setId);
                } else {
                    callbacks.onConfirm(setId);
                }
            });
            views.buttonRemove.setOnClickListener(v -> {
                if (setId != null && sessionExerciseId != null) {
                    callbacks.onRemove(sessionExerciseId, setId);
                }
            });
            views.buttonTechnique.setOnClickListener(v -> {
                if (setId != null) {
                    callbacks.onTechnique(setId);
                }
            });
            views.buttonAddSegment.setOnClickListener(v -> {
                if (setId != null) {
                    callbacks.onAddSegment(setId);
                }
            });
        }

        void bind(SessionRow.SetRow row) {
            binding = true;
            setId = row.id();
            sessionExerciseId = row.sessionExerciseId();
            View root = views.getRoot();

            views.setNumber.setText(row.number() == null
                    ? root.getContext().getString(R.string.session_set_warmup_badge)
                    : root.getContext().getString(R.string.session_set_number, row.number()));
            views.planned.setText(row.plannedText());
            views.planned.setVisibility(row.plannedText() == null ? View.GONE : View.VISIBLE);
            views.previous.setText(row.previousText());
            views.previous.setVisibility(row.previousText() == null ? View.GONE : View.VISIBLE);

            views.weightLayout.setVisibility(row.showWeight() ? View.VISIBLE : View.GONE);
            views.weightLayout.setHint(row.weightLabel());
            views.repsLayout.setHint(row.repsLabel());
            setTextIfIdle(views.weightInput, row.weightText());
            setTextIfIdle(views.repsInput, row.repsText());
            // The suggestion is a PLACEHOLDER on the layout, not a hint on the field: the layout
            // already draws "kg" in that exact spot, so two hints were painted on top of each other
            // and neither was readable (found in review).
            views.weightLayout.setPlaceholderText(row.weightHint());
            views.repsLayout.setPlaceholderText(row.repsHint());

            // One reps field or two, never both: showing a combined field beside per-side ones
            // would let the same set be recorded twice in two different meanings.
            views.repsLayout.setVisibility(row.perSide() ? View.GONE : View.VISIBLE);
            views.repsLeftLayout.setVisibility(row.perSide() ? View.VISIBLE : View.GONE);
            views.repsRightLayout.setVisibility(row.perSide() ? View.VISIBLE : View.GONE);
            if (row.perSide()) {
                setTextIfIdle(views.repsLeftInput, row.repsLeftText());
                setTextIfIdle(views.repsRightInput, row.repsRightText());
                views.repsLeftLayout.setPlaceholderText(row.repsLeftHint());
                views.repsRightLayout.setPlaceholderText(row.repsRightHint());
                // "E" and "D" are readable but not speakable; TalkBack gets the whole word.
                views.repsLeftInput.setContentDescription(
                        root.getContext().getString(R.string.session_reps_left_description));
                views.repsRightInput.setContentDescription(
                        root.getContext().getString(R.string.session_reps_right_description));
            }

            views.buttonTechnique.setText(row.badge() == null ? "—" : row.badge());
            views.buttonTechnique.setContentDescription(root.getContext().getString(
                    R.string.session_technique_of_set, number(row)));
            // Every set offers the same words, so the description is what says WHICH set.
            views.buttonAddSegment.setContentDescription(root.getContext().getString(
                    R.string.session_add_segment_of_set, number(row)));

            boolean done = row.done();
            views.buttonDone.setSelected(done);
            views.buttonDone.setContentDescription(root.getContext().getString(
                    done ? R.string.session_undo_set : R.string.session_confirm_set, number(row)));
            // "feita" as a WORD, because setSelected() alone changes nothing the user can see: the
            // stock icon button reacts to enabled/disabled, not to selected (found in review).
            views.doneLabel.setVisibility(done ? View.VISIBLE : View.GONE);
            views.setNumber.setContentDescription(done
                    ? root.getContext().getString(R.string.session_set_number, number(row)) + ", "
                    + root.getContext().getString(R.string.session_set_done)
                    : null);
            // A confirmed set is not editable: typing into it used to show a number that was never
            // written to disk. Undo first, then correct it.
            views.weightInput.setEnabled(!done);
            views.repsInput.setEnabled(!done);
            views.repsLeftInput.setEnabled(!done);
            views.repsRightInput.setEnabled(!done);
            views.weightLayout.setHelperText(done
                    ? root.getContext().getString(R.string.session_set_done_hint) : null);

            views.buttonRemove.setVisibility(row.removable() ? View.VISIBLE : View.GONE);
            views.buttonRemove.setContentDescription(root.getContext().getString(
                    R.string.session_remove_set, number(row)));
            binding = false;
        }

        private static int number(SessionRow.SetRow row) {
            return spokenNumber(row.number());
        }
    }

    /**
     * A drop under its set. Built like {@link SetHolder} and for the same reasons: the watchers
     * are installed once and stay quiet while a row is bound, a field that has focus is never
     * written into, and the typed text lives in the ViewModel. A recycled holder that got any of
     * that wrong would type one row's numbers into another, which is the bug those rules exist for.
     */
    static final class SegmentHolder extends RecyclerView.ViewHolder {
        private final ItemSessionSegmentBinding views;
        private boolean binding;
        @Nullable
        private String segmentId;

        SegmentHolder(ItemSessionSegmentBinding views, Callbacks callbacks) {
            super(views.getRoot());
            this.views = views;
            // Installed once: a watcher added on every bind would fire for the previous row's text.
            views.segmentWeightInput.addTextChangedListener(new SimpleWatcher(text -> {
                if (!binding && segmentId != null) {
                    callbacks.onWeightTyped(segmentId, text);
                }
            }));
            views.segmentRepsInput.addTextChangedListener(new SimpleWatcher(text -> {
                if (!binding && segmentId != null) {
                    callbacks.onRepsTyped(segmentId, text);
                }
            }));
            views.segmentWeightInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus && segmentId != null) {
                    callbacks.onFieldDone(segmentId);
                }
            });
            views.segmentRepsInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus && segmentId != null) {
                    callbacks.onFieldDone(segmentId);
                }
            });
            views.segmentRepsLeftInput.addTextChangedListener(new SimpleWatcher(text -> {
                if (!binding && segmentId != null) {
                    callbacks.onRepsLeftTyped(segmentId, text);
                }
            }));
            views.segmentRepsRightInput.addTextChangedListener(new SimpleWatcher(text -> {
                if (!binding && segmentId != null) {
                    callbacks.onRepsRightTyped(segmentId, text);
                }
            }));
            views.segmentRepsLeftInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus && segmentId != null) {
                    callbacks.onFieldDone(segmentId);
                }
            });
            views.segmentRepsRightInput.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus && segmentId != null) {
                    callbacks.onFieldDone(segmentId);
                }
            });
            views.segmentButtonDone.setOnClickListener(v -> {
                if (segmentId == null) {
                    return;
                }
                if (views.segmentButtonDone.isSelected()) {
                    callbacks.onUndo(segmentId);
                } else {
                    callbacks.onConfirm(segmentId);
                }
            });
            views.segmentButtonRemove.setOnClickListener(v -> {
                if (segmentId != null) {
                    callbacks.onRemoveSegment(segmentId);
                }
            });
        }

        void bind(SessionRow.Segment row) {
            binding = true;
            segmentId = row.id();
            View root = views.getRoot();

            String label = root.getContext().getString(R.string.session_segment_label, row.index());
            views.segmentLabel.setText(label);

            views.segmentWeightLayout.setVisibility(row.showWeight() ? View.VISIBLE : View.GONE);
            views.segmentWeightLayout.setHint(row.weightLabel());
            views.segmentRepsLayout.setHint(row.repsLabel());
            setTextIfIdle(views.segmentWeightInput, row.weightText());
            setTextIfIdle(views.segmentRepsInput, row.repsText());
            // A PLACEHOLDER on the layout, never a hint on the field: see SetHolder.bind.
            views.segmentWeightLayout.setPlaceholderText(row.weightHint());
            views.segmentRepsLayout.setPlaceholderText(row.repsHint());

            // One reps field or two, never both: see SetHolder.bind.
            views.segmentRepsLayout.setVisibility(row.perSide() ? View.GONE : View.VISIBLE);
            views.segmentRepsLeftLayout.setVisibility(row.perSide() ? View.VISIBLE : View.GONE);
            views.segmentRepsRightLayout.setVisibility(row.perSide() ? View.VISIBLE : View.GONE);
            if (row.perSide()) {
                setTextIfIdle(views.segmentRepsLeftInput, row.repsLeftText());
                setTextIfIdle(views.segmentRepsRightInput, row.repsRightText());
                views.segmentRepsLeftLayout.setPlaceholderText(row.repsLeftHint());
                views.segmentRepsRightLayout.setPlaceholderText(row.repsRightHint());
                // "E" and "D" are readable but not speakable; TalkBack gets the whole word.
                views.segmentRepsLeftInput.setContentDescription(
                        root.getContext().getString(R.string.session_reps_left_description));
                views.segmentRepsRightInput.setContentDescription(
                        root.getContext().getString(R.string.session_reps_right_description));
            }

            boolean done = row.done();
            int setNumber = spokenNumber(row.setNumber());
            views.segmentButtonDone.setSelected(done);
            views.segmentButtonDone.setContentDescription(root.getContext().getString(
                    done ? R.string.session_undo_segment : R.string.session_confirm_segment,
                    row.index(), setNumber));
            // "feita" as a WORD, for the same reason as on a set: selection alone draws nothing.
            views.segmentDoneLabel.setVisibility(done ? View.VISIBLE : View.GONE);
            views.segmentLabel.setContentDescription(done
                    ? label + ", " + root.getContext().getString(R.string.session_set_done)
                    : null);
            // A confirmed drop is not editable, exactly like a confirmed set: undo first.
            views.segmentWeightInput.setEnabled(!done);
            views.segmentRepsInput.setEnabled(!done);
            views.segmentRepsLeftInput.setEnabled(!done);
            views.segmentRepsRightInput.setEnabled(!done);
            views.segmentWeightLayout.setHelperText(done
                    ? root.getContext().getString(R.string.session_set_done_hint) : null);

            views.segmentButtonRemove.setContentDescription(root.getContext().getString(
                    R.string.session_remove_segment, row.index(), setNumber));
            binding = false;
        }
    }

    static final class AddHolder extends RecyclerView.ViewHolder {
        private final ItemSessionAddSetBinding views;

        AddHolder(ItemSessionAddSetBinding views) {
            super(views.getRoot());
            this.views = views;
        }

        void bind(SessionRow.AddSet row, Callbacks callbacks) {
            views.buttonAddSet.setContentDescription(views.getRoot().getContext()
                    .getString(R.string.session_add_set) + ": " + row.exerciseName());
            views.buttonAddSet.setOnClickListener(v -> callbacks.onAddSet(row.sessionExerciseId()));
        }
    }

    /** A warm-up has no number: it is spoken as 1, as every description of a set already did. */
    private static int spokenNumber(@Nullable Integer number) {
        return number == null ? 1 : number;
    }

    /** Never writes into a field the user is typing in, and never re-sets the same text. */
    private static void setTextIfIdle(android.widget.EditText field, @Nullable String text) {
        String value = text == null ? "" : text;
        if (field.hasFocus() || value.contentEquals(field.getText())) {
            return;
        }
        field.setText(value);
    }

    /** TextWatcher with only the callback that matters. */
    private static final class SimpleWatcher implements TextWatcher {
        private final java.util.function.Consumer<String> onChanged;

        SimpleWatcher(java.util.function.Consumer<String> onChanged) {
            this.onChanged = onChanged;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            onChanged.accept(s.toString());
        }
    }

    private static final DiffUtil.ItemCallback<SessionRow> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull SessionRow oldItem, @NonNull SessionRow newItem) {
            return oldItem.id().equals(newItem.id());
        }

        @Override
        public boolean areContentsTheSame(@NonNull SessionRow oldItem, @NonNull SessionRow newItem) {
            return Objects.equals(oldItem, newItem);
        }
    };
}
