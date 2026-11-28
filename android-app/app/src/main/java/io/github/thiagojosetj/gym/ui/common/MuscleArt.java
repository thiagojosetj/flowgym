package io.github.thiagojosetj.gym.ui.common;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

import io.github.thiagojosetj.gym.R;

/**
 * Which picture shows a muscle, and from which side.
 *
 * <p>The table is written out rather than resolved from the code at runtime
 * ({@code Resources.getIdentifier}): a name assembled from a string is invisible to R8, which
 * would either strip the drawable or force keeping every resource in the app, and a missing one
 * would fail silently on the device instead of at build time. Written like this, deleting a
 * drawable stops the build.
 *
 * <p>A group's picture is the union of its subgroups', so {@code chest} lights what
 * {@code chest.upper}, {@code chest.middle} and {@code chest.lower} light. See
 * {@code tools/musclemap.py}, which draws all of it.
 */
public final class MuscleArt {

    /** The silhouette the region is painted on. */
    public enum Side {
        FRONT(R.drawable.muscle_body_front),
        BACK(R.drawable.muscle_body_back);

        @DrawableRes
        public final int body;

        Side(@DrawableRes int body) {
            this.body = body;
        }
    }

    /** A muscle's picture: the figure, and the region lit on it. */
    public record Art(Side side, @DrawableRes int region) {
    }

    private static final Map<String, Art> BY_CODE = new HashMap<>();

    private static void put(String code, Side side, @DrawableRes int region) {
        BY_CODE.put(code, new Art(side, region));
    }

    static {
        // groups
        put("chest", Side.FRONT, R.drawable.muscle_group_chest);
        put("back", Side.BACK, R.drawable.muscle_group_back);
        put("shoulders", Side.FRONT, R.drawable.muscle_group_shoulders);
        put("biceps", Side.FRONT, R.drawable.muscle_group_biceps);
        put("triceps", Side.BACK, R.drawable.muscle_group_triceps);
        put("forearms", Side.FRONT, R.drawable.muscle_group_forearms);
        put("quads", Side.FRONT, R.drawable.muscle_group_quads);
        put("hamstrings", Side.BACK, R.drawable.muscle_group_hamstrings);
        put("glutes", Side.BACK, R.drawable.muscle_group_glutes);
        put("adductors", Side.FRONT, R.drawable.muscle_group_adductors);
        put("calves", Side.BACK, R.drawable.muscle_group_calves);
        put("abs", Side.FRONT, R.drawable.muscle_group_abs);
        put("lower_back", Side.BACK, R.drawable.muscle_group_lower_back);

        // chest
        put("chest.upper", Side.FRONT, R.drawable.muscle_chest_upper);
        put("chest.middle", Side.FRONT, R.drawable.muscle_chest_middle);
        put("chest.lower", Side.FRONT, R.drawable.muscle_chest_lower);

        // back
        put("back.lats", Side.BACK, R.drawable.muscle_back_lats);
        put("back.upper_traps", Side.BACK, R.drawable.muscle_back_upper_traps);
        put("back.mid_lower_traps", Side.BACK, R.drawable.muscle_back_mid_lower_traps);
        put("back.rhomboids", Side.BACK, R.drawable.muscle_back_rhomboids);
        put("back.teres_major", Side.BACK, R.drawable.muscle_back_teres_major);

        // shoulders
        put("shoulders.front_delt", Side.FRONT, R.drawable.muscle_shoulders_front_delt);
        put("shoulders.side_delt", Side.FRONT, R.drawable.muscle_shoulders_side_delt);
        put("shoulders.rear_delt", Side.BACK, R.drawable.muscle_shoulders_rear_delt);
        put("shoulders.rotator_cuff", Side.BACK, R.drawable.muscle_shoulders_rotator_cuff);

        // biceps
        put("biceps.long_head", Side.FRONT, R.drawable.muscle_biceps_long_head);
        put("biceps.short_head", Side.FRONT, R.drawable.muscle_biceps_short_head);
        put("biceps.brachialis", Side.FRONT, R.drawable.muscle_biceps_brachialis);

        // triceps
        put("triceps.long_head", Side.BACK, R.drawable.muscle_triceps_long_head);
        put("triceps.lateral_head", Side.BACK, R.drawable.muscle_triceps_lateral_head);
        put("triceps.medial_head", Side.BACK, R.drawable.muscle_triceps_medial_head);

        // forearms
        put("forearms.flexors", Side.FRONT, R.drawable.muscle_forearms_flexors);
        put("forearms.extensors", Side.BACK, R.drawable.muscle_forearms_extensors);
        put("forearms.brachioradialis", Side.FRONT, R.drawable.muscle_forearms_brachioradialis);

        // quadriceps
        put("quads.rectus_femoris", Side.FRONT, R.drawable.muscle_quads_rectus_femoris);
        put("quads.vastus_lateralis", Side.FRONT, R.drawable.muscle_quads_vastus_lateralis);
        put("quads.vastus_medialis", Side.FRONT, R.drawable.muscle_quads_vastus_medialis);
        put("quads.vastus_intermedius", Side.FRONT, R.drawable.muscle_quads_vastus_intermedius);

        // hamstrings
        put("hamstrings.biceps_femoris", Side.BACK, R.drawable.muscle_hamstrings_biceps_femoris);
        put("hamstrings.semis", Side.BACK, R.drawable.muscle_hamstrings_semis);

        // glutes
        put("glutes.maximus", Side.BACK, R.drawable.muscle_glutes_maximus);
        put("glutes.medius", Side.BACK, R.drawable.muscle_glutes_medius);
        put("glutes.minimus", Side.BACK, R.drawable.muscle_glutes_minimus);

        // adductors
        put("adductors.magnus", Side.FRONT, R.drawable.muscle_adductors_magnus);
        put("adductors.longus_brevis", Side.FRONT, R.drawable.muscle_adductors_longus_brevis);

        // calves
        put("calves.gastrocnemius", Side.BACK, R.drawable.muscle_calves_gastrocnemius);
        put("calves.soleus", Side.BACK, R.drawable.muscle_calves_soleus);

        // abdomen
        put("abs.rectus", Side.FRONT, R.drawable.muscle_abs_rectus);
        put("abs.obliques", Side.FRONT, R.drawable.muscle_abs_obliques);
        put("abs.transverse", Side.FRONT, R.drawable.muscle_abs_transverse);

        // lower back
        put("lower_back.erectors", Side.BACK, R.drawable.muscle_lower_back_erectors);
    }

    /**
     * The picture for this muscle code, or {@code null} when the catalogue has one this app does
     * not draw. Returning null rather than a stand-in keeps the caller honest: a screen must then
     * decide to show nothing, instead of silently showing the wrong body part.
     */
    @Nullable
    public static Art of(@Nullable String muscleCode) {
        return muscleCode == null ? null : BY_CODE.get(muscleCode);
    }

    private MuscleArt() {
    }
}
