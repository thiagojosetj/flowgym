package io.github.thiagojosetj.gym.ui.templates.editor;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import io.github.thiagojosetj.gym.databinding.ItemEditorFooterBinding;

/** Last row of the editor: empty-state hint and the "add exercises" button. */
final class EditorFooterAdapter extends RecyclerView.Adapter<EditorFooterAdapter.Holder> {

    private final Runnable onAdd;
    private boolean showEmptyHint = true;
    private boolean enabled;

    EditorFooterAdapter(Runnable onAdd) {
        this.onAdd = onAdd;
    }

    void submit(boolean showEmptyHint, boolean enabled) {
        if (this.showEmptyHint != showEmptyHint || this.enabled != enabled) {
            this.showEmptyHint = showEmptyHint;
            this.enabled = enabled;
            notifyItemChanged(0);
        }
    }

    @Override
    public int getItemCount() {
        return 1;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemEditorFooterBinding binding =
                ItemEditorFooterBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        binding.buttonAdd.setOnClickListener(v -> onAdd.run());
        return new Holder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.binding.emptyHint.setVisibility(showEmptyHint ? View.VISIBLE : View.GONE);
        holder.binding.buttonAdd.setEnabled(enabled);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemEditorFooterBinding binding;

        Holder(ItemEditorFooterBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
