package com.pvpcalc.combat;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.entity.EntityType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

/**
 * Theoretical melee damage BEFORE armor reduction, mirroring PlayerEntity#attack of 1.21.x.
 * Pure calculation – nothing here writes to the CSV.
 */
public final class DamageCalculator {
    public enum Kind { SWORD, AXE, MACE, SPEAR, OTHER }

    /** details = text for the CSV "Details" column. */
    public record DamageResult(String details, double damage, boolean blocked) {}

    private static final TagKey<EntityType<?>> SENSITIVE_SMITE = tag("sensitive_to_smite");
    private static final TagKey<EntityType<?>> SENSITIVE_BANE = tag("sensitive_to_bane_of_arthropods");
    private static final TagKey<EntityType<?>> SENSITIVE_IMPALING = tag("sensitive_to_impaling");

    private DamageCalculator() {}

    private static TagKey<EntityType<?>> tag(String path) {
        return TagKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of("minecraft", path));
    }

    public static String fmt(double d) {
        return String.format(Locale.ROOT, "%.1f", d);
    }

    // ---------- item helpers ----------

    public static String itemPath(ItemStack s) {
        return s.isEmpty() ? "" : Registries.ITEM.getId(s.getItem()).getPath();
    }

    public static Kind kindOf(ItemStack s) {
        String p = itemPath(s);
        if (p.endsWith("_sword")) return Kind.SWORD;
        if (p.endsWith("_axe")) return Kind.AXE;
        if (p.equals("mace")) return Kind.MACE;
        if (p.endsWith("_spear")) return Kind.SPEAR;
        return Kind.OTHER;
    }

    /** Reads the level dynamically from the stack (never hardcoded). */
    public static int enchantLevel(ItemStack stack, RegistryKey<Enchantment> key) {
        if (stack == null || stack.isEmpty()) return 0;
        ItemEnchantmentsComponent c = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        for (RegistryEntry<Enchantment> e : c.getEnchantments()) {
            if (e.matchesKey(key)) return c.getLevel(e);
        }
        return 0;
    }

    /** Damage added by enchantments against this target (Sharpness, Smite, Bane of Arthropods, Impaling). */
    public static double enchantBonus(ItemStack weapon, Entity target) {
        double bonus = 0.0;
        int sharp = enchantLevel(weapon, Enchantments.SHARPNESS);
        if (sharp > 0) bonus += 0.5 * sharp + 0.5;
        if (target != null) {
            int smite = enchantLevel(weapon, Enchantments.SMITE);
            if (smite > 0 && target.getType().isIn(SENSITIVE_SMITE)) bonus += 2.5 * smite;
            int bane = enchantLevel(weapon, Enchantments.BANE_OF_ARTHROPODS);
            if (bane > 0 && target.getType().isIn(SENSITIVE_BANE)) bonus += 2.5 * bane;
            int imp = enchantLevel(weapon, Enchantments.IMPALING);
            if (imp > 0 && target.getType().isIn(SENSITIVE_IMPALING)) bonus += 2.5 * imp;
        }
        return bonus;
    }

    // ---------- status effects ----------

    public static int strengthLevel(LivingEntity e) {
        StatusEffectInstance s = e.getStatusEffect(StatusEffects.STRENGTH);
        return s == null ? 0 : s.getAmplifier() + 1;
    }

    public static int weaknessLevel(LivingEntity e) {
        StatusEffectInstance s = e.getStatusEffect(StatusEffects.WEAKNESS);
        return s == null ? 0 : s.getAmplifier() + 1;
    }

    /** +3 per Strength level, -4 per Weakness level (already contained in the ATTACK_DAMAGE attribute). */
    public static double effectBonus(LivingEntity e) {
        return 3.0 * strengthLevel(e) - 4.0 * weaknessLevel(e);
    }

    private static String strengthSuffix(LivingEntity e) {
        int l = strengthLevel(e);
        if (l <= 0) return "";
        String r = l == 1 ? "I" : l == 2 ? "II" : String.valueOf(l);
        return " (Strength " + r + ")";
    }

    // ---------- shield ----------

    /** Spec rule: a target blocking with a shield takes 0 damage, overriding everything else. */
    public static boolean isShieldBlocking(LivingEntity target) {
        return target.isBlocking();
    }

    // ---------- melee ----------

    public static DamageResult melee(ClientPlayerEntity p, Entity target) {
        ItemStack weapon = p.getMainHandStack();
        Kind kind = kindOf(weapon);
        String name = switch (kind) {
            case SWORD -> "Sword";
            case AXE -> "Axe";
            case MACE -> "Mace";
            case SPEAR -> "Spear";
            case OTHER -> weapon.isEmpty() ? "Fist" : weapon.getName().getString();
        };

        if (target instanceof LivingEntity le && isShieldBlocking(le)) {
            return new DamageResult(name + " hit blocked by shield: 0.0 damage", 0.0, true);
        }

        double h = p.getAttackCooldownProgress(0.5F);
        boolean charged = h > 0.9;
        double attr = p.getAttributeValue(EntityAttributes.ATTACK_DAMAGE); // weapon + Strength/Weakness
        double f = attr * (0.2 + h * h * 0.8);

        double smash = kind == Kind.MACE ? MaceCalculator.smashBonus(p, weapon) : 0.0;
        double g = (enchantBonus(weapon, target) + smash) * h;

        boolean sprintKb = p.isSprinting() && charged;
        boolean crit = charged
                && MaceCalculator.fallDistance(p) > 0.0
                && !p.isOnGround()
                && !p.isClimbing()
                && !p.isTouchingWater()
                && !p.hasStatusEffect(StatusEffects.BLINDNESS)
                && !p.hasVehicle()
                && target instanceof LivingEntity
                && !p.isSprinting();
        if (crit) f *= 1.5;

        Vec3d v = p.getVelocity();
        boolean sweep = kind == Kind.SWORD && charged && !crit && !sprintKb && p.isOnGround()
                && 0.6 * Math.hypot(v.x, v.z) < p.getMovementSpeed();

        double total = Math.max(0.0, f + g);
        String label;
        if (kind == Kind.MACE && smash > 0) {
            label = "Mace smash (fall " + fmt(MaceCalculator.fallDistance(p)) + " blocks" + (crit ? ", critical" : "") + ")";
        } else if (kind == Kind.SPEAR) {
            label = crit ? "Spear jab critical" : "Spear jab";
        } else if (crit) {
            label = kind == Kind.SWORD ? "Critical hit" : name + " critical hit";
        } else if (sweep) {
            label = "Sweep attack";
        } else if (sprintKb) {
            label = name + " sprint/knockback hit";
        } else {
            label = name + " hit";
        }

        StringBuilder sb = new StringBuilder(label).append(": ").append(fmt(total)).append(" damage");
        if (sweep) {
            int sweepLvl = enchantLevel(weapon, Enchantments.SWEEPING_EDGE);
            double ratio = sweepLvl / (sweepLvl + 1.0);
            sb.append(" (splash ").append(fmt(1.0 + ratio * f)).append(")");
        }
        if (!charged) sb.append(" [charge ").append(Math.round(h * 100)).append("%]");
        sb.append(strengthSuffix(p));
        return new DamageResult(sb.toString(), total, false);
    }
}
