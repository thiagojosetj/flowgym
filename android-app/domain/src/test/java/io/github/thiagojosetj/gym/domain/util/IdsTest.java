package io.github.thiagojosetj.gym.domain.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.junit.Test;

public class IdsTest {

    @Test
    public void generatesVersion7WithRfcVariant() {
        UUID uuid = UUID.fromString(Ids.newId());
        assertEquals(7, uuid.version());
        assertEquals(2, uuid.variant()); // IETF variant "10"
    }

    @Test
    public void embedsTheTimestamp() {
        long millis = 1_790_000_000_123L;
        UUID uuid = Ids.uuidV7(millis, new Random(42));
        assertEquals(millis, Ids.timestampOf(uuid.toString()));
    }

    @Test
    public void idsCreatedLaterSortAfterEarlierOnes() {
        String earlier = Ids.uuidV7(1_000L, new Random(1)).toString();
        String later = Ids.uuidV7(2_000L, new Random(1)).toString();
        assertTrue(earlier.compareTo(later) < 0);
    }

    @Test
    public void manyIdsAreUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            assertTrue(seen.add(Ids.newId()));
        }
    }

    @Test
    public void rejectsTimestampsOutsideRange() {
        assertThrows(IllegalArgumentException.class, () -> Ids.uuidV7(-1L, new Random()));
    }

    @Test
    public void timestampOfRejectsOtherVersions() {
        String v4 = UUID.randomUUID().toString();
        assertThrows(IllegalArgumentException.class, () -> Ids.timestampOf(v4));
    }
}
