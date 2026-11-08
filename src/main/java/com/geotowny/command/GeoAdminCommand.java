package com.geotowny.command;

import com.geotowny.GeoTownyPlugin;
import com.geotowny.consistency.ConsistencyChecker;
import com.geotowny.consistency.ConsistencyChecker.Issue;
import com.geotowny.consistency.ConsistencyChecker.RepairResult;
import com.geotowny.consistency.ConsistencyChecker.Report;
import com.geotowny.consistency.ConsistencyChecker.Severity;
import com.geotowny.util.ItemCodec;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Locale;

/**
 * 管理命令：version / reload / verify / repair。
 * <p>业务命令（town/nation/...）在 P3 接入。
 */
public final class GeoAdminCommand implements CommandExecutor, TabCompleter {

    private static final int ISSUE_DISPLAY_LIMIT = 20;
    private static final List<String> SUBCOMMANDS = List.of("version", "reload", "verify", "repair");

    private final GeoTownyPlugin plugin;
    private final ConsistencyChecker consistencyChecker = new ConsistencyChecker();

    public GeoAdminCommand(GeoTownyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("geotowny.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有权限执行该命令。");
            return true;
        }
        if (args.length == 0) {
            usage(sender, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "version" -> sender.sendMessage(ChatColor.GOLD + "GeoTowny "
                    + plugin.getDescription().getVersion()
                    + ChatColor.GRAY + " (Spigot " + plugin.getDescription().getAPIVersion() + ")");
            case "reload" -> {
                if (!sender.hasPermission("geotowny.admin.reload")) {
                    sender.sendMessage(ChatColor.RED + "你没有权限重载配置。");
                    return true;
                }
                plugin.reloadPluginConfig();
                sender.sendMessage(ChatColor.GREEN + "GeoTowny 配置已重载。");
            }
            case "verify" -> verify(sender);
            case "repair" -> repair(sender);
            default -> usage(sender, label);
        }
        return true;
    }

    private void verify(CommandSender sender) {
        if (!sender.hasPermission("geotowny.admin.verify")) {
            sender.sendMessage(ChatColor.RED + "你没有权限执行一致性检查。");
            return;
        }
        if (plugin.storage() == null) {
            sender.sendMessage(ChatColor.RED + "数据层未就绪。");
            return;
        }
        Report report = consistencyChecker.check(plugin.storage().snapshot());
        sender.sendMessage(ChatColor.GOLD + "一致性检查: " + ChatColor.WHITE + report.summary());
        printIssues(sender, report.issues());
        if (!report.issues().isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "使用 /geotowny repair 执行自动修复。");
        }
        verifyItemCodec(sender);
    }

    private void verifyItemCodec(CommandSender sender) {
        try {
            ItemStack sample = new ItemStack(Material.STONE, 1);
            ItemMeta meta = sample.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("GeoTownyCodecProbe");
                sample.setItemMeta(meta);
            }
            String encoded = ItemCodec.encode(sample);
            ItemStack decoded = ItemCodec.decode(encoded);
            boolean ok = decoded != null
                    && decoded.getType() == Material.STONE
                    && decoded.getAmount() == 1
                    && decoded.getItemMeta() != null
                    && "GeoTownyCodecProbe".equals(decoded.getItemMeta().getDisplayName());
            sender.sendMessage((ok ? ChatColor.GREEN : ChatColor.RED)
                    + "物品序列化自检: " + (ok ? "通过" : "失败"));
        } catch (RuntimeException e) {
            sender.sendMessage(ChatColor.RED + "物品序列化自检异常: " + e.getMessage());
        }
    }

    private void repair(CommandSender sender) {
        if (!sender.hasPermission("geotowny.admin.verify")) {
            sender.sendMessage(ChatColor.RED + "你没有权限执行一致性修复。");
            return;
        }
        if (plugin.storage() == null) {
            sender.sendMessage(ChatColor.RED + "数据层未就绪。");
            return;
        }
        RepairResult result = consistencyChecker.repair(plugin.storage().snapshot());
        plugin.storage().applySnapshot(result.snapshot());
        sender.sendMessage(ChatColor.GOLD + "一致性修复: " + ChatColor.WHITE + result.summary());
        printIssues(sender, result.fixed());
        if (result.residual().hasErrors()) {
            sender.sendMessage(ChatColor.RED + "仍有未修复错误 " + result.residual().errorCount()
                    + " 项，请检查日志或手动处理。");
        }
    }

    private void printIssues(CommandSender sender, List<Issue> issues) {
        issues.stream().limit(ISSUE_DISPLAY_LIMIT).forEach(issue -> {
            ChatColor color = issue.severity() == Severity.ERROR ? ChatColor.RED : ChatColor.YELLOW;
            sender.sendMessage(color + "[" + issue.type() + "] " + issue.message());
        });
        if (issues.size() > ISSUE_DISPLAY_LIMIT) {
            sender.sendMessage(ChatColor.GRAY + "（仅显示前 " + ISSUE_DISPLAY_LIMIT + " 条，共 "
                    + issues.size() + " 条）");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(sub -> sub.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }

    private static void usage(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.YELLOW + "用法: /" + label + " <version|reload|verify|repair>");
    }
}