package io.github.thiagojosetj.gym.ui.progress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.testutil.TestGymApplication;

/**
 * The chart of PRG-01, drawn for real and then looked at (ADR-0042).
 *
 * <p>{@code GraphicsMode.NATIVE} is what makes this possible: Robolectric's legacy graphics draw
 * nothing, so a test under it would pass against a blank canvas. These tests read the pixels, so
 * they fail if the drawing stops happening.
 *
 * <p>They do <b>not</b> prove the chart on a phone. Robolectric measures text at one pixel per
 * character, so nothing here says a label fits next to a real glyph - that check is in the
 * roadmap, against the device.
 */
@RunWith(AndroidJUnit4.class)
@Config(application = TestGymApplication.class, qualifiers = "pt-rBR-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SessionChartViewTest {

    private static final int WIDTH = 379;
    /**
     * Blank margin drawn around the chart, so the chart can be caught painting outside itself.
     *
     * <p>A bitmap exactly the size of the view hides the bug it is meant to catch: a label placed
     * above the view's own top lands off the bitmap entirely, every pixel of the image is still
     * clean, and the test passes while the label is nowhere. Given room to spill, it spills.
     */
    private static final int PAD = 40;
    private static final int LIGHT_SURFACE = 0xFFF1F4F2;
    private static final int DARK_SURFACE = 0xFF1B211F;
    /** The brand teal of values/colors.xml, and its night counterpart. */
    private static final int LIGHT_SERIES = 0xFF006B5A;
    private static final int DARK_SERIES = 0xFF82D5BF;

    @Test
    public void theChartDrawsNothingOutsideItself() {
        SessionChartView chart = laidOut(context());

        Bitmap bitmap = draw(chart, LIGHT_SURFACE);

        // The two defects this caught, both found by rendering the chart and looking at it: the
        // newest session's marker sliced in half by the right edge, and the value label of the
        // highest point drawn past the top. A view whose measured height excludes its own labels
        // is how a chart ends up with a little scrollbar of its own, or with a missing label.
        assertNothingOutside(bitmap, LIGHT_SURFACE, chart.getMeasuredHeight());
    }

    @Test
    public void theSeriesWearsTheThemeAndNotAHardcodedColour() {
        Bitmap light = render(LIGHT_SURFACE, "light");
        RuntimeEnvironment.setQualifiers("+night");
        Bitmap dark = render(DARK_SURFACE, "dark");

        // Dark mode is the palette's own night step, not a flipped light one.
        assertTrue("a linha devia usar o teal do tema claro", countOf(light, LIGHT_SERIES) > 100);
        assertTrue("e o teal do tema escuro no escuro", countOf(dark, DARK_SERIES) > 100);
        assertEquals("nada da cor do tema escuro no claro", 0, countOf(light, DARK_SERIES));
        assertNotEquals(LIGHT_SERIES, DARK_SERIES);
    }

    @Test
    public void aTapSelectsTheNearestSessionByColumnAndNotTheNearestDot() {
        SessionChartView chart = laidOut(context());
        AtomicInteger selected = new AtomicInteger(-1);
        chart.setOnPointSelectedListener(selected::set);

        // Every tap here is at y = 2: along the top edge, nowhere near the line. A phone has no
        // hover and nobody lands on an 8dp dot, so the whole column is the target and the y is
        // not part of the answer.
        //
        // Asserted at the ends and in the middle rather than at a computed pixel: where the
        // plot's edges fall depends on how wide the axis labels measure, and a test that
        // hardcoded that would be testing the font.
        tap(chart, 0f, 2f);
        assertEquals("o extremo esquerdo e a sessao mais antiga", 0, chart.selectedIndex());
        assertEquals(0, selected.get());

        tap(chart, WIDTH, 2f);
        assertEquals("e o direito, a mais nova", 7, chart.selectedIndex());
        assertEquals(7, selected.get());

        tap(chart, WIDTH / 2f, 2f);
        int middle = chart.selectedIndex();
        assertTrue("o meio da tela cai no meio da serie, e nao numa ponta: " + middle,
                middle == 3 || middle == 4);
    }

    @Test
    public void aHistoryWithEverySessionIdenticalStillDraws() {
        // Same load every week: the value range is zero wide, and dividing by it would either
        // crash or draw nothing. One flat line through the middle is the honest picture.
        SessionChartView chart = new SessionChartView(context());
        List<SessionChartView.Point> points = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            points.add(new SessionChartView.Point(40_000L, "d" + i, false));
        }
        chart.setPoints(points, value -> (value / 1000) + " kg");

        measureAndLayout(chart);
        Bitmap bitmap = draw(chart, LIGHT_SURFACE);

        assertTrue("a linha devia estar la", countOf(bitmap, LIGHT_SERIES) > 100);
        assertNothingOutside(bitmap, LIGHT_SURFACE, chart.getMeasuredHeight());
    }

    // ------------------------------------------------------------------ helpers

    /** The margin around the chart is untouched: every mark and label is inside the view. */
    private static void assertNothingOutside(Bitmap bitmap, int surface, int height) {
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                boolean inside = x >= PAD && x < PAD + WIDTH && y >= PAD && y < PAD + height;
                if (!inside) {
                    assertEquals("pintou fora da view em " + x + "," + y, surface,
                            bitmap.getPixel(x, y));
                }
            }
        }
    }

    private static int countOf(Bitmap bitmap, int colour) {
        int count = 0;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                if (bitmap.getPixel(x, y) == colour) {
                    count++;
                }
            }
        }
        return count;
    }

    private static Context context() {
        return new ContextThemeWrapper(ApplicationProvider.getApplicationContext(),
                R.style.Theme_Gym);
    }

    private Bitmap render(int surface, String name) {
        SessionChartView chart = laidOut(context());
        Bitmap bitmap = draw(chart, surface);
        savePreview(bitmap, surface, chart.getMeasuredHeight(), name);
        return bitmap;
    }

    /** Eight weeks of a bench press, the record in the middle so its halo can be seen. */
    private static SessionChartView laidOut(Context context) {
        SessionChartView chart = new SessionChartView(context);
        long[] grams = {40_000, 40_000, 42_500, 45_000, 42_500, 47_500, 45_000, 50_000};
        String[] days = {"2 jun.", "9 jun.", "16 jun.", "23 jun.", "30 jun.", "7 jul.", "14 jul.",
                "21 jul."};
        List<SessionChartView.Point> points = new ArrayList<>();
        for (int i = 0; i < grams.length; i++) {
            points.add(new SessionChartView.Point(grams[i], days[i], i == 5));
        }
        chart.setPoints(points, value -> (value / 1000) + " kg");
        return measureAndLayout(chart);
    }

    /** Named for what it does: without this the view has no width and every x is the same x. */
    private static SessionChartView measureAndLayout(SessionChartView chart) {
        chart.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        chart.layout(0, 0, WIDTH, chart.getMeasuredHeight());
        return chart;
    }

    /** Drawn with a blank margin all round, so anything painted outside the view shows up. */
    private static Bitmap draw(SessionChartView chart, int surface) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH + PAD * 2,
                Math.max(1, chart.getMeasuredHeight()) + PAD * 2, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(surface);
        canvas.translate(PAD, PAD);
        chart.draw(canvas);
        return bitmap;
    }

    private static void tap(SessionChartView chart, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, x, y, 0);
        chart.onTouchEvent(event);
        event.recycle();
    }

    /**
     * Writes the rendered chart next to the build output, for a person to look at.
     *
     * <p>A convenience and never an assertion: if it cannot be written the test carries on. The
     * assertions above are about pixels, not about this file.
     */
    private static void savePreview(Bitmap padded, int surface, int height, String name) {
        try {
            File directory = new File("build/chart-previews");
            if (!directory.isDirectory() && !directory.mkdirs()) {
                return;
            }
            // Cropped back to the view itself: the margin is for the assertion, not for looking.
            Bitmap bitmap = Bitmap.createBitmap(padded, PAD, PAD, WIDTH, Math.max(1, height));
            try (FileOutputStream stream =
                         new FileOutputStream(new File(directory, "chart-" + name + ".png"))) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            }
        } catch (Exception ignored) {
            // A preview nobody can write is not a failing chart.
        }
    }
}
