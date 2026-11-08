package com.geotowny.gui;

import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.Town;
import com.geotowny.domain.model.TownRank;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TownMemberGui extends AbstractGui {

    public static final int SIZE = 54;
    public static final int MEMBERS_PER_PAGE = 45;
    public static final int SLOT_PREV = 45;
    public static final int SLOT_INFO = 49;
    public static final int SLOT_NEXT = 53;

    private final Town town;
    private final GeoTownyCore core;
    private final List<Resident> members;
    private int page;

    public TownMemberGui(Plugin plugin, Player player, Town town, GeoTownyCore core, int page) {
        super(plugin, player, SIZE, ChatColor.AQUA + "城镇成员");
        this.town = town;
        this.core = core;
        this.page = Math.max(0, page);
        this.members = new ArrayList<>();
        for (String uuid : town.getResidentUuids()) {
            Resident resident = core.getResident(uuid);
            if (resident != null) {
                members.add(resident);
            }
        }
        refresh();
    }

    public void refresh() {
        getInventory().clear();
        int pages = totalPages();
        page = Math.min(page, pages - 1);
        int start = page * MEMBERS_PER_PAGE;

        for (int slot = 0; slot < MEMBERS_PER_PAGE; slot++) {
            int index = start + slot;
            if (index >= members.size()) {
                break;
            }
            getInventory().setItem(slot, icon(members.get(index)));
        }

        ItemStack pane = pane();
        for (int slot = MEMBERS_PER_PAGE; slot < SIZE; slot++) {
            if (slot != SLOT_PREV && slot != SLOT_INFO && slot != SLOT_NEXT) {
                getInventory().setItem(slot, pane);
            }
        }

        ItemStack prev = new ItemStack(page > 0 ? Material.SPECTRAL_ARROW : Material.ARROW);
        ItemMeta prevMeta = prev.getItemMeta();
        if (prevMeta != null) {
            prevMeta.setDisplayName(ChatColor.YELLOW + "上一页 (第 " + (page + 1) + "/" + pages + " 页)");
            prev.setItemMeta(prevMeta);
        }
        getInventory().setItem(SLOT_PREV, prev);

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(ChatColor.AQUA + town.getName() + " 成员列表");
            infoMeta.setLore(List.of(
                    ChatColor.GREEN + "成员数: " + members.size(),
                    ChatColor.YELLOW + "页码: " + (page + 1) + "/" + pages));
            info.setItemMeta(infoMeta);
        }
        getInventory().setItem(SLOT_INFO, info);

        ItemStack next = new ItemStack(page + 1 < pages ? Material.SPECTRAL_ARROW : Material.ARROW);
        ItemMeta nextMeta = next.getItemMeta();
        if (nextMeta != null) {
            nextMeta.setDisplayName(ChatColor.YELLOW + "下一页 (第 " + (page + 1) + "/" + pages + " 页)");
            next.setItemMeta(nextMeta);
        }
        getInventory().setItem(SLOT_NEXT, next);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() != getInventory()) {
            return;
        }
        int slot = event.getSlot();
        if (slot == SLOT_PREV) {
            if (page > 0) {
                reopen(page - 1);
            }
            return;
        }
        if (slot == SLOT_NEXT) {
            if (page + 1 < totalPages()) {
                reopen(page + 1);
            }
            return;
        }
        if (slot == SLOT_INFO) {
            refresh();
            return;
        }
        if (slot >= MEMBERS_PER_PAGE) {
            return;
        }
        int index = page * MEMBERS_PER_PAGE + slot;
        if (index >= members.size()) {
            return;
        }
        Resident member = members.get(index);
        player.sendMessage(ChatColor.GOLD + "======== 成员信息 ========");
        player.sendMessage(ChatColor.YELLOW + "姓名: " + ChatColor.WHITE + member.getName());
        player.sendMessage(ChatColor.YELLOW + "职位: " + ChatColor.WHITE
                + rankName(member.getTownRank()));
        player.sendMessage(ChatColor.YELLOW + "状态: " + (isOnline(member) ? ChatColor.GREEN + "在线"
                : ChatColor.GRAY + "离线"));
        player.sendMessage(ChatColor.YELLOW + "最后在线: " + ChatColor.WHITE
                + formatLastOnline(member.getLastOnline()));
    }

    private void reopen(int newPage) {
        player.closeInventory();
        plugin.getServer().getScheduler().runTask(plugin,
                () -> new TownMemberGui(plugin, player, town, core, newPage).open());
    }

    private int totalPages() {
        return Math.max(1, (members.size() + MEMBERS_PER_PAGE - 1) / MEMBERS_PER_PAGE);
    }

    private ItemStack icon(Resident resident) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.WHITE + resident.getName());
            meta.setLore(List.of(
                    ChatColor.YELLOW + "职位: " + rankName(resident.getTownRank()),
                    ChatColor.GRAY + "状态: " + (isOnline(resident) ? ChatColor.GREEN + "● 在线"
                            : ChatColor.GRAY + "● 离线"),
                    ChatColor.DARK_GRAY + "最后在线: " + formatLastOnline(resident.getLastOnline()),
                    ChatColor.GRAY + "点击查看详情"));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private boolean isOnline(Resident resident) {
        try {
            return Bukkit.getPlayer(UUID.fromString(resident.getUuidString())) != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String rankName(TownRank rank) {
        if (rank == null) {
            return "居民";
        }
        return switch (rank) {
            case MAYOR -> "市长";
            case ASSISTANT -> "助理";
            case SHERIFF -> "警长";
            case TREASURER -> "财务";
            case BUILDER -> "建筑工";
            case RESIDENT -> "居民";
        };
    }

    private static String formatLastOnline(long timestamp) {
        if (timestamp <= 0) {
            return "未知";
        }
        long diff = Math.max(0, System.currentTimeMillis() - timestamp);
        long days = diff / (24L * 60L * 60L * 1000L);
        if (days > 0) {
            return days + " 天前";
        }
        long hours = diff / (60L * 60L * 1000L);
        if (hours > 0) {
            return hours + " 小时前";
        }
        long minutes = diff / (60L * 1000L);
        return minutes > 0 ? minutes + " 分钟前" : "刚刚";
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