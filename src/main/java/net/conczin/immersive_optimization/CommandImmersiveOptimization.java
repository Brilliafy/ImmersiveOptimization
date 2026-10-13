package net.conczin.immersive_optimization;

import net.conczin.immersive_optimization.config.ModConfig;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

public class CommandImmersiveOptimization extends CommandBase {

    @Override
    public String getName() {
        return "io";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("immersiveoptimization", "immersive_optimization");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/io [status|top|stress|reload|toggle]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0 || "status".equalsIgnoreCase(args[0]) || "info".equalsIgnoreCase(args[0])) {
            sender.sendMessage(new TextComponentString(TextFormatting.GOLD + "=== Immersive Optimization Status ==="));
            boolean enabled = ModConfig.GENERAL.enableEntities;
            sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "Throttling: " +
                    (enabled ? TextFormatting.GREEN + "ENABLED" : TextFormatting.RED + "DISABLED") +
                    TextFormatting.GRAY + " | Scheduler: " +
                    (TickScheduler.isWorkerRunning() ? TextFormatting.GREEN + "ACTIVE (refresh every " + TickScheduler.refreshIntervalTicks() + " ticks)" : TextFormatting.RED + "STOPPED")));

            double avgMspt = TickScheduler.getAverageTickMs(server);
            double tps = avgMspt <= 50.0 ? 20.0 : Math.min(20.0, 1000.0 / avgMspt);
            sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "Server MSPT: " +
                    (avgMspt < 40.0 ? TextFormatting.GREEN : avgMspt < 50.0 ? TextFormatting.YELLOW : TextFormatting.RED) +
                    String.format(Locale.ROOT, "%.1f ms", avgMspt) +
                    TextFormatting.GRAY + " | TPS: " +
                    (tps > 19.0 ? TextFormatting.GREEN : tps > 15.0 ? TextFormatting.YELLOW : TextFormatting.RED) +
                    String.format(Locale.ROOT, "%.1f", tps)));

            Map<Integer, TickScheduler.LevelData> dataMap = TickScheduler.INSTANCE.levelData;
            if (dataMap.isEmpty()) {
                sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "No dimension statistics recorded yet."));
            } else {
                for (TickScheduler.LevelData data : dataMap.values()) {
                    sender.sendMessage(new TextComponentString(TextFormatting.AQUA + data.toLog()));
                }
            }
            return;
        }

        if ("top".equalsIgnoreCase(args[0])) {
            int targetDim = 0;
            if (args.length > 1) {
                try {
                    targetDim = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(new TextComponentString(TextFormatting.RED + "Invalid dimension ID: " + args[1]));
                    return;
                }
            } else {
                Entity senderEntity = sender.getCommandSenderEntity();
                if (senderEntity != null) {
                    targetDim = senderEntity.dimension;
                }
            }

            TickScheduler.LevelData data = TickScheduler.INSTANCE.levelData.get(targetDim);
            if (data == null || data.previousStats == null || data.previousStats.topEntityCounts.isEmpty()) {
                sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "No entity breakdown recorded for Dim " + targetDim + "."));
                return;
            }

            sender.sendMessage(new TextComponentString(TextFormatting.GOLD + "=== Top Loaded Entities (Dim " + targetDim + ") ==="));
            Map<String, Integer> counts = data.previousStats.topEntityCounts;
            counts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(10)
                    .forEach(entry -> {
                        sender.sendMessage(new TextComponentString(
                                TextFormatting.GRAY + " - " + TextFormatting.WHITE + entry.getKey() +
                                TextFormatting.AQUA + ": " + TextFormatting.YELLOW + entry.getValue() + TextFormatting.GRAY + " loaded"));
                    });
            return;
        }

        if ("stress".equalsIgnoreCase(args[0])) {
            sender.sendMessage(new TextComponentString(TextFormatting.GOLD + "=== Server Stress & Anti-Lag Metrics ==="));
            double avgMspt = TickScheduler.getAverageTickMs(server);
            double tps = avgMspt <= 50.0 ? 20.0 : Math.min(20.0, 1000.0 / avgMspt);
            int threshold = ModConfig.TUNING.stressedThreshold;

            sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "Tick Time: " +
                    String.format(Locale.ROOT, "%.2f ms", avgMspt) + TextFormatting.GRAY +
                    " (TPS: " + String.format(Locale.ROOT, "%.1f", tps) + ")" +
                    TextFormatting.GRAY + " | Stress Threshold: " + TextFormatting.YELLOW + threshold + " ms"));

            Map<Integer, TickScheduler.LevelData> dataMap = TickScheduler.INSTANCE.levelData;
            if (dataMap.isEmpty()) {
                sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "No active dimension stress tracked."));
            } else {
                for (TickScheduler.LevelData data : dataMap.values()) {
                    double antiStress = 1.0 - ((double) data.stressedTicks / TickScheduler.MAX_STRESS_TICKS) * ModConfig.TUNING.minDecreaseFactor;
                    sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "Dim " + data.dimensionId + ": " +
                            TextFormatting.AQUA + "Stress: " + data.stressedTicks + "/" + TickScheduler.MAX_STRESS_TICKS +
                            TextFormatting.GRAY + " | Factor: " + String.format(Locale.ROOT, "%.2fx", antiStress) +
                            TextFormatting.GRAY + " | Total Stressed Ticks: " + TextFormatting.YELLOW + data.lifeTimeStressedTicks));
                }
            }
            return;
        }

        if ("reload".equalsIgnoreCase(args[0])) {
            if (!sender.canUseCommand(2, getName())) {
                sender.sendMessage(new TextComponentString(TextFormatting.RED + "You do not have permission to reload the configuration."));
                return;
            }
            ConfigManager.sync("immersive_optimization", Config.Type.INSTANCE);
            ModConfig.initCache();
            sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "Immersive Optimization configuration reloaded."));
            return;
        }

        if ("toggle".equalsIgnoreCase(args[0])) {
            if (!sender.canUseCommand(2, getName())) {
                sender.sendMessage(new TextComponentString(TextFormatting.RED + "You do not have permission to toggle optimization."));
                return;
            }
            ModConfig.GENERAL.enableEntities = !ModConfig.GENERAL.enableEntities;
            ConfigManager.sync("immersive_optimization", Config.Type.INSTANCE);
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Entity throttling is now: " +
                    (ModConfig.GENERAL.enableEntities ? TextFormatting.GREEN + "ENABLED" : TextFormatting.RED + "DISABLED")));
            return;
        }

        sender.sendMessage(new TextComponentString(TextFormatting.RED + "Usage: " + getUsage(sender)));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "status", "info", "top", "stress", "reload", "toggle");
        }
        if (args.length == 2 && "top".equalsIgnoreCase(args[0])) {
            Set<Integer> dims = TickScheduler.INSTANCE.levelData.keySet();
            List<String> dimStrings = dims.stream().map(String::valueOf).collect(Collectors.toList());
            return getListOfStringsMatchingLastWord(args, dimStrings);
        }
        return Collections.emptyList();
    }
}
