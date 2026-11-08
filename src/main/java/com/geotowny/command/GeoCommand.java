package com.geotowny.command;

import com.geotowny.GeoTownyPlugin;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.Nation;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.Town;
import com.geotowny.domain.model.WarState;
import com.geotowny.domain.money.Money;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * /geo 命令：全局统计、排行榜、保存/重载与管理员经济操作。
 */
public final class GeoCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "info", "save", "reload", "list", "influence", "war",
            "top", "festival", "version", "admin", "web");

    private static final List<String> TOP_OPTIONS = List.of(
            "wealth", "influence", "size", "online", "towns");

    private final GeoTownyPlugin plugin;
    private final GeoTownyCore core;

    public GeoCommand(GeoTownyPlugin plugin, GeoTownyCore core) {
        this.plugin = plugin;
        this.core = core;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            info(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "info" -> info(sender);
            case "save" -> save(sender);
            case "reload" -> reload(sender);
            case "list" -> list(sender, args);
            case "influence" -> influence(sender);
            case "war" -> war(sender, args);
            case "top" -> top(sender, args);
            case "festival" -> send(sender, "节日系统将在 P6 接入。");
            case "version" -> send(sender, "GeoTowny " + plugin.getDescription().getVersion()
                    + " (Spigot " + plugin.getDescription().getAPIVersion() + ")");
            case "admin" -> admin(sender, args);
            case "web" -> web(sender, args);
            default -> usage(sender, label);
        }
        return true;
    }

    private void info(CommandSender sender) {
        send(sender, ChatColor.GOLD + "=== GeoTowny 全局统计 ===");
        send(sender, "居民: " + core.allResidents().size()
                + " | 城镇: " + core.allTowns().size()
                + " | 国家: " + core.allNations().size()
                + " | 领地: " + core.allTownBlocks().size()
                + " | 在线: " + Bukkit.getOnlinePlayers().size());
    }

    private void save(CommandSender sender) {
        if (!sender.hasPermission("geotowny.admin")) {
            send(sender, "你没有权限执行该操作。");
            return;
        }
        core.saveAll();
        send(sender, "所有数据已保存。");
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("geotowny.admin.reload")) {
            send(sender, "你没有权限执行该操作。");
            return;
        }
        plugin.reloadPluginConfig();
        send(sender, "配置已重载（部分服务参数需重启生效）。");
    }

    private void list(CommandSender sender, String[] args) {
        String type = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "towns";
        if (type.equals("towns")) {
            send(sender, ChatColor.GOLD + "=== 城镇列表 (" + core.allTowns().size() + ") ===");
            core.allTowns().stream().limit(30).forEach(town -> send(sender,
                    " - " + town.getName() + " [" + town.getCode() + "] | 居民 "
                            + town.getResidentCount() + " | 领地 " + town.getClaimCount()));
            return;
        }
        if (type.equals("nations")) {
            send(sender, ChatColor.GOLD + "=== 国家列表 (" + core.allNations().size() + ") ===");
            core.allNations().stream().limit(30).forEach(nation -> send(sender,
                    " - " + nation.getName() + " [" + nation.getCode() + "] | 城镇 "
                            + nation.getTownCount() + " | 国体 " + nation.getIdeology().getDisplayName()));
            return;
        }
        send(sender, "用法: /geo list <towns|nations>");
    }

    private void influence(CommandSender sender) {
        List<Nation> ranked = core.allNations().stream()
                .sorted(Comparator.comparingDouble(Nation::getInfluence).reversed())
                .limit(10)
                .toList();
        if (ranked.isEmpty()) {
            send(sender, "当前没有国家。");
            return;
        }
        send(sender, ChatColor.GOLD + "=== 国家影响力排行 ===");
        int index = 1;
        for (Nation nation : ranked) {
            send(sender, index++ + ". " + nation.getName() + " - " + String.format("%.1f", nation.getInfluence()));
        }
    }

    private void war(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("list")) {
            send(sender, "用法: /geo war list");
            return;
        }
        send(sender, ChatColor.GOLD + "=== 参战国家 ===");
        boolean any = false;
        for (Nation nation : core.allNations()) {
            if (nation.getWarState() == WarState.ACTIVE) {
                send(sender, " - " + nation.getName() + " | " + nation.getWarState());
                any = true;
            }
        }
        if (!any) {
            send(sender, "当前没有处于战争状态的国家。");
        }
    }

    private void top(CommandSender sender, String[] args) {
        String type = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "influence";
        switch (type) {
            case "wealth" -> {
                send(sender, ChatColor.GOLD + "=== 财富排行 ===");
                core.allResidents().stream()
                        .sorted(Comparator.comparing(Resident::getBalance).reversed())
                        .limit(10)
                        .forEach(resident -> send(sender, " - " + resident.getName()
                                + " | " + resident.getBalance().format()));
            }
            case "influence" -> influence(sender);
            case "size" -> {
                send(sender, ChatColor.GOLD + "=== 领地规模排行 ===");
                core.allTowns().stream()
                        .sorted(Comparator.comparingInt(Town::getClaimCount).reversed())
                        .limit(10)
                        .forEach(town -> send(sender, " - " + town.getName()
                                + " | 领地 " + town.getClaimCount()));
            }
            case "online" -> {
                send(sender, ChatColor.GOLD + "=== 最近在线排行 ===");
                core.allResidents().stream()
                        .sorted(Comparator.comparingLong(Resident::getLastOnline).reversed())
                        .limit(10)
                        .forEach(resident -> send(sender, " - " + resident.getName()
                                + " | " + resident.getLastOnline()));
            }
            case "towns" -> {
                send(sender, ChatColor.GOLD + "=== 国家城镇数排行 ===");
                core.allNations().stream()
                        .sorted(Comparator.comparingInt(Nation::getTownCount).reversed())
                        .limit(10)
                        .forEach(nation -> send(sender, " - " + nation.getName()
                                + " | 城镇 " + nation.getTownCount()));
            }
            default -> send(sender, "用法: /geo top <wealth|influence|size|online|towns>");
        }
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("geotowny.admin.economy")) {
            send(sender, "你没有权限执行该操作。");
            return;
        }
        if (args.length < 4) {
            send(sender, "用法: /geo admin <pay|take|setbalance> <玩家> <金额>");
            return;
        }
        Resident target = core.getResidentByName(args[2]);
        if (target == null) {
            send(sender, "玩家不存在。");
            return;
        }
        Money amount;
        try {
            amount = Money.parse(args[3]);
        } catch (RuntimeException e) {
            send(sender, "金额格式无效: " + args[3]);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "pay" -> {
                target.deposit(amount);
                core.saveResident(target);
                send(sender, "已向 " + target.getName() + " 发放 " + amount.format() + "。");
            }
            case "take" -> {
                if (!target.withdraw(amount)) {
                    send(sender, "该玩家余额不足。");
                    return;
                }
                core.saveResident(target);
                send(sender, "已从 " + target.getName() + " 扣除 " + amount.format() + "。");
            }
            case "setbalance" -> {
                target.setBalance(amount);
                core.saveResident(target);
                send(sender, "已把 " + target.getName() + " 的余额设为 " + amount.format() + "。");
            }
            default -> send(sender, "用法: /geo admin <pay|take|setbalance> <玩家> <金额>");
        }
    }

    private void usage(CommandSender sender, String label) {
        send(sender, "/" + label + " info | save | reload | version");
        send(sender, "/" + label + " list <towns|nations> | influence | war list");
        send(sender, "/" + label + " top <wealth|influence|size|online|towns>");
        send(sender, "/" + label + " web login | status | logout | password <set|clear> [密码]");
        send(sender, "/" + label + " admin <pay|take|setbalance> <玩家> <金额>");
    }

    private void web(CommandSender sender, String[] args) {
        if (!sender.hasPermission("geotowny.web.login")) {
            send(sender, "你没有权限使用 Web 功能。");
            return;
        }
        if (args.length < 2) {
            send(sender, "用法: /geo web <login|logout|status|password set/clear>");
            return;
        }
        com.geotowny.web.WebService web = plugin.webService();
        if (web == null) {
            send(sender, "Web 服务尚未初始化。");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "login" -> {
                if (!(sender instanceof Player player)) {
                    send(sender, "只有玩家可以生成 Web 登录链接。");
                    return;
                }
                if (!web.isRunning()) {
                    send(sender, "Web 服务未运行，请检查 web.enabled 与端口配置。");
                    return;
                }
                String link = web.issueOneTimeToken(player.getUniqueId());
                send(sender, "一次性 Web 登录链接（" + plugin.config().webOneTimeExpiryMinutes() + " 分钟有效）：");
                send(sender, link);
                send(sender, "也可使用 /geo web password set <密码> 设置长期密码。");
            }
            case "logout" -> {
                if (!(sender instanceof Player player)) {
                    send(sender, "只有玩家可以注销 Web 会话。");
                    return;
                }
                web.revokeSessions(player.getUniqueId());
                send(sender, "已注销该玩家的所有 Web 会话。");
            }
            case "password" -> {
                if (!(sender instanceof Player player)) {
                    send(sender, "只有玩家可以设置 Web 密码。");
                    return;
                }
                if (args.length < 3) {
                    send(sender, "用法: /geo web password <set|clear> [密码]");
                    return;
                }
                if (args[2].equalsIgnoreCase("set")) {
                    if (args.length < 4) {
                        send(sender, "用法: /geo web password set <密码>");
                        return;
                    }
                    String password = args[3];
                    if (password.length() < 6 || password.length() > 128) {
                        send(sender, "密码长度必须在 6-128 字符之间。");
                        return;
                    }
                    if (web.setPassword(player.getUniqueId(), password)) {
                        send(sender, "Web 登录密码已设置。");
                    } else {
                        send(sender, "设置失败：玩家数据不存在。");
                    }
                } else if (args[2].equalsIgnoreCase("clear")) {
                    web.clearPassword(player.getUniqueId());
                    web.revokeSessions(player.getUniqueId());
                    send(sender, "Web 登录密码已清除，相关会话已注销。");
                } else {
                    send(sender, "用法: /geo web password <set|clear> [密码]");
                }
            }
            case "status" -> {
                send(sender, "Web 服务: " + (web.isRunning() ? "运行中" : "未运行"));
                send(sender, "地址: " + web.baseUrl());
                send(sender, "绑定: " + plugin.config().webBind() + ":" + plugin.config().webPort()
                        + " | 会话有效期: " + plugin.config().webSessionExpiryHours() + " 小时");
            }
            default -> send(sender, "用法: /geo web <login|logout|status|password set/clear>");
        }
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.GOLD + "[GeoTowny] " + ChatColor.RESET + message);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("list")) {
                return filter(List.of("towns", "nations"), args[1]);
            }
            if (sub.equals("war")) {
                return filter(List.of("list"), args[1]);
            }
            if (sub.equals("top")) {
                return filter(TOP_OPTIONS, args[1]);
            }
            if (sub.equals("admin")) {
                return filter(List.of("pay", "take", "setbalance"), args[1]);
            }
            if (sub.equals("web")) {
                return filter(List.of("login", "logout", "status", "password"), args[1]);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("web")
                && args[1].equalsIgnoreCase("password")) {
            return filter(List.of("set", "clear"), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}