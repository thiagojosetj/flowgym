package io.github.thiagojosetj.gym.ui.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import io.github.thiagojosetj.gym.data.seed.CatalogSeeder;

/**
 * The catalogue decides which muscles exist; this pins that every one of them has a picture.
 *
 * <p>Without it, a muscle added to the catalogue renders an empty space on the device and nobody
 * notices until a user asks why one row has no image - exactly the kind of gap a reviewer reading
 * a diff cannot see, because the two files live far apart.
 */
@RunWith(AndroidJUnit4.class)
public class MuscleArtTest {

    @Test
    public void everyMuscleInTheCatalogueHasAPicture() throws Exception {
        List<String> withoutArt = new ArrayList<>();
        for (String code : catalogueMuscleCodes()) {
            if (MuscleArt.of(code) == null) {
                withoutArt.add(code);
            }
        }

        assertTrue("sem imagem no mapa muscular: " + withoutArt, withoutArt.isEmpty());
    }

    @Test
    public void theCatalogueStillHasTheFiftyOneMusclesThePicturesWereDrawnFor() throws Exception {
        // A guard on the number itself: dropping a muscle from the catalogue would leave an
        // orphan drawable that the test above cannot see, because it only looks one way.
        assertEquals(51, catalogueMuscleCodes().size());
    }

    @Test
    public void aSubgroupIsShownOnTheSideItIsActuallyOn() throws Exception {
        // The reason the front/back cue exists at all: these two differ only by side, and a
        // picture that got this wrong would be worse than no picture.
        assertEquals(MuscleArt.Side.FRONT, MuscleArt.of("shoulders.front_delt").side());
        assertEquals(MuscleArt.Side.BACK, MuscleArt.of("shoulders.rear_delt").side());
        assertNotEquals(MuscleArt.of("shoulders.front_delt").region(),
                MuscleArt.of("shoulders.rear_delt").region());
    }

    @Test
    public void everyMuscleGetsItsOwnRegionAndNotASharedOne() throws Exception {
        TreeSet<Integer> regions = new TreeSet<>();
        for (String code : catalogueMuscleCodes()) {
            regions.add(MuscleArt.of(code).region());
        }

        assertEquals("duas entradas apontam para o mesmo desenho", 51, regions.size());
    }

    @Test
    public void aCodeThatIsNotAMuscleGivesNothingInsteadOfTheWrongBodyPart() {
        assertNull(MuscleArt.of("nao.existe"));
        assertNull(MuscleArt.of(""));
        assertNull(MuscleArt.of(null));
    }

    @Test
    public void bothSilhouettesExist() {
        assertNotEquals(0, MuscleArt.Side.FRONT.body);
        assertNotEquals(0, MuscleArt.Side.BACK.body);
        assertNotEquals(MuscleArt.Side.FRONT.body, MuscleArt.Side.BACK.body);
    }

    // ------------------------------------------------------------------ helpers

    /** Group codes and subgroup codes, read from the catalogue the app actually ships. */
    private static List<String> catalogueMuscleCodes() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        String json;
        try (InputStream in = context.getAssets().open(CatalogSeeder.ASSET_PATH);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            for (int read; (read = in.read(buffer)) != -1; ) {
                out.write(buffer, 0, read);
            }
            json = out.toString(StandardCharsets.UTF_8.name());
        }

        List<String> codes = new ArrayList<>();
        JSONArray groups = new JSONObject(json).getJSONArray("muscles");
        for (int g = 0; g < groups.length(); g++) {
            JSONObject group = groups.getJSONObject(g);
            codes.add(group.getString("code"));
            JSONArray subgroups = group.optJSONArray("subgroups");
            for (int s = 0; subgroups != null && s < subgroups.length(); s++) {
                codes.add(subgroups.getJSONObject(s).getString("code"));
            }
        }
        assertNotNull(codes);
        return codes;
    }
}
