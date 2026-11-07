package com.pvpcalc.tracking;

import com.pvpcalc.config.PvPCalcConfig;
import com.pvpcalc.logging.CSVLogger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashMap;
import java.util.Map;

/** Central gate for event logging: respects the master switch and prevents duplicates. */
public final class MatchTracker {
    public static final String MATCH_MARKER = "--- NEW MATCH STARTED ---";

    private static final Map<String, Long> RECENT = new HashMap<>();
    private static int opponentId = -1;

    private MatchTracker() {}

    public static String kitContext() {
        String k = PvPCalcConfig.get().activeKit;
        return (k == null || k.isEmpty()) ? "NONE" : k;
    }

    public static void startMatch(String kit) {
        if (!PvPCalcConfig.get().enabled) return;
        opponentId = -1;
        CSVLogger.append(MATCH_MARKER, kit, "");
    }

    public static void log(String event, String details) {
        if (!PvPCalcConfig.get().enabled) return;
        CSVLogger.append(event, kitContext(), details);
    }

    /** True if the same key was already seen within windowTicks (1 = same tick only). */
    public static boolean duplicate(String key, int windowTicks) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;
        long now = mc.world.getTime();
        Long last = RECENT.get(key);
        if (RECENT.size() > 256) RECENT.clear();
        if (last != null && now - last < windowTicks) return true;
        RECENT.put(key, now);
        return false;
    }

    public static void setOpponent(PlayerEntity p) {
        opponentId = p.getId();
    }

    public static boolean isOpponent(PlayerEntity p) {
        return opponentId == -1 || opponentId == p.getId();
    }

    public static void clearTransient() {
        RECENT.clear();
        opponentId = -1;
    }
}
