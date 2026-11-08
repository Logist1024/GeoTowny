package com.geotowny.command;

import com.geotowny.GeoTownyPlugin;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.checkin.CheckInRecord;
import com.geotowny.domain.model.Resident;
import com.geotowny.gui.CheckInGui;
import com.geotowny.service.CheckInService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class CheckInCommand implements CommandExecutor {

    private final GeoTownyPlugin plugin;
    private final CheckInService service;
    private final GeoTownyCore core;

    public CheckInCommand(GeoTownyPlugin plugin, CheckInService service, GeoTownyCore core) {
        this.plugin = plugin;
        this.service = service;
        this.core = core;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "只有玩家可以使用签到系统。");
            return true;
        }
        if (!player.hasPermission("geotowny.checkin")) {
            send(player, "你没有权限使用签到。");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("info")) {
            info(player);
            return true;
        }
        new CheckInGui(plugin, player, service, core, plugin.config()).open();
        return true;
    }

    private void info(Player player) {
        String uuid = player.getUniqueId().toString();
        CheckInRecord record = service.record(uuid);
        send(player, ChatColor.GOLD + "========== 签到统计 ==========");
        send(player, "连续天数: " + record.getCurrentStreak());
        send(player, "累计签到: " + record.getTotalCheckIns());
        send(player, "下次奖励: " + service.nextReward(uuid).format()
                + " " + plugin.config().currencyName());
        send(player, service.hasCheckedInToday(uuid)
                ? ChatColor.GREEN + "今天已签到"
                : ChatColor.RED + "今天还未签到，输入 /checkin 打开签到日历");
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.GOLD + "[GeoTowny] " + ChatColor.RESET + message);
    }
}