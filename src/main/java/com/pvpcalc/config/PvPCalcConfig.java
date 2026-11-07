package com.pvpcalc.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pvpcalc.PvPCalcClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PvPCalcConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static PvPCalcConfig instance = new PvPCalcConfig();

    /** Master switch. Persisted. */
    public boolean enabled = true;
    /** Currently active kit (empty = none). Persisted. */
    public String activeKit = "";
    /** Only log damage against other players. */
    public boolean onlyPlayers = true;
    /** Custom Minemen-style spear: damage = base * (1 + factor * |velocity| [blocks/tick]). */
    public double minemenSpearVelocityFactor = 3.0;

    public static PvPCalcConfig get() {
        return instance;
    }

    public static Path directory() {
        return FabricLoader.getInstance().getConfigDir().resolve("pvpcalc");
    }

    private static Path file() {
        return directory().resolve("config.json");
    }

    public static synchronized void load() {
        try {
            Files.createDirectories(directory());
            if (Files.exists(file())) {
                PvPCalcConfig loaded = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), PvPCalcConfig.class);
                if (loaded != null) {
                    if (loaded.activeKit == null) loaded.activeKit = "";
                    instance = loaded;
                }
            } else {
                save();
            }
        } catch (Exception e) {
            PvPCalcClient.LOGGER.error("Could not load PvPCalc config, using defaults", e);
            instance = new PvPCalcConfig();
        }
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(directory());
            Files.writeString(file(), GSON.toJson(instance), StandardCharsets.UTF_8);
        } catch (IOException e) {
            PvPCalcClient.LOGGER.error("Could not save PvPCalc config", e);
        }
    }
}
