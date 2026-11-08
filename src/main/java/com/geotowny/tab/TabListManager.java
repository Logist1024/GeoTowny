package com.geotowny.tab;

import com.geotowny.GeoTownyPlugin;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.Nation;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.Town;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Tab 列表与聊天前缀：[国家][城镇]玩家名。
 */
public final class TabListManager implements Listener {

    private final GeoTownyPlugin plugin;
    private final GeoTownyCore core;
    private BukkitTask task;

    public TabListManager(GeoTownyPlugin plugin, GeoTownyCore core) {
        this.plugin = plugin;
        this.core = core;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 40L, 40L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        update(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Resident resident = core.getResident(event.getPlayer().getUniqueId());
        event.setFormat(prefix(resident) + "%1$s" + ChatColor.WHITE + ": %2$s");
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    public void update(Player player) {
        Resident resident = core.getResident(player.getUniqueId());
        player.setPlayerListName(prefix(resident) + player.getName());
    }

    private String prefix(Resident resident) {
        if (resident == null) {
            return "";
        }
        Town town = core.getTown(resident.getTownUuid());
        Nation nation = town != null && town.hasNation() ? core.getNation(town.getNationUuid()) : null;
        StringBuilder builder = new StringBuilder();
        if (nation != null) {
            builder.append(ChatColor.GOLD).append('[').append(nation.getName()).append(']');
        }
        if (town != null) {
            builder.append(ChatColor.GREEN).append('[').append(town.getName()).append(']');
        }
        return builder.toString();
    }
}