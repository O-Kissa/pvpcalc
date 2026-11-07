package com.pvpcalc.tracking;

import com.pvpcalc.config.PvPCalcConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Golden apple detection.
 *
 * The server never tells the client "player X ate an apple". The only client-visible information is that a player
 * is using an item (synced entity flag) and which item is held. We therefore treat an eating session as "consumed"
 * when a player was using a golden apple and stopped after (almost) the full 32-tick eating time. This is a
 * best-effort heuristic for opponents; it is reliable for the local player.
 */
public final class GoldenAppleTracker {
    private static final int MIN_TICKS_FOR_CONSUMPTION = 30; // full eating time is 32 ticks

    private record Eating(boolean enchanted, int lastUseTime) {}

    private static final Map<Integer, Eating> STATES = new HashMap<>();

    private GoldenAppleTracker() {}

    public static void tick(MinecraftClient mc) {
        ClientWorld world = mc.world;
        if (world == null || mc.player == null || !PvPCalcConfig.get().enabled) {
            STATES.clear();
            return;
        }
        Set<Integer> seen = new HashSet<>();
        for (PlayerEntity p : world.getPlayers()) {
            seen.add(p.getId());
            ItemStack active = p.isUsingItem() ? p.getActiveItem() : ItemStack.EMPTY;
            boolean normal = active.isOf(Items.GOLDEN_APPLE);
            boolean ench = active.isOf(Items.ENCHANTED_GOLDEN_APPLE);
            Eating prev = STATES.get(p.getId());
            if (normal || ench) {
                STATES.put(p.getId(), new Eating(ench, p.getItemUseTime()));
            } else if (prev != null) {
                STATES.remove(p.getId());
                if (prev.lastUseTime() >= MIN_TICKS_FOR_CONSUMPTION
                        && !MatchTracker.duplicate("gapple:" + p.getId(), 40)) {
                    String apple = prev.enchanted() ? "Enchanted Golden Apple" : "Golden Apple";
                    String who = p == mc.player ? "You" : "Opponent";
                    MatchTracker.log("GOLDEN APPLE", who + " consumed " + apple);
                }
            }
        }
        STATES.keySet().retainAll(seen);
    }

    public static void reset() {
        STATES.clear();
    }
}
