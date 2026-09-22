package io.github.thiagojosetj.gym.ui.templates.editor;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import io.github.thiagojosetj.gym.databinding.ItemEditorHeaderBinding;
import io.github.thiagojosetj.gym.domain.template.TemplateRules;

/**
 * The template's own fields (name, description, notes) as the first row of the editor list.
 *
 * <p>Updates use a payload so RecyclerView rebinds the SAME view holder: without it, the default
 * change animation would swap views and the keyboard would lose focus while typing.
 */
final class EditorHeaderAdapter extends RecyclerView.Adapter<EditorHeaderAdapter.Holder> {

    record Header(String name, String description, String notes, @Nullable String nameError, boolean enabled) {
    }

    private static final Object PAYLOAD_STATE = new Object();

    private final Consumer<String> onNameChanged;
    private final Consumer<String> onDescriptionChanged;
    private final Consumer<String> onNotesChanged;
    private Header header = new Header("", null, null, null, false);

    EditorHeaderAdapter(Consumer<String> onNameChanged, Consumer<String> onDescriptionChanged,
                        Consumer<String> onNotesChanged) {
        this.onNameChanged = onNameChanged;
        this.onDescriptionChanged = onDescriptionChanged;
        this.onNotesChanged = onNotesChanged;
    }

    void submit(Header next) {
        if (!next.equals(header)) {
            header = next;
            notifyItemChanged(0, PAYLOAD_STATE);
        }
    }

    @Override
    public int getItemCount() {
        return 1;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemEditorHeaderBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(header);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position, @NonNull List<Object> payloads) {
        holder.bind(header); // same work: bind() only touches views whose value changed
    }

    final class Holder extends RecyclerView.ViewHolder {

        private final ItemEditorHeaderBinding binding;
        private boolean updatingViews;

        Holder(ItemEditorHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.nameLayout.setCounterMaxLength(TemplateRules.MAX_NAME_LENGTH);
            watch(binding.nameInput, onNameChanged);
            watch(binding.descriptionInput, onDescriptionChanged);
            watch(binding.notesInput, onNotesChanged);
        }

        void bind(Header h) {
            updatingViews = true;
            setIfDifferent(binding.nameInput, h.name());
            setIfDifferent(binding.descriptionInput, h.description());
            setIfDifferent(binding.notesInput, h.notes());
            updatingViews = false;
            binding.nameLayout.setError(h.nameError());
            binding.nameInput.setEnabled(h.enabled());
            binding.descriptionInput.setEnabled(h.enabled());
            binding.notesInput.setEnabled(h.enabled());
        }

        private void watch(EditText input, Consumer<String> listener) {
            input.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (!updatingViews) {
                        listener.accept(s.toString());
                    }
                }
            });
        }

        /** Never resets text that already matches: keeps the cursor where the user left it. */
        private void setIfDifferent(EditText input, @Nullable String value) {
            String target = value == null ? "" : value;
            String current = input.getText() == null ? "" : input.getText().toString();
            // The draft trims values; don't fight trailing spaces the user is still typing.
            if (!Objects.equals(current.trim(), target.trim())) {
                input.setText(target);
            }
        }
    }
}
