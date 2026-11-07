package com.pvpcalc.tracking;

import com.pvpcalc.config.PvPCalcConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

public final class TotemTracker {
    private TotemTracker() {}

    public static void onEntityStatus(EntityStatusS2CPacket packet) {
        if (!PvPCalcConfig.get().enabled) return;
        if (packet.getStatus() != EntityStatuses.USE_TOTEM_OF_UNDYING) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        if (world == null || mc.player == null) return;

        Entity e = packet.getEntity(world);
        if (!(e instanceof PlayerEntity p) || p == mc.player) return;
        if (MatchTracker.duplicate("totem:" + p.getId(), 10)) return;

        String details = MatchTracker.isOpponent(p)
                ? "Opponent used Totem of Undying"
                : "Player " + p.getName().getString() + " used Totem of Undying";
        MatchTracker.log("TOTEM POP", details);
    }
}
