package com.geotowny.command;

import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.Nation;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.Town;
import com.geotowny.domain.money.Money;
import com.geotowny.service.ResidentService;
import com.geotowny.util.Result;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * /resident 命令（别名 /res）：居民信息、玩家间转账、在线统计。
 */
public final class ResidentCommand implements CommandExecutor, TabCompleter {

    private final GeoTownyCore core;
    private final ResidentService residentService;

    public ResidentCommand(GeoTownyCore core, ResidentService residentService) {
        this.core = core;
        this.residentService = residentService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            info(sender, args.length > 1 ? args[1] : null);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "pay" -> pay(sender, args);
            case "list" -> list(sender);
            default -> usage(sender, label);
        }
        return true;
    }

    private void pay(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 3) {
            send(sender, "用法: /resident pay <玩家> <金额>");
            return;
        }
        Resident senderResident = core.getOrCreateResident(player.getUniqueId(), player.getName());
        Resident target = core.getResidentByName(args[1]);
        if (target == null) {
            send(sender, "目标玩家不存在。");
            return;
        }
        Money amount;
        try {
            amount = Money.parse(args[2]);
        } catch (RuntimeException e) {
            send(sender, "金额格式无效: " + args[2]);
            return;
        }
        Result result = residentService.pay(senderResident, target, amount);
        send(sender, result.getMessage());
        Player targetPlayer = Bukkit.getPlayer(target.getUuid());
        if (targetPlayer != null && result.isSuccess()) {
            targetPlayer.sendMessage(ChatColor.GOLD + "[GeoTowny] " + ChatColor.RESET
                    + "收到 " + senderResident.getName() + " 的转账 " + amount.format()
                    + "，当前余额 " + target.getBalance().format() + "。");
        }
    }

    private void info(CommandSender sender, String name) {
        Resident resident;
        if (name != null) {
            resident = core.getResidentByName(name);
        } else if (sender instanceof Player player) {
            resident = core.getOrCreateResident(player.getUniqueId(), player.getName());
        } else {
            send(sender, "控制台请使用 /resident info <玩家>。");
            return;
        }
        if (resident == null) {
            send(sender, "居民不存在。");
            return;
        }
        Town town = core.getTown(resident.getTownUuid());
        Nation nation = town != null && town.hasNation() ? core.getNation(town.getNationUuid()) : null;
        send(sender, ChatColor.GOLD + "=== 居民 " + resident.getName() + " ===");
        send(sender, "城镇: " + (town == null ? "无" : town.getName())
                + " | 城镇职位: " + (resident.getTownRank() == null ? "无" : resident.getTownRank()));
        send(sender, "国家: " + (nation == null ? "无" : nation.getName())
                + " | 国家职位: " + (resident.getNationRank() == null ? "无" : resident.getNationRank()));
        send(sender, "余额: " + resident.getBalance().format()
                + " | 最后在线: " + resident.getLastOnline());
    }

    private void list(CommandSender sender) {
        int total = core.allResidents().size();
        int online = Bukkit.getOnlinePlayers().size();
        send(sender, "居民总数: " + total + " | 在线: " + online);
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        send(sender, "只有玩家可以执行该命令。");
        return null;
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.GOLD + "[GeoTowny] " + ChatColor.RESET + message);
    }

    private void usage(CommandSender sender, String label) {
        send(sender, "/" + label + " info [玩家] | pay <玩家> <金额> | list");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("info", "pay", "list").stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("pay"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}