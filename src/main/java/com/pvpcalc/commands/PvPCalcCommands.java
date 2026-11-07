package com.pvpcalc.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.pvpcalc.config.KitManager;
import com.pvpcalc.config.PvPCalcConfig;
import com.pvpcalc.logging.CSVLogger;
import com.pvpcalc.tracking.MatchTracker;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Optional;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class PvPCalcCommands {
    private PvPCalcCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(root());
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> root() {
        return literal("pvpcalc")
                .executes(ctx -> status(ctx.getSource()))
                .then(literal("on").executes(ctx -> setEnabled(ctx.getSource(), true)))
                .then(literal("off").executes(ctx -> setEnabled(ctx.getSource(), false)))
                .then(literal("reset").executes(ctx -> reset(ctx.getSource())))
                .then(literal("active_kit")
                        .then(argument("kit", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> CommandSource.suggestMatching(KitManager.allKits(), builder))
                                .executes(ctx -> activateKit(ctx.getSource(), StringArgumentType.getString(ctx, "kit")))))
                .then(pvpBranch("PvP"))
                .then(pvpBranch("pvp"));
    }

    /** /pvpcalc PvP [netherite|diamond|other|additional] – shows clickable kit menus. */
    private static LiteralArgumentBuilder<FabricClientCommandSource> pvpBranch(String name) {
        List<String> groups = List.copyOf(KitManager.GROUPS.keySet());
        return literal(name)
                .executes(ctx -> {
                    groups.forEach(g -> ctx.getSource().sendFeedback(menu(g)));
                    return 1;
                })
                .then(literal("netherite").executes(ctx -> showGroup(ctx.getSource(), groups.get(0))))
                .then(literal("diamond").executes(ctx -> showGroup(ctx.getSource(), groups.get(1))))
                .then(literal("other").executes(ctx -> showGroup(ctx.getSource(), groups.get(2))))
                .then(literal("additional").executes(ctx -> showGroup(ctx.getSource(), groups.get(3))));
    }

    private static int showGroup(FabricClientCommandSource src, String group) {
        src.sendFeedback(menu(group));
        return 1;
    }

    private static Text menu(String group) {
        MutableText line = Text.literal(group + ": ").formatted(Formatting.GOLD);
        for (String kit : KitManager.GROUPS.get(group)) {
            line.append(button(kit)).append(Text.literal(" "));
        }
        return line;
    }

    private static Text button(String kit) {
        Style style = Style.EMPTY
                .withColor(Formatting.AQUA)
                .withBold(true)
                .withClickEvent(new ClickEvent.RunCommand("/pvpcalc active_kit " + kit))
                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Activate kit: " + kit)));
        return Text.literal("[" + kit + "]").setStyle(style);
    }

    private static int activateKit(FabricClientCommandSource src, String raw) {
        Optional<String> kit = KitManager.resolve(raw);
        if (kit.isEmpty()) {
            src.sendError(Text.literal("[PvPCalc] Unknown kit: " + raw));
            return 0;
        }
        PvPCalcConfig cfg = PvPCalcConfig.get();
        cfg.activeKit = kit.get();
        PvPCalcConfig.save();
        src.sendFeedback(Text.literal("[PvPCalc] Active kit: ").formatted(Formatting.GREEN)
                .append(Text.literal(kit.get()).formatted(Formatting.AQUA, Formatting.BOLD))
                .append(Text.literal(cfg.enabled ? "" : " (tracking is OFF – no match marker written)").formatted(Formatting.GRAY)));
        MatchTracker.startMatch(kit.get()); // no-op when tracking is disabled
        return 1;
    }

    private static int setEnabled(FabricClientCommandSource src, boolean on) {
        PvPCalcConfig.get().enabled = on;
        PvPCalcConfig.save();
        if (!on) MatchTracker.clearTransient();
        src.sendFeedback(Text.literal("[PvPCalc] Tracking " + (on ? "ENABLED" : "DISABLED")
                + (on ? "" : " (CSV data kept)")).formatted(on ? Formatting.GREEN : Formatting.RED));
        return 1;
    }

    private static int reset(FabricClientCommandSource src) {
        if (CSVLogger.reset()) {
            src.sendFeedback(Text.literal("[PvPCalc] CSV session history cleared.").formatted(Formatting.YELLOW));
            return 1;
        }
        src.sendError(Text.literal("[PvPCalc] Could not reset the CSV file (see log)."));
        return 0;
    }

    private static int status(FabricClientCommandSource src) {
        PvPCalcConfig c = PvPCalcConfig.get();
        src.sendFeedback(Text.literal("[PvPCalc] Tracking: " + (c.enabled ? "ON" : "OFF")
                + " | Kit: " + (c.activeKit.isEmpty() ? "NONE" : c.activeKit)).formatted(Formatting.YELLOW));
        src.sendFeedback(Text.literal("Commands: /pvpcalc on|off|reset|active_kit <KIT>|PvP [netherite|diamond|other|additional]").formatted(Formatting.GRAY));
        return 1;
    }
}
