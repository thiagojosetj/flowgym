package io.github.thiagojosetj.gym.domain.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TextNormalizerTest {

    @Test
    public void removesAccentsAndLowercases() {
        assertEquals("supino reto", TextNormalizer.normalize("Súpino RETO"));
        assertEquals("elevacao lateral", TextNormalizer.normalize("Elevação Lateral"));
        assertEquals("gluteo maximo", TextNormalizer.normalize("Glúteo Máximo"));
    }

    @Test
    public void collapsesPunctuationAndWhitespace() {
        assertEquals("leg press 45", TextNormalizer.normalize("  Leg-press   (45°) "));
        assertEquals("puxada frente", TextNormalizer.normalize("Puxada\tfrente"));
    }

    @Test
    public void nullAndBlankBecomeEmpty() {
        assertEquals("", TextNormalizer.normalize(null));
        assertEquals("", TextNormalizer.normalize("   "));
    }

    @Test
    public void partialQueryIsContainedInNormalizedName() {
        String stored = TextNormalizer.normalize("Supino reto com barra");
        assertTrue(stored.contains(TextNormalizer.normalize("PINO")));
        assertTrue(stored.contains(TextNormalizer.normalize("reto com")));
    }
}
