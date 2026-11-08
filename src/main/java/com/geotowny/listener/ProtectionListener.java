package com.geotowny.listener;

import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.Nation;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.Town;
import com.geotowny.domain.model.TownBlock;
import com.geotowny.domain.model.TownLog;
import com.geotowny.service.ProtectionService;
import com.geotowny.service.TownLogService;
import com.geotowny.world.WorldRegistry;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * 领地保护与行为日志监听。
 */
public final class ProtectionListener implements Listener {

    private final GeoTownyCore core;
    private final ProtectionService protection;
    private final TownLogService logService;
    private final WorldRegistry worlds;

    public ProtectionListener(GeoTownyCore core, ProtectionService protection,
                              TownLogService logService, WorldRegistry worlds) {
        this.core = core;
        this.protection = protection;
        this.logService = logService;
        this.worlds = worlds;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Resident resident = core.getResident(player.getUniqueId());
        if (resident == null) {
            return;
        }
        Block block = event.getBlock();
        String worldKey = worlds.toInternalKey(block.getWorld());
        if (!protection.canBreak(resident, worldKey, block.getX(), block.getZ())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "[GeoTowny] 该区域受保护，无法破坏。");
            return;
        }
        log(resident, worldKey, block, TownLog.ActionType.BREAK, block.getType().name());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Resident resident = core.getResident(player.getUniqueId());
        if (resident == null) {
            return;
        }
        Block block = event.getBlock();
        String worldKey = worlds.toInternalKey(block.getWorld());
        if (!protection.canPlace(resident, worldKey, block.getX(), block.getZ())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "[GeoTowny] 该区域受保护，无法放置。");
            return;
        }
        log(resident, worldKey, block, TownLog.ActionType.PLACE, block.getType().name());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        Resident resident = core.getResident(player.getUniqueId());
        if (resident == null) {
            return;
        }
        Block block = event.getClickedBlock();
        String worldKey = worlds.toInternalKey(block.getWorld());
        if (!protection.canInteract(resident, worldKey, block.getX(), block.getZ())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "[GeoTowny] 该区域受保护，无法交互。");
            return;
        }
        log(resident, worldKey, block, TownLog.ActionType.OPEN_CHEST, block.getType().name());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)
                || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Resident resident = core.getResident(attacker.getUniqueId());
        if (resident == null) {
            return;
        }
        String worldKey = worlds.toInternalKey(victim.getWorld());
        if (!protection.canPvp(resident, worldKey, victim.getLocation().getBlockX(),
                victim.getLocation().getBlockZ())) {
            event.setCancelled(true);
            attacker.sendMessage(ChatColor.RED + "[GeoTowny] 该区域禁止 PVP。");
            return;
        }
        TownBlock block = protection.blockAt(worldKey, victim.getLocation().getBlockX(),
                victim.getLocation().getBlockZ());
        if (block != null) {
            log(resident, worldKey, victim.getLocation().getBlock(), TownLog.ActionType.PVP,
                    victim.getName());
        }
    }

    private void log(Resident resident, String worldKey, Block block,
                     TownLog.ActionType action, String detail) {
        TownBlock townBlock = protection.blockAt(worldKey, block.getX(), block.getZ());
        if (townBlock == null) {
            return;
        }
        Town town = core.getTown(townBlock.getTownUuid());
        if (town == null) {
            return;
        }
        Nation nation = town.hasNation() ? core.getNation(town.getNationUuid()) : null;
        TownLog log = new TownLog(System.currentTimeMillis(), town.getUuid(),
                resident.getUuidString(), resident.getName(),
                town.getName(), nation == null ? null : nation.getName(),
                action, worldKey, block.getX(), block.getY(), block.getZ(), detail);
        logService.add(log);
    }
}