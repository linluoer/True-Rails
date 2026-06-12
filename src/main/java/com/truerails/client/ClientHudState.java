package com.truerails.client;

/** 纯数据类：不得引入任何 Minecraft 客户端类（网络包 handler 会引用本类）。 */
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
