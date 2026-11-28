package io.github.thiagojosetj.gym.ui.common;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

import io.github.thiagojosetj.gym.R;

/**
 * A body with one muscle lit on it.
 *
 * <p>Two layers rather than one picture: the silhouette and the region are separate drawables, so
 * each is tinted from the theme here. Baking the colours into the artwork would need a second set
 * of files for dark mode, and 51 muscles is already enough files.
 *
 * <p>Says nothing by itself. The caller sets the content description, because only the caller
 * knows whether this is "Peitoral superior" on a filter or part of a longer sentence; a view that
 * invented its own would make a screen reader repeat the name twice.
 */
public class MuscleMapView extends FrameLayout {

    private final ImageView body;
    private final ImageView region;

    public MuscleMapView(Context context) {
        this(context, null);
    }

    public MuscleMapView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MuscleMapView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        // The attributes belong to Material, not to this app's own R.
        body = layer(context, com.google.android.material.R.attr.colorOutlineVariant);
        region = layer(context, androidx.appcompat.R.attr.colorPrimary);
        addView(body);
        addView(region);
        // Decorative on its own; the caller names it.
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    private ImageView layer(Context context, @AttrRes int colorAttr) {
        ImageView view = new ImageView(context);
        view.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT,
                Gravity.CENTER));
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        @ColorInt int color = MaterialColors.getColor(context, colorAttr, 0);
        view.setImageTintList(ColorStateList.valueOf(color));
        return view;
    }

    /**
     * Shows this muscle, or nothing at all when the catalogue has one the app does not draw.
     * Showing an empty figure instead of a wrong one is the point: a body with no muscle lit says
     * "no picture", while the nearest available muscle would say something false.
     */
    public void setMuscle(@Nullable String muscleCode) {
        MuscleArt.Art art = MuscleArt.of(muscleCode);
        if (art == null) {
            body.setImageDrawable(null);
            region.setImageDrawable(null);
            return;
        }
        body.setImageResource(art.side().body);
        region.setImageResource(art.region());
    }

    /** True when a muscle is actually drawn, so a caller can hide the whole row instead. */
    public boolean hasMuscle() {
        return body.getDrawable() != null;
    }
}
