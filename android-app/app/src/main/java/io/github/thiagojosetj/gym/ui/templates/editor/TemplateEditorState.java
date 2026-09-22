package io.github.thiagojosetj.gym.ui.templates.editor;

import java.util.List;

import io.github.thiagojosetj.gym.domain.template.TemplateDraft;

/**
 * Everything the editor screen draws.
 *
 * @param errors validation problems, only filled after the user tried to save
 */
public record TemplateEditorState(
        Status status,
        boolean isNew,
        String name,
        String description,
        String notes,
        List<TemplateExerciseItem> exercises,
        List<TemplateDraft.Error> errors) {

    public enum Status { LOADING, READY, SAVING, LOAD_FAILED }

    public boolean editable() {
        return status == Status.READY;
    }
}
