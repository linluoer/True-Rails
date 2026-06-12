package com.truerails.train;

public enum TrainState {
    CRUISING,
    BRAKING,
    DOCKED,
    DEPARTING;

    public static TrainState byOrdinal(int i) {
        TrainState[] v = values();
        return (i >= 0 && i < v.length) ? v[i] : CRUISING;
    }
}
