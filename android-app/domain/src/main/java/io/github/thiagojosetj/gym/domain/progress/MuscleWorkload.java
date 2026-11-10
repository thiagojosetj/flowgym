package io.github.thiagojosetj.gym.domain.progress;

/**
 * How much one muscle group was worked in a period (PRODUCT_SPEC PRG-04).
 *
 * <p>Two counts, never one. A set of bench press is a set for the chest and it is also something
 * the triceps did, but the two are not the same amount of work and nobody can say what fraction of
 * a set a secondary muscle got. Adding them with a weight of one would claim the triceps were
 * trained as hard as the chest; a weight of a half would be a number this app invented. So both
 * are reported, side by side, and the reader decides (PRODUCT_SPEC section 9's rule about never
 * presenting an invented figure as a measurement).
 *
 * <p>No load volume here either, and for the same reason: a bench press's 400 kg is not "400 kg of
 * chest plus 400 kg of triceps", and there is no honest way to split it. Sets per muscle per week
 * is the figure training actually uses, and it is one this app can state without guessing.
 *
 * @param primarySets   working sets whose exercise names this group as a main target
 * @param secondarySets working sets whose exercise names it only as assisting
 */
public record MuscleWorkload(String muscleGroupId, String name, int sortOrder, int primarySets,
                             int secondarySets) {

    public int totalSets() {
        return primarySets + secondarySets;
    }
}
