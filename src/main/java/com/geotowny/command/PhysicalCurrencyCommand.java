package com.geotowny.command;

import com.geotowny.GeoTownyPlugin;
import com.geotowny.config.PluginConfig;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.money.Money;
import com.geotowny.service.PhysicalCurrencyService;
import com.geotowny.util.Result;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class PhysicalCurrencyCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "info", "denominations", "withdraw", "deposit", "issued", "reset");

    private final GeoTownyPlugin plugin;
    private final GeoTownyCore core;
    private final PluginConfig config;

    public PhysicalCurrencyCommand(GeoTownyPlugin plugin, GeoTownyCore core, PluginConfig config) {
        this.plugin = plugin;
        this.core = core;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            info(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "info" -> info(sender);
            case "denominations" -> denominations(sender);
            case "withdraw" -> withdraw(sender, args);
            case "deposit" -> deposit(sender, args);
            case "issued" -> issued(sender, args);
            case "reset" -> reset(sender);
            default -> usage(sender, label);
        }
        return true;
    }

    private void info(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, "只有玩家可以查看纸币背包统计。");
            return;
        }
        if (!requireUse(player)) {
            return;
        }
        PhysicalCurrencyService service = plugin.physicalCurrencyService();
        send(player, ChatColor.GOLD + "========== 实体货币 ==========");
        send(player, "手续费: " + String.format("%.0f%%", config.physicalCurrencyFeeRate() * 100)
                + "（仅提现收取）");
        send(player, "背包纸币总值: " + service.totalValue(player).format() + " " + config.currencyName());
        for (Money denomination : PhysicalCurrencyService.DENOMINATIONS) {
            int count = service.count(player, denomination);
            if (count > 0) {
                send(player, " - " + service.denominationName(denomination) + " × " + count);
            }
        }
        send(player, "提现: /currency withdraw <面额>");
        send(player, "充值: /currency deposit <面额> <张数>");
    }

    private void denominations(CommandSender sender) {
        send(sender, ChatColor.GOLD + "=== 可用纸币面额 ===");
        send(sender, plugin.physicalCurrencyService().validDenominations());
        send(sender, "提现收取 " + String.format("%.0f%%", config.physicalCurrencyFeeRate() * 100)
                + " 手续费，充值免费。");
    }

    private void withdraw(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requireUse(player)) {
            return;
        }
        if (args.length < 2) {
            send(sender, "用法: /currency withdraw <面额>");
            return;
        }
        Money amount = parseMoney(sender, args[1]);
        if (amount == null) {
            return;
        }
        Resident resident = core.getOrCreateResident(player.getUniqueId(), player.getName());
        Result result = plugin.physicalCurrencyService().withdraw(player, resident, amount);
        send(sender, (result.isSuccess() ? ChatColor.GREEN : ChatColor.RED) + result.getMessage());
    }

    private void deposit(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requireUse(player)) {
            return;
        }
        if (args.length < 3) {
            send(sender, "用法: /currency deposit <面额> <张数>");
            return;
        }
        Money amount = parseMoney(sender, args[1]);
        if (amount == null) {
            return;
        }
        int count;
        try {
            count = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            send(sender, "张数格式无效。");
            return;
        }
        Resident resident = core.getOrCreateResident(player.getUniqueId(), player.getName());
        Result result = plugin.physicalCurrencyService().deposit(player, resident, amount, count);
        send(sender, (result.isSuccess() ? ChatColor.GREEN : ChatColor.RED) + result.getMessage());
    }

    private void issued(CommandSender sender, String[] args) {
        if (!sender.hasPermission("geotowny.currency.admin")) {
            send(sender, "你没有权限执行该操作。");
            return;
        }
        PhysicalCurrencyService service = plugin.physicalCurrencyService();
        if (args.length > 1) {
            Money amount = parseMoney(sender, args[1]);
            if (amount == null) {
                return;
            }
            send(sender, service.denominationName(amount) + " 发行量: " + service.getIssued(amount));
            return;
        }
        send(sender, ChatColor.GOLD + "=== 纸币发行量 ===");
        for (Money denomination : PhysicalCurrencyService.DENOMINATIONS) {
            send(sender, " - " + service.denominationName(denomination)
                    + ": " + service.getIssued(denomination));
        }
    }

    private void reset(CommandSender sender) {
        if (!sender.hasPermission("geotowny.currency.admin")) {
            send(sender, "你没有权限执行该操作。");
            return;
        }
        plugin.physicalCurrencyService().resetIssued();
        send(sender, "纸币发行量统计已重置。");
    }

    private Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, "只有玩家可以执行该操作。");
            return null;
        }
        return player;
    }

    private boolean requireUse(Player player) {
        if (!player.hasPermission("geotowny.currency.use")) {
            send(player, "你没有权限使用实体货币。");
            return false;
        }
        return true;
    }

    private Money parseMoney(CommandSender sender, String value) {
        try {
            Money amount = Money.parse(value);
            if (!PhysicalCurrencyService.isValidDenomination(amount)) {
                send(sender, "无效面额，可选: " + plugin.physicalCurrencyService().validDenominations());
                return null;
            }
            return amount;
        } catch (RuntimeException e) {
            send(sender, "面额格式无效: " + value);
            return null;
        }
    }

    private void usage(CommandSender sender, String label) {
        send(sender, "/" + label + " info | denominations | issued");
        send(sender, "/" + label + " withdraw <面额>");
        send(sender, "/" + label + " deposit <面额> <张数>");
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.GOLD + "[GeoTowny] " + ChatColor.RESET + message);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("withdraw")
                || args[0].equalsIgnoreCase("deposit") || args[0].equalsIgnoreCase("issued"))) {
            return filter(java.util.Arrays.stream(PhysicalCurrencyService.DENOMINATIONS)
                    .map(Money::format).toList(), args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}