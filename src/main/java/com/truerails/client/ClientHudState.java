package com.truerails.client;

public final class ClientHudState {
    public static float actualSpeed;
    public static float displaySpeed;
    public static float cruise;
    public static float fuelPct;
    public static boolean hasFurnace;
    public static boolean reverse;
    public static int state;

    public static void reset() {
        actualSpeed = 0;
        displaySpeed = 0;
        cruise = 0;
        fuelPct = 0;
        hasFurnace = false;
        reverse = false;
        state = 0;
    }

    private ClientHudState() {}
}
