package com.geotowny.listener;

import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.Resident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 玩家生命周期监听：创建/恢复居民，维护最后在线时间。
 */
public final class PlayerListener implements Listener {

    private final GeoTownyCore core;

    public PlayerListener(GeoTownyCore core) {
        this.core = core;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        core.getOrCreateResident(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Resident resident = core.getResident(event.getPlayer().getUniqueId());
        if (resident != null) {
            resident.setLastOnline(System.currentTimeMillis());
            core.saveResident(resident);
        }
    }
}