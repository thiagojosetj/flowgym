package io.github.thiagojosetj.gym.ui.progress;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One measure of one exercise across its sessions (PRODUCT_SPEC PRG-01), drawn on a canvas.
 *
 * <p>Why drawn and not a library: ADR-0042. What it is allowed to look like is in that ADR too,
 * and the parts that are easy to get wrong are these:
 *
 * <p><b>One series, one colour.</b> There is never a second line here, so there is no pair of
 * hues for anybody to tell apart - which is just as well, because this app's primary and tertiary
 * measured as a FAILING pair for normal colour vision (ADR-0042). The point that holds a record is
 * a bigger marker with a ring, and a line of text under the chart says what the bigger marker
 * means. Shape and words, never colour alone.
 *
 * <p><b>No area fill.</b> The y axis does not start at zero - a 40 kg to 45 kg change would be
 * invisible if it did - so a wash down to the baseline would shade an area that stands for
 * nothing. The axis labels say where it does start, which is why they are not optional.
 *
 * <p><b>The grid is deliberately faint</b> (about 1.5:1 against the surface). A grid is not data.
 *
 * <p><b>Touch instead of hover.</b> A phone has no hover, so a tap anywhere selects the nearest
 * session by x - the whole column is the target, not the 8dp dot. Nothing here is the only way to
 * read a value: the session list below is the same numbers as text (ADR-0042).
 */
public class SessionChartView extends View {

    /** One session's value for the measure on screen. */
    public record Point(long value, String day, boolean record) {
    }

    /** Turns a raw value into the text the axis and the labels show. */
    public interface ValueFormatter {
        String format(long value);
    }

    public interface OnPointSelectedListener {
        void onPointSelected(int index);
    }

    /** Taken from the mark specs in ADR-0042; dp, converted once per layout. */
    private static final float PLOT_HEIGHT_DP = 150f;
    private static final float LINE_WIDTH_DP = 2f;
    private static final float MARKER_RADIUS_DP = 4f;
    /** The halo that marks a record, measured from the dot's own edge. */
    private static final float HALO_GAP_DP = 4f;
    private static final float SELECTED_RADIUS_DP = 7f;
    private static final float RING_WIDTH_DP = 2f;
    private static final float LABEL_SP = 11f;
    private static final float GAP_DP = 6f;
    /** How many horizontal gridlines to aim for. Three or four reads; eight is wallpaper. */
    private static final int TARGET_TICKS = 3;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint();
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();

    private List<Point> points = Collections.emptyList();
    private ValueFormatter formatter = String::valueOf;
    @Nullable
    private OnPointSelectedListener listener;
    private int selected = -1;
    private long axisMin;
    private long axisMax;
    private long axisStep = 1L;

    public SessionChartView(Context context) {
        this(context, null);
    }

