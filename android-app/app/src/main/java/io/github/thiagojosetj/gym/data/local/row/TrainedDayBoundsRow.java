package io.github.thiagojosetj.gym.data.local.row;

import androidx.annotation.Nullable;

/** Oldest and newest day with a finished session; both null when there are none. */
public class TrainedDayBoundsRow {

    @Nullable
    public String firstDay;

    @Nullable
    public String lastDay;
}
