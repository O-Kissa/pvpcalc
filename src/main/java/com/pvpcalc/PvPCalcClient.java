package com.pvpcalc;

import com.pvpcalc.combat.ExplosionCalculator;
import com.pvpcalc.combat.SpearCalculator;
import com.pvpcalc.combat.WeaponHandler;
import com.pvpcalc.commands.PvPCalcCommands;
import com.pvpcalc.config.PvPCalcConfig;
import com.pvpcalc.logging.CSVLogger;
import com.pvpcalc.tracking.GoldenAppleTracker;
import com.pvpcalc.tracking.MatchTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PvPCalcClient implements ClientModInitializer {
    public static final String MOD_ID = "pvpcalc";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        PvPCalcConfig.load();
        CSVLogger.ensureFile();
        PvPCalcCommands.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                // Order matters: velocity/snapshots first, then consumers.
                SpearCalculator.trackTick(client);
                ExplosionCalculator.snapshot(client);
                WeaponHandler.tick(client);
                GoldenAppleTracker.tick(client);
            } catch (Throwable t) {
                LOGGER.error("PvPCalc tick failed", t);
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetTransientState());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> resetTransientState());

        LOGGER.info("PvPCalc initialized (tracking {})", PvPCalcConfig.get().enabled ? "ON" : "OFF");
    }

    private static void resetTransientState() {
        MatchTracker.clearTransient();
        GoldenAppleTracker.reset();
        WeaponHandler.reset();
        SpearCalculator.reset();
        ExplosionCalculator.reset();
    }
}