    public SessionChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        // colorPrimary is declared by AppCompat and the Material 3 roles by Material, so the two
        // namespaces here are not an inconsistency - they are where each attribute lives.
        int series = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary);
        int ink = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant);
        int grid = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant);
        // The ring is the surface showing through, so it has to BE the surface the chart sits on.
        int surface = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurfaceContainerLow);

        linePaint.setColor(series);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(LINE_WIDTH_DP));
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        markerPaint.setColor(series);
        markerPaint.setStyle(Paint.Style.FILL);

        ringPaint.setColor(surface);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(RING_WIDTH_DP));

        haloPaint.setColor(series);
        haloPaint.setStyle(Paint.Style.STROKE);
        haloPaint.setStrokeWidth(dp(RING_WIDTH_DP));

        // Solid and one shade off the surface. Dashed reads as "projection" when it is just a grid.
        gridPaint.setColor(grid);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);

        // Text wears text colour, never the series colour.
        textPaint.setColor(ink);
        textPaint.setTextSize(sp(LABEL_SP));

        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setFocusable(true);
    }

    /**
     * Replaces what is drawn.
     *
     * @param points oldest first, which is the order a chart reads
     */
    public void setPoints(List<Point> points, ValueFormatter formatter) {
        this.points = points == null ? Collections.<Point>emptyList() : new ArrayList<>(points);
        this.formatter = formatter == null ? String::valueOf : formatter;
        // The newest session is selected to begin with: it is the one somebody opening this screen
        // is asking about, and it means the readout below is never an empty line.
        this.selected = this.points.isEmpty() ? -1 : this.points.size() - 1;
        computeAxis();
        requestLayout();
        invalidate();
    }

    public void setOnPointSelectedListener(@Nullable OnPointSelectedListener listener) {
        this.listener = listener;
    }

    public int selectedIndex() {
        return selected;
    }

    /**
     * The y range: nice round numbers bracketing the data, never forced to zero.
     *
     * <p>Zero would flatten the only thing a load chart is for - five kilos on a bench press is a
     * real month of work and would be two pixels on a 0-to-45 axis. The price is that the axis has
     * to be labelled, and it is.
     */
    private void computeAxis() {
        if (points.isEmpty()) {
            axisMin = 0L;
            axisMax = 1L;
            axisStep = 1L;
            return;
        }
        long min = points.get(0).value();
        long max = min;
        for (Point point : points) {
            min = Math.min(min, point.value());
            max = Math.max(max, point.value());
        }
        axisStep = niceStep(max - min);
        axisMin = Math.floorDiv(min, axisStep) * axisStep;
        axisMax = -Math.floorDiv(-max, axisStep) * axisStep;
        if (axisMax <= axisMin) {
            // Every session identical: one line through the middle rather than a divide by zero.
            axisMin -= axisStep;
            axisMax += axisStep;
        }
    }

    /** 1, 2 or 5 times a power of ten - the steps people read without doing arithmetic. */
    private static long niceStep(long span) {
        if (span <= 0L) {
            return 1L;
        }
        double raw = (double) span / TARGET_TICKS;
        double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
        double normalised = raw / magnitude;
        double step = normalised <= 1d ? 1d : normalised <= 2d ? 2d : normalised <= 5d ? 5d : 10d;
        return Math.max(1L, (long) (step * magnitude));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Sized to include the axis band, not just the plot: a container that fits only the plot
        // is how a chart ends up with its own little scrollbar.
        int height = (int) (dp(PLOT_HEIGHT_DP) + topInset() + bottomInset());
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                resolveSize(height, heightMeasureSpec));
    }

    /**
     * How much room the widest marker needs beside the plot.
     *
     * <p>The first and last sessions sit ON the plot's edges, so without this the dot that marks
     * the newest session - the one somebody opened the screen to see - is sliced in half by the
     * right-hand border. The ring counts: it is part of the mark, not spacing around it.
     */
    private float markerInset() {
        return dp(SELECTED_RADIUS_DP) + dp(HALO_GAP_DP) + dp(RING_WIDTH_DP);
    }

    /**
     * The band above the plot: a value label, clear of the marker it belongs to.
     *
     * <p>Used by {@code onMeasure} and by {@code onDraw} alike, because a height that reserves
     * less than the drawing uses is exactly how a label ends up sliced by the top edge - which is
     * what the first render of this chart did.
     */
    private float topInset() {
        return labelHeight() + markerInset() + dp(GAP_DP) / 2f;
    }

    /** The band below: the day labels, clear of a marker sitting on the bottom gridline. */
    private float bottomInset() {
        return labelHeight() + dp(GAP_DP) + markerInset();
    }

    private float labelHeight() {
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        return metrics.descent - metrics.ascent;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (points.isEmpty()) {
            return;
        }
        float labels = labelHeight();
        // Room on the left for the y labels, below for the days, above for the direct labels.
        float left = widestAxisLabel() + markerInset();
        float right = getWidth() - markerInset();
        float top = topInset();
        float bottom = getHeight() - bottomInset();
        if (right <= left || bottom <= top) {
            return;
        }

        drawGrid(canvas, left, right, top, bottom);
        drawSeries(canvas, left, right, top, bottom);
        drawDayLabels(canvas, left, right, bottom, labels);
    }

    private void drawGrid(Canvas canvas, float left, float right, float top, float bottom) {
        for (long value = axisMin; value <= axisMax; value += axisStep) {
            float y = yOf(value, top, bottom);
            canvas.drawLine(left, y, right, y, gridPaint);
            String text = formatter.format(value);
            // Right-aligned against the plot, so the numbers line up with each other.
            canvas.drawText(text, left - dp(GAP_DP) - textPaint.measureText(text),
                    y + labelHeight() / 3f, textPaint);
        }
    }

    private void drawSeries(Canvas canvas, float left, float right, float top, float bottom) {
        linePath.reset();
        for (int i = 0; i < points.size(); i++) {
            float x = xOf(i, left, right);
            float y = yOf(points.get(i).value(), top, bottom);
            if (i == 0) {
                linePath.moveTo(x, y);
            } else {
                linePath.lineTo(x, y);
            }
        }
        // One session draws no line: there is a point, and no trend to imply.
        if (points.size() > 1) {
            canvas.drawPath(linePath, linePaint);
        }

        int extreme = indexOfMax();
        for (int i = 0; i < points.size(); i++) {
            Point point = points.get(i);
            float x = xOf(i, left, right);
            float y = yOf(point.value(), top, bottom);
            float radius = dp(i == selected ? SELECTED_RADIUS_DP : MARKER_RADIUS_DP);
            if (i == selected) {
                canvas.drawLine(x, top, x, bottom, gridPaint);
            }
            // Ring first, then the dot on top: the ring is the surface separating the marker from
            // the line it sits on.
            canvas.drawCircle(x, y, radius, ringPaint);
            canvas.drawCircle(x, y, radius, markerPaint);
            // A record is a HALO, not a slightly bigger dot. Two sizes of circle is a difference
            // nobody spots, and it would also collide with the selected marker's own size - which
            // is transient, while a record is the thing worth seeing.
            if (point.record()) {
                canvas.drawCircle(x, y, radius + dp(HALO_GAP_DP), haloPaint);
            }
            // Labelled selectively - the extreme and the last point. A number on every dot is
            // chaos and goes unread.
            if (i == extreme || i == points.size() - 1) {
                // Kept clear of the halo, so a record's label never sits on its own ring.
                drawValueLabel(canvas, point.value(), x, y, left, right);
            }
        }
    }

    private void drawValueLabel(Canvas canvas, long value, float x, float y, float left,
                                float right) {
        String text = formatter.format(value);
        float width = textPaint.measureText(text);
        // Nudged inside the plot rather than clipped: a label cropped by the edge is worse than no
        // label, and the value is in the list below either way.
        float textX = Math.max(left, Math.min(x - width / 2f, right - width));
        canvas.drawText(text, textX, y - markerInset() - dp(GAP_DP) / 2f, textPaint);
    }

    /**
     * Only the ends are named.
     *
     * <p>A day under every point would overlap itself by the fourth session, and the selected
     * session's own day is spelled out in the line of text under the chart.
     */
    private void drawDayLabels(Canvas canvas, float left, float right, float bottom,
                               float labels) {
        float baseline = bottom + labels;
        canvas.drawText(points.get(0).day(), left, baseline, textPaint);
        if (points.size() > 1) {
            String last = points.get(points.size() - 1).day();
            canvas.drawText(last, right - textPaint.measureText(last), baseline, textPaint);
        }
    }

    private float widestAxisLabel() {
        float widest = 0f;
        for (long value = axisMin; value <= axisMax; value += axisStep) {
            widest = Math.max(widest, textPaint.measureText(formatter.format(value)));
        }
        return widest;
    }

    private int indexOfMax() {
        int index = 0;
        for (int i = 1; i < points.size(); i++) {
            if (points.get(i).value() > points.get(index).value()) {
                index = i;
            }
        }
        return index;
    }

    private float xOf(int index, float left, float right) {
        if (points.size() == 1) {
            return (left + right) / 2f;
        }
        return left + (right - left) * index / (points.size() - 1);
    }

    private float yOf(long value, float top, float bottom) {
        float span = axisMax - axisMin;
        float ratio = span == 0f ? 0.5f : (value - axisMin) / span;
        return bottom - (bottom - top) * ratio;
    }

    /**
     * The nearest session by x, from a tap anywhere in its column.
     *
     * <p>Not "land on the 8dp dot": a pinpoint target on a phone is a target nobody hits.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (points.isEmpty()) {
            return false;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP) {
            // An accessibility service cannot tap a coordinate, so a click with no position has
            // no session to select; it still goes through performClick, because a view that
            // answers touch and never calls it is unreachable from those services altogether.
            // What makes the data reachable is this view's spoken description and the list of
            // sessions below it, which is the same numbers as text.
            performClick();
            return true;
        }
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_MOVE) {
            return super.onTouchEvent(event);
        }
        float left = widestAxisLabel() + markerInset();
        float right = getWidth() - markerInset();
        int index = 0;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            float distance = Math.abs(event.getX() - xOf(i, left, right));
            if (distance < best) {
                best = distance;
                index = i;
            }
        }
        if (index != selected) {
            selected = index;
            invalidate();
            if (listener != null) {
                listener.onPointSelected(index);
            }
        }
        if (action == MotionEvent.ACTION_DOWN && getParent() != null) {
            // The chart lives in a scroll view: without this, dragging along it scrolls the page
            // instead of moving the selection.
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                getResources().getDisplayMetrics());
    }
}
