package io.github.thiagojosetj.gym.ui.session;

/**
 * What the user has typed into a set but not confirmed yet, kept as raw text.
 *
 * <p>Raw text on purpose: "42," is a valid moment of typing and must not be parsed, rounded or
 * rejected while the finger is still moving. It lives in the ViewModel (so it survives rotation and
 * scrolling) and never in the recycled row.
 */
record SetDraft(String weightText, String repsText, String repsLeftText, String repsRightText) {

    static final SetDraft EMPTY = new SetDraft(null, null, null, null);

    SetDraft withWeight(String text) {
        return new SetDraft(text, repsText, repsLeftText, repsRightText);
    }

    SetDraft withReps(String text) {
        return new SetDraft(weightText, text, repsLeftText, repsRightText);
    }

    /** Left and right are separate fields, so a half-filled set stays visibly half-filled. */
    SetDraft withRepsLeft(String text) {
        return new SetDraft(weightText, repsText, text, repsRightText);
    }

    SetDraft withRepsRight(String text) {
        return new SetDraft(weightText, repsText, repsLeftText, text);
    }

    boolean isEmpty() {
        return blank(weightText) && blank(repsText) && blank(repsLeftText) && blank(repsRightText);
    }

    private static boolean blank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
