package io.github.thiagojosetj.gym.ui.history;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.domain.session.TrainingCalendar;

/**
 * The month grid of trained days (PRODUCT_SPEC HIS-02).
 *
 * <p>Draws what it is given and decides nothing: which month, which days have sessions and which
 * one is selected all arrive from outside. The weekday letters, their order and the month's name
 * come from the device locale, so the grid reads the way the rest of the system's dates do.
 */
public class TrainingCalendarView extends LinearLayout {

    /** Material's own disabled opacity for an icon, so the arrow matches the rest of the app. */
    private static final float DISABLED_ALPHA = 0.38f;

    public interface Listener {
        void onDaySelected(LocalDate date);

        void onMonthStep(int months);
    }

    private final TextView title;
    private final LinearLayout weekdayRow;
    private final GridLayout grid;
    private final TextView summary;
    @Nullable
    private Listener listener;

    public TrainingCalendarView(Context context) {
        this(context, null);
    }

    public TrainingCalendarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_training_calendar, this, true);
        title = findViewById(R.id.month_title);
        weekdayRow = findViewById(R.id.weekday_row);
        grid = findViewById(R.id.day_grid);
        summary = findViewById(R.id.calendar_summary);
        findViewById(R.id.previous_month).setOnClickListener(v -> step(-1));
        findViewById(R.id.next_month).setOnClickListener(v -> step(1));
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    /**
     * Which arrows lead anywhere; the caller knows where the first and last sessions are.
     *
     * <p>Dimmed as well as disabled: an arrow that looks live and does nothing reads as a broken
     * app, and nothing else on the square tells the person they have reached the end.
     */
    public void setMonthStepEnabled(boolean back, boolean forward) {
        arrow(R.id.previous_month, back);
        arrow(R.id.next_month, forward);
    }

    private void arrow(int id, boolean enabled) {
        View button = findViewById(id);
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : DISABLED_ALPHA);
    }

    public void render(TrainingCalendar calendar, @Nullable LocalDate selected) {
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        title.setText(calendar.month().format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)));
        renderWeekdays(calendar.firstDayOfWeek(), locale);
        renderDays(calendar, selected, locale);
        summary.setText(getResources().getQuantityString(R.plurals.calendar_trained_days,
                calendar.trainedDays(), calendar.trainedDays()));
    }

    private void renderWeekdays(DayOfWeek firstDayOfWeek, Locale locale) {
        for (int i = 0; i < 7; i++) {
            TextView label;
            if (i < weekdayRow.getChildCount()) {
                label = (TextView) weekdayRow.getChildAt(i);
            } else {
                label = new TextView(getContext());
                label.setLayoutParams(new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
                label.setGravity(android.view.Gravity.CENTER);
                // The row is a legend for the grid below; a screen reader reading seven letters in
                // a row adds nothing to what the day squares already say.
                label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                weekdayRow.addView(label);
            }
            label.setText(firstDayOfWeek.plus(i).getDisplayName(TextStyle.NARROW, locale));
        }
    }

    /**
     * Fills the grid, reusing the squares that are already there.
     *
     * <p>Not {@code removeAllViews()} and forty-two fresh inflations. Every tap on a day redraws
     * this whole grid, and destroying the view that was just tapped takes the accessibility focus
     * with it - a TalkBack user would be thrown back to the top of the screen by the very gesture
     * they meant to use. (That the focus then survives is not proved here; Robolectric does not
     * run TalkBack. What is proved is that the squares really are reused and carry nothing over.)
     *
     * <p>This only has an effect because the history list turns RecyclerView's change animations
     * OFF. Left on, every rebind builds a second holder and cross-fades, so the grid would be
     * inflated again anyway - which is exactly what the test below caught when the setting was
     * missing.
     *
     * <p>Reusing means stale state is the hazard: a square that held a trained day and becomes
     * padding next month must be cleared, or the calendar shows a mark on a square that is not
     * even a day.
     */
    private void renderDays(TrainingCalendar calendar, @Nullable LocalDate selected, Locale locale) {
        DateTimeFormatter spoken = DateTimeFormatter.ofPattern("d 'de' MMMM", locale);
        LayoutInflater inflater = LayoutInflater.from(getContext());
        List<TrainingCalendar.Day> days = calendar.days();
        // A shorter month leaves squares over; February after January, for instance.
        while (grid.getChildCount() > days.size()) {
            grid.removeViewAt(grid.getChildCount() - 1);
        }
        while (grid.getChildCount() < days.size()) {
            grid.addView(newCell(inflater));
        }
        Resources res = getResources();
        for (int i = 0; i < days.size(); i++) {
            bind((TextView) grid.getChildAt(i), days.get(i), selected, spoken, res);
        }
    }

    private TextView newCell(LayoutInflater inflater) {
        TextView cell = (TextView) inflater.inflate(R.layout.item_calendar_day, grid, false);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams(cell.getLayoutParams());
        params.width = 0;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        cell.setLayoutParams(params);
        return cell;
    }

    private void bind(TextView cell, TrainingCalendar.Day day, @Nullable LocalDate selected,
                      DateTimeFormatter spoken, Resources res) {
        if (day.isBlank()) {
            // A padding square is not a day: it says nothing and answers nothing.
            cell.setText("");
            cell.setActivated(false);
            cell.setSelected(false);
            cell.setClickable(false);
            cell.setFocusable(false);
            cell.setOnClickListener(null);
            cell.setContentDescription(null);
            cell.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            return;
        }
        LocalDate date = day.date();
        cell.setText(String.valueOf(date.getDayOfMonth()));
        cell.setActivated(day.trained());
        cell.setSelected(Objects.equals(date, selected));
        cell.setClickable(true);
        cell.setFocusable(true);
        cell.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        // "14" alone does not say which month it is in, nor whether anything happened on it.
        cell.setContentDescription(res.getString(
                day.trained() ? R.string.calendar_day_trained : R.string.calendar_day_free,
                spoken.format(date)));
        cell.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDaySelected(date);
            }
        });
    }

    private void step(int months) {
        if (listener != null) {
            listener.onMonthStep(months);
        }
    }
}
