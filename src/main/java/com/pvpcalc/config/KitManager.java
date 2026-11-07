package com.pvpcalc.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class KitManager {
    /** Menu group title -> kits (in display order). */
    public static final Map<String, List<String>> GROUPS = new LinkedHashMap<>();

    static {
        GROUPS.put("Netherite PvP", List.of("NETHERITE POT", "SMP"));
        GROUPS.put("Diamond PvP", List.of("DIAMOND POT", "DIAMOND CRYSTAL", "DIAMOND SMP"));
        GROUPS.put("Other PvP Modes", List.of("UHC", "HOPLITE", "MANHUNT", "AXE", "SWORD"));
        GROUPS.put("Additional PvP Modes", List.of("SPEAR MACE", "MACE", "ELYTRA MACE", "SPEAR ELYTRA", "CRYSTAL", "BOW", "CREEPER"));
    }

    private KitManager() {}

    public static List<String> allKits() {
        List<String> all = new ArrayList<>();
        GROUPS.values().forEach(all::addAll);
        return all;
    }

    /** Normalizes user input (case/whitespace) and returns the canonical kit name if valid. */
    public static Optional<String> resolve(String input) {
        if (input == null) return Optional.empty();
        String norm = input.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        return allKits().stream().filter(k -> k.equals(norm)).findFirst();
    }
}
