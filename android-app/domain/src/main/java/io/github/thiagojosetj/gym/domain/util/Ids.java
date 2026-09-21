package io.github.thiagojosetj.gym.domain.util;

import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;

/**
 * Client-side identifier generation (ADR-0007).
 *
 * <p>Every syncable record gets its id on the device where it is created, so records can be
 * created offline on several devices without collisions and keep the same id on the server and on
 * iOS. We use UUID version 7 (RFC 9562): the first 48 bits are a Unix timestamp in milliseconds,
 * which keeps ids roughly ordered by creation time and improves B-tree index locality in
 * PostgreSQL; the remaining 74 bits are random.
 */
public final class Ids {

    private static final Random RANDOM = new SecureRandom();

    private Ids() {
    }

    /** A new random UUIDv7 string (lower case, canonical 8-4-4-4-12 format). */
    public static String newId() {
        return uuidV7(System.currentTimeMillis(), RANDOM).toString();
    }

    /** Builds a UUIDv7 for the given timestamp. Visible for tests (deterministic randomness). */
    static UUID uuidV7(long epochMillis, Random random) {
        if (epochMillis < 0 || epochMillis > 0xFFFF_FFFF_FFFFL) {
            throw new IllegalArgumentException("Timestamp out of UUIDv7 range: " + epochMillis);
        }
        // most significant 64 bits: unix_ts_ms (48) | version (4) | rand_a (12)
        long msb = epochMillis << 16;
        msb |= 0x7000L;
        msb |= random.nextInt(1 << 12);
        // least significant 64 bits: variant "10" (2) | rand_b (62)
        long lsb = random.nextLong();
        lsb &= 0x3FFF_FFFF_FFFF_FFFFL;
        lsb |= 0x8000_0000_0000_0000L;
        return new UUID(msb, lsb);
    }

    /** Extracts the creation timestamp from a UUIDv7 string. */
    public static long timestampOf(String uuidV7) {
        UUID uuid = UUID.fromString(uuidV7);
        if (uuid.version() != 7) {
            throw new IllegalArgumentException("Not a UUIDv7: " + uuidV7);
        }
        return uuid.getMostSignificantBits() >>> 16;
    }
}
