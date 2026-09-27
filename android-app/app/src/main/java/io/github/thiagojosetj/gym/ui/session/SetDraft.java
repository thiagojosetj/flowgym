package io.github.thiagojosetj.gym.ui.session;

/**
 * What the user has typed into a set but not confirmed yet, kept as raw text.
 *
 * <p>Raw text on purpose: "42," is a valid moment of typing and must not be parsed, rounded or
 * rejected while the finger is still moving. It lives in the ViewModel (so it survives rotation and
 * scrolling) and never in the recycled row.
 */
record SetDraft(String weightText, String repsText) {

    static final SetDraft EMPTY = new SetDraft(null, null);

    SetDraft withWeight(String text) {
        return new SetDraft(text, repsText);
    }

    SetDraft withReps(String text) {
        return new SetDraft(weightText, text);
    }

    boolean isEmpty() {
        return blank(weightText) && blank(repsText);
    }

    private static boolean blank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
