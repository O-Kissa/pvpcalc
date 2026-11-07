package com.pvpcalc.combat;

import com.pvpcalc.config.PvPCalcConfig;
import com.pvpcalc.tracking.MatchTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Event glue: melee attacks (via PlayerEntityMixin), bow / crossbow releases and spear charges (via client tick).
 * Calculations live in DamageCalculator / SpearCalculator; logging goes through MatchTracker.
 */
public final class WeaponHandler {
    private static final double SPEAR_MIN_RELATIVE_SPEED = 4.6; // blocks/s, approximate damage threshold
    private static final int SPEAR_MIN_USE_TICKS = 8;
    private static final double SPEAR_REACH = 4.5;

    private record CrossbowSnap(boolean firework, int stars, int arrows) {}

    // bow state
    private static boolean bowDrawing;
    private static int bowTicks;
    private static int bowSlot;
    private static ItemStack bowStack = ItemStack.EMPTY;
    // crossbow state
    private static CrossbowSnap prevMain, prevOff;
    // spear charge state
    private static final Set<Integer> CHARGE_HITS = new HashSet<>();

    private WeaponHandler() {}

    // ---------------- melee ----------------

    public static void onPlayerAttack(ClientPlayerEntity self, Entity target) {
        PvPCalcConfig cfg = PvPCalcConfig.get();
        if (!cfg.enabled || target == null || target == self) return;
        if (!(target instanceof LivingEntity)) return;
        if (cfg.onlyPlayers && !(target instanceof PlayerEntity)) return;
        if (MatchTracker.duplicate("atk:" + target.getId(), 1)) return;
        if (target instanceof PlayerEntity pe) MatchTracker.setOpponent(pe);

        DamageCalculator.DamageResult r = DamageCalculator.melee(self, target);
        MatchTracker.log("DAMAGE", r.details());
    }

