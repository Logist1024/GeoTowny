package com.geotowny.gui;

import com.geotowny.config.PluginConfig;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.checkin.CheckInRecord;
import com.geotowny.domain.model.Resident;
import com.geotowny.service.CheckInService;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.List;

public final class CheckInGui extends AbstractGui {

    public static final int SIZE = 54;
    public static final int SLOT_CHECK_IN = 45;
    public static final int SLOT_INFO = 49;

    private final CheckInService service;
    private final GeoTownyCore core;
    private final PluginConfig config;

    public CheckInGui(Plugin plugin, Player player, CheckInService service, GeoTownyCore core, PluginConfig config) {
        super(plugin, player, SIZE, ChatColor.GOLD + "签到日历");
        this.service = service;
        this.core = core;
        this.config = config;
        refresh();
    }

    public void refresh() {
        getInventory().clear();
        String uuid = player.getUniqueId().toString();
        CheckInRecord record = service.record(uuid);
        int cycle = Math.max(1, Math.min(service.cycleDays(), 7));
        int streak = record.getCurrentStreak();
        boolean checkedToday = service.hasCheckedInToday(uuid);
        int completed = streak <= 0 ? 0 : ((streak - 1) % cycle) + 1;

        for (int day = 0; day < cycle; day++) {
            int number = day + 1;
            Material material = Material.GRAY_STAINED_GLASS_PANE;
            String name = ChatColor.GRAY + "第 " + number + " 天";
            if (checkedToday) {
                if (day < completed - 1) {
                    material = Material.GREEN_STAINED_GLASS_PANE;
                    name = ChatColor.GREEN + "✓ 第 " + number + " 天";
                } else if (day == completed - 1) {
                    material = Material.LIME_STAINED_GLASS_PANE;
                    name = ChatColor.GREEN + "✓ 今天已签到（第 " + number + " 天）";
                }
            } else if (day < completed) {
                material = Material.GREEN_STAINED_GLASS_PANE;
                name = ChatColor.GREEN + "✓ 第 " + number + " 天";
            } else if (day == completed % cycle) {
                material = Material.YELLOW_STAINED_GLASS_PANE;
                name = ChatColor.GOLD + "→ 今日可签到（第 " + number + " 天）";
            }
            ItemStack dayItem = new ItemStack(material);
            ItemMeta meta = dayItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(name);
                meta.setLore(List.of(ChatColor.DARK_GRAY + "连续奖励按自然日累计"));
                dayItem.setItemMeta(meta);
            }
            getInventory().setItem(day, dayItem);
        }

        ItemStack pane = pane();
        for (int slot = cycle; slot < SLOT_CHECK_IN; slot++) {
            getInventory().setItem(slot, pane);
        }
        for (int slot = SLOT_CHECK_IN + 1; slot < SIZE; slot++) {
            if (slot != SLOT_INFO) {
                getInventory().setItem(slot, pane);
            }
        }

        ItemStack button;
        if (checkedToday) {
            button = new ItemStack(Material.BARRIER);
            ItemMeta meta = button.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.RED + "今天已签到");
                button.setItemMeta(meta);
            }
        } else {
            button = new ItemStack(Material.GOLD_INGOT);
            ItemMeta meta = button.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GOLD + "点击签到");
                meta.setLore(List.of(ChatColor.YELLOW + "本次奖励: "
                        + service.rewardFor(streak + 1).format() + " " + config.currencyName()));
                button.setItemMeta(meta);
            }
        }
        getInventory().setItem(SLOT_CHECK_IN, button);

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(ChatColor.AQUA + "签到统计");
            infoMeta.setLore(List.of(
                    ChatColor.GREEN + "连续天数: " + streak,
                    ChatColor.AQUA + "累计签到: " + record.getTotalCheckIns(),
                    ChatColor.GOLD + "下次奖励: " + service.rewardFor(streak + 1).format()
                            + " " + config.currencyName(),
                    ChatColor.GRAY + "周期: " + cycle + " 天"));
            info.setItemMeta(infoMeta);
        }
        getInventory().setItem(SLOT_INFO, info);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() != getInventory()) {
            return;
        }
        if (event.getSlot() != SLOT_CHECK_IN) {
            return;
        }
        Resident resident = core.getOrCreateResident(player.getUniqueId(), player.getName());
        CheckInService.CheckInResult result = service.checkIn(resident);
        player.sendMessage((result.success() ? ChatColor.GREEN : ChatColor.RED)
                + "[GeoTowny] " + result.message());
        refresh();
    }

    private static ItemStack pane() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        return pane;
    }
}