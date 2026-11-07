package com.pvpcalc.combat;

import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/** Native 1.21.11 mace smash bonus (added on top of the normal attack damage). */
public final class MaceCalculator {
    private MaceCalculator() {}

    public static double fallDistance(PlayerEntity p) {
        return p.fallDistance;
    }

    /** Vanilla: fallDistance > 1.5 and not gliding. */
    public static boolean canSmash(PlayerEntity p) {
        return fallDistance(p) > 1.5 && !p.isGliding();
    }

    /** Bonus damage: 4/block up to 3, then 2/block up to 8, then 1/block; + Density 0.5 per level per block. */
    public static double smashBonus(PlayerEntity p, ItemStack mace) {
        if (!canSmash(p)) return 0.0;
        double h = fallDistance(p);
        double bonus;
        if (h <= 3.0) bonus = 4.0 * h;
        else if (h <= 8.0) bonus = 12.0 + 2.0 * (h - 3.0);
        else bonus = 22.0 + (h - 8.0);
        int density = DamageCalculator.enchantLevel(mace, Enchantments.DENSITY);
        return bonus + 0.5 * density * h; // Breach only reduces armor, no damage here
    }
}
