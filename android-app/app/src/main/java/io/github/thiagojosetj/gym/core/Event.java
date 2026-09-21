package io.github.thiagojosetj.gym.core;

/**
 * One-shot UI event carried through LiveData (ARCHITECTURE §5.3).
 *
 * <p>LiveData re-delivers its last value to new observers, e.g. after a rotation. Wrapping
 * "navigate back" or "show snackbar" in an Event makes sure it is handled only once.
 */
public final class Event<T> {

    private final T content;
    private boolean handled;

    public Event(T content) {
        this.content = content;
    }

    /** Returns the content the first time; null afterwards. */
    public T consume() {
        if (handled) {
            return null;
        }
        handled = true;
        return content;
    }

    public T peek() {
        return content;
    }
}