    // ---------------- per-tick: bow, crossbow, spear charge ----------------

    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        ClientWorld world = mc.world;
        if (p == null || world == null || !PvPCalcConfig.get().enabled) {
            reset();
            return;
        }
        tickBow(p);
        tickCrossbow(p);
        tickSpear(p, world);
    }

    private static void tickBow(ClientPlayerEntity p) {
        ItemStack active = p.isUsingItem() ? p.getActiveItem() : ItemStack.EMPTY;
        if (active.isOf(Items.BOW)) {
            bowDrawing = true;
            bowTicks = p.getItemUseTime();
            bowSlot = p.getInventory().getSelectedSlot();
            bowStack = active.copy();
        } else if (bowDrawing) {
            bowDrawing = false;
            // Released (not cancelled by switching hotbar slot) while still holding a bow
            boolean holding = p.getMainHandStack().isOf(Items.BOW) || p.getOffHandStack().isOf(Items.BOW);
            if (holding && p.getInventory().getSelectedSlot() == bowSlot) {
                logBow(bowTicks + 1, bowStack);
            }
        }
    }

    private static void logBow(int useTicks, ItemStack bow) {
        double f = useTicks / 20.0;
        double pull = Math.min(1.0, (f * f + f * 2.0) / 3.0);
        if (pull < 0.1) return; // vanilla does not shoot below 10 %
        int power = DamageCalculator.enchantLevel(bow, Enchantments.POWER);
        double baseDamage = 2.0 + (power > 0 ? power * 0.5 + 0.5 : 0.0);
        int i = (int) Math.ceil(pull * 3.0 * baseDamage);
        boolean crit = pull >= 1.0; // fully drawn arrows are critical
        String dmg = crit ? DamageCalculator.fmt(i) + "-" + DamageCalculator.fmt(i + i / 2 + 1) : DamageCalculator.fmt(i);
        MatchTracker.log("DAMAGE", "Bow shot: " + dmg + " damage (draw " + Math.round(pull * 100) + "%, "
                + useTicks + " ticks" + (power > 0 ? ", Power " + power : "") + (crit ? ", critical" : "") + ")");
    }

    private static CrossbowSnap snap(ItemStack s) {
        if (!s.isOf(Items.CROSSBOW)) return null;
        ChargedProjectilesComponent c = s.get(DataComponentTypes.CHARGED_PROJECTILES);
        if (c == null || c.isEmpty()) return null;
        List<ItemStack> list = c.getProjectiles();
        boolean fw = false;
        int stars = 0;
        for (ItemStack st : list) {
            if (st.isOf(Items.FIREWORK_ROCKET)) {
                fw = true;
                FireworksComponent comp = st.get(DataComponentTypes.FIREWORKS);
                stars = comp == null ? 0 : comp.explosions().size();
            }
        }
        return new CrossbowSnap(fw, stars, list.size());
    }

    private static void tickCrossbow(ClientPlayerEntity p) {
        CrossbowSnap main = snap(p.getMainHandStack());
        CrossbowSnap off = snap(p.getOffHandStack());
        if (prevMain != null && main == null && p.getMainHandStack().isOf(Items.CROSSBOW)) logCrossbow(prevMain);
        if (prevOff != null && off == null && p.getOffHandStack().isOf(Items.CROSSBOW)) logCrossbow(prevOff);
        prevMain = main;
        prevOff = off;
    }

    private static void logCrossbow(CrossbowSnap s) {
        if (s.firework()) {
            // Vanilla: a rocket without stars deals no explosion damage; otherwise 5 + 2 per star.
            double dmg = s.stars() > 0 ? 5.0 + 2.0 * s.stars() : 0.0;
            MatchTracker.log("DAMAGE", "Crossbow firework: " + DamageCalculator.fmt(dmg) + " damage (" + s.stars() + " stars)");
        } else {
            int i = (int) Math.ceil(3.15 * 2.0); // crossbow arrow speed 3.15, base damage 2.0, always critical
            MatchTracker.log("DAMAGE", "Crossbow arrow: " + DamageCalculator.fmt(i) + "-" + DamageCalculator.fmt(i + i / 2 + 1) + " damage (critical)");
        }
    }

    private static void tickSpear(ClientPlayerEntity p, ClientWorld world) {
        ItemStack active = p.isUsingItem() ? p.getActiveItem() : ItemStack.EMPTY;
        if (!SpearCalculator.isSpear(active)) {
            CHARGE_HITS.clear();
            return;
        }
        if (p.getItemUseTime() < SPEAR_MIN_USE_TICKS) return;

        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1.0F);
        Vec3d end = eye.add(look.multiply(SPEAR_REACH));
        Box search = p.getBoundingBox().stretch(look.multiply(SPEAR_REACH)).expand(1.0);
        boolean onlyPlayers = PvPCalcConfig.get().onlyPlayers;

        for (Entity e : world.getOtherEntities(p, search, x -> x instanceof LivingEntity && x.isAlive() && !x.isSpectator())) {
            if (onlyPlayers && !(e instanceof PlayerEntity)) continue;
            if (CHARGE_HITS.contains(e.getId())) continue;
            Box b = e.getBoundingBox().expand(e.getTargetingMargin());
            if (!b.contains(eye) && b.raycast(eye, end).isEmpty()) continue;

            LivingEntity le = (LivingEntity) e;
            SpearCalculator.Result r = SpearCalculator.charge(p, le, active);
            if (r.relativeSpeed() < SPEAR_MIN_RELATIVE_SPEED) continue;
            CHARGE_HITS.add(e.getId());
            if (e instanceof PlayerEntity pe) MatchTracker.setOpponent(pe);

            if (DamageCalculator.isShieldBlocking(le)) {
                MatchTracker.log("DAMAGE", "Spear charge blocked by shield: 0.0 damage");
            } else {
                MatchTracker.log("DAMAGE", "Spear charge: native " + DamageCalculator.fmt(r.nativeDamage())
                        + " / Minemen-style " + DamageCalculator.fmt(r.customDamage())
                        + " damage (rel. speed " + DamageCalculator.fmt(r.relativeSpeed()) + " b/s, velocity "
                        + String.format(java.util.Locale.ROOT, "%.2f", r.velocityMagnitude()) + " b/tick)");
            }
        }
    }

    public static void reset() {
        bowDrawing = false;
        bowStack = ItemStack.EMPTY;
        prevMain = null;
        prevOff = null;
        CHARGE_HITS.clear();
    }
}
