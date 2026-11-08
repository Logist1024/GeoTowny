package com.geotowny.command;

import com.geotowny.GeoTownyPlugin;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.PlotType;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.TownBlock;
import com.geotowny.domain.money.Money;
import com.geotowny.service.PlotService;
import com.geotowny.util.Result;
import com.geotowny.world.WorldRegistry;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /plot 命令（别名 /p）：个人地块认领、类型、出售、购买、信任。
 */
public final class PlotCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "claim", "unclaim", "set", "fs", "nfs", "buy", "perms", "info");

    private static final List<String> PLOT_TYPES = List.of(
            "DEFAULT", "SHOP", "FARM", "EMBASSY", "FORT", "BANK", "ARENA", "INN");

    private final GeoTownyCore core;
    private final PlotService plotService;
    private final WorldRegistry worlds;

    public PlotCommand(GeoTownyPlugin plugin, GeoTownyCore core, PlotService plotService,
                       WorldRegistry worlds) {
        this.core = core;
        this.plotService = plotService;
        this.worlds = worlds;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            usage(sender, label);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "claim" -> claim(sender);
            case "unclaim" -> unclaim(sender);
            case "set" -> set(sender, args);
            case "fs" -> forSale(sender, args);
            case "nfs" -> cancelSale(sender);
            case "buy" -> buy(sender);
            case "perms" -> perms(sender, args);
            case "info" -> info(sender);
            default -> usage(sender, label);
        }
        return true;
    }

    private void claim(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender)) {
            return;
        }
        Result result = plotService.claimPlot(resident(player), worldKey(player),
                chunkX(player), chunkZ(player));
        send(sender, result.getMessage());
    }

    private void unclaim(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender)) {
            return;
        }
        Result result = plotService.unclaimPlot(resident(player), worldKey(player),
                chunkX(player), chunkZ(player));
        send(sender, result.getMessage());
    }

    private void set(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender)) {
            return;
        }
        if (args.length < 2) {
            send(sender, "用法: /plot set <" + String.join("|", PLOT_TYPES) + ">");
            return;
        }
        PlotType type;
        try {
            type = PlotType.valueOf(args[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            send(sender, "未知地块类型: " + args[1]);
            return;
        }
        Result result = plotService.setPlotType(resident(player), worldKey(player),
                chunkX(player), chunkZ(player), type);
        send(sender, result.getMessage());
    }

    private void forSale(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender)) {
            return;
        }
        Money price = Money.ZERO;
        if (args.length > 1) {
            try {
                price = Money.parse(args[1]);
            } catch (RuntimeException e) {
                send(sender, "价格格式无效: " + args[1]);
                return;
            }
        }
        Result result = plotService.setForSale(resident(player), worldKey(player),
                chunkX(player), chunkZ(player), price);
        send(sender, result.getMessage());
    }

    private void cancelSale(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender)) {
            return;
        }
        Result result = plotService.cancelSale(resident(player), worldKey(player),
                chunkX(player), chunkZ(player));
        send(sender, result.getMessage());
    }

    private void buy(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender)) {
            return;
        }
        Result result = plotService.buyPlot(resident(player), worldKey(player),
                chunkX(player), chunkZ(player));
        send(sender, result.getMessage());
    }

    private void perms(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender)) {
            return;
        }
        TownBlock block = core.getTownBlockAt(worldKey(player), chunkX(player), chunkZ(player));
        if (block == null) {
            send(sender, "当前区块无地块。");
            return;
        }
        if (args.length == 1) {
            if (block.getTrustedPlayers().isEmpty()) {
                send(sender, "该地块没有信任玩家。");
                return;
            }
            send(sender, "信任玩家: " + String.join(", ", block.getTrustedPlayers()));
            return;
        }
        if (args.length < 3) {
            send(sender, "用法: /plot perms <add|remove> <玩家>");
            return;
        }
        Resident target = core.getResidentByName(args[2]);
        Result result = switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> plotService.trust(resident(player), worldKey(player),
                    chunkX(player), chunkZ(player), target);
            case "remove" -> plotService.untrust(resident(player), worldKey(player),
                    chunkX(player), chunkZ(player), target);
            default -> Result.fail("未知操作: " + args[1]);
        };
        send(sender, result.getMessage());
    }

    private void info(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender)) {
            return;
        }
        TownBlock block = core.getTownBlockAt(worldKey(player), chunkX(player), chunkZ(player));
        if (block == null) {
            send(sender, "当前区块未被任何城镇声明。");
            return;
        }
        var town = core.getTown(block.getTownUuid());
        Resident owner = core.getResident(block.getOwnerUuid());
        send(sender, ChatColor.GOLD + "=== 地块 " + block.key() + " ===");
        send(sender, "城镇: " + (town == null ? "?" : town.getName())
                + " | 类型: " + block.getPlotType());
        send(sender, "主人: " + (owner == null ? "公共地块" : owner.getName())
                + " | 出售: " + (block.isForSale() ? block.getPrice().format() : "否"));
        send(sender, "信任玩家: " + block.getTrustedPlayers().size());
    }

    private Resident resident(Player player) {
        return core.getOrCreateResident(player.getUniqueId(), player.getName());
    }

    private String worldKey(Player player) {
        return worlds.toInternalKey(player.getWorld());
    }

    private int chunkX(Player player) {
        return player.getLocation().getChunk().getX();
    }

    private int chunkZ(Player player) {
        return player.getLocation().getChunk().getZ();
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        send(sender, "只有玩家可以执行该命令。");
        return null;
    }

    private boolean require(CommandSender sender) {
        if (!sender.hasPermission("geotowny.plot.use")) {
            send(sender, "你没有权限执行该操作。");
            return false;
        }
        return true;
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.GOLD + "[GeoTowny] " + ChatColor.RESET + message);
    }

    private void usage(CommandSender sender, String label) {
        send(sender, "/" + label + " claim|unclaim|info|buy");
        send(sender, "/" + label + " set <类型> | fs [价格] | nfs");
        send(sender, "/" + label + " perms [add|remove] <玩家>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return filter(PLOT_TYPES, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("perms")) {
            return filter(List.of("add", "remove"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("perms")) {
            return filter(org.bukkit.Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}