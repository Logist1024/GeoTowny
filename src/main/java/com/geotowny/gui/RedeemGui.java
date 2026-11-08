package com.geotowny.gui;

import com.geotowny.config.PluginConfig;
import com.geotowny.domain.money.Money;
import com.geotowny.domain.redeem.RedeemCode;
import com.geotowny.service.RedeemService;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public final class RedeemGui extends AbstractGui {

    public static final int SIZE = 54;
    public static final int ITEMS_PER_PAGE = 45;
    public static final int SLOT_PREV = 45;
    public static final int SLOT_INFO = 49;
    public static final int SLOT_GEN_GEO = 50;
    public static final int SLOT_GEN_ITEM = 51;
    public static final int SLOT_GEN_CMD = 52;
    public static final int SLOT_NEXT = 53;

    private final RedeemService service;
    private final PluginConfig config;
    private int page;
    private List<RedeemCode> cachedPage = new ArrayList<>();
    private int cachedPages = 1;

    public RedeemGui(Plugin plugin, Player player, RedeemService service, PluginConfig config, int page) {
        super(plugin, player, SIZE, ChatColor.GOLD + "兑换码管理");
        this.service = service;
        this.config = config;
        this.page = Math.max(0, page);
        refresh();
    }

    public void refresh() {
        List<RedeemCode> all = new ArrayList<>(service.getAllCodes());
        cachedPages = Math.max(1, (all.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        page = Math.min(page, cachedPages - 1);
        cachedPage = new ArrayList<>();
        getInventory().clear();

        for (int slot = 0; slot < ITEMS_PER_PAGE; slot++) {
            int index = page * ITEMS_PER_PAGE + slot;
            if (index >= all.size()) {
                break;
            }
            RedeemCode code = all.get(index);
            cachedPage.add(code);
            getInventory().setItem(slot, icon(code));
        }

        ItemStack pane = pane();
        for (int slot = 46; slot <= 48; slot++) {
            getInventory().setItem(slot, pane);
        }

        ItemStack prev = new ItemStack(page > 0 ? Material.SPECTRAL_ARROW : Material.ARROW);
        ItemMeta prevMeta = prev.getItemMeta();
        if (prevMeta != null) {
            prevMeta.setDisplayName(ChatColor.YELLOW + "上一页 (第 " + (page + 1) + "/" + cachedPages + " 页)");
            prev.setItemMeta(prevMeta);
        }
        getInventory().setItem(SLOT_PREV, prev);

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(ChatColor.AQUA + "兑换码统计");
            infoMeta.setLore(List.of(
                    ChatColor.WHITE + "总数: " + all.size(),
                    ChatColor.GRAY + "左键: 查看详情",
                    ChatColor.RED + "Shift+左键: 删除兑换码"));
            info.setItemMeta(infoMeta);
        }
        getInventory().setItem(SLOT_INFO, info);

        ItemStack genGeo = new ItemStack(Material.GOLD_INGOT);
        ItemMeta geoMeta = genGeo.getItemMeta();
        if (geoMeta != null) {
            geoMeta.setDisplayName(ChatColor.GOLD + "生成 Geo 兑换码");
            geoMeta.setLore(List.of(
                    ChatColor.YELLOW + "随机 8 位码，默认奖励 100 Geo",
                    ChatColor.GREEN + "左键: 生成 100 Geo",
                    ChatColor.GREEN + "Shift+左键: 生成 500 Geo"));
            genGeo.setItemMeta(geoMeta);
        }
        getInventory().setItem(SLOT_GEN_GEO, genGeo);

        ItemStack genItem = new ItemStack(Material.CHEST);
        ItemMeta itemMeta = genItem.getItemMeta();
        if (itemMeta != null) {
            itemMeta.setDisplayName(ChatColor.AQUA + "生成物品兑换码");
            itemMeta.setLore(List.of(
                    ChatColor.YELLOW + "手持物品，生成随机 8 位码",
                    ChatColor.GRAY + "保留完整物品数据",
                    ChatColor.GREEN + "左键: 生成"));
            genItem.setItemMeta(itemMeta);
        }
        getInventory().setItem(SLOT_GEN_ITEM, genItem);

        ItemStack genCmd = new ItemStack(Material.COMMAND_BLOCK);
        ItemMeta cmdMeta = genCmd.getItemMeta();
        if (cmdMeta != null) {
            cmdMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "生成指令兑换码");
            cmdMeta.setLore(List.of(
                    ChatColor.YELLOW + "示例: /give @s minecraft:diamond 1",
                    ChatColor.GREEN + "左键: 生成示例指令码"));
            genCmd.setItemMeta(cmdMeta);
        }
        getInventory().setItem(SLOT_GEN_CMD, genCmd);

        ItemStack next = new ItemStack(page + 1 < cachedPages ? Material.SPECTRAL_ARROW : Material.ARROW);
        ItemMeta nextMeta = next.getItemMeta();
        if (nextMeta != null) {
            nextMeta.setDisplayName(ChatColor.YELLOW + "下一页 (第 " + (page + 1) + "/" + cachedPages + " 页)");
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
        ClickType click = event.getClick();

        if (slot == SLOT_PREV) {
            if (page > 0) {
                reopen(page - 1);
            }
            return;
        }
        if (slot == SLOT_NEXT) {
            if (page + 1 < cachedPages) {
                reopen(page + 1);
            }
            return;
        }
        if (slot == SLOT_INFO) {
            refresh();
            return;
        }
        if (slot == SLOT_GEN_GEO) {
            Money amount = click.isShiftClick() ? Money.of(500.0) : Money.of(100.0);
            generate("geo", amount, null);
            return;
        }
        if (slot == SLOT_GEN_ITEM) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR) {
                player.sendMessage(ChatColor.RED + "[GeoTowny] 请手持要作为奖励的物品。");
                return;
            }
            String code = service.generateRandomCode();
            if (code == null || service.createItem(code, hand, 1) == null) {
                player.sendMessage(ChatColor.RED + "[GeoTowny] 生成兑换码失败。");
                return;
            }
            player.sendMessage(ChatColor.GREEN + "[GeoTowny] 已生成物品兑换码: " + code);
            reopen(0);
            return;
        }
        if (slot == SLOT_GEN_CMD) {
            String code = service.generateRandomCode();
            if (code == null || service.createCommand(code, "give @s minecraft:diamond 1", 1) == null) {
                player.sendMessage(ChatColor.RED + "[GeoTowny] 生成兑换码失败。");
                return;
            }
            player.sendMessage(ChatColor.GREEN + "[GeoTowny] 已生成指令兑换码: " + code);
            reopen(0);
            return;
        }
        if (slot >= ITEMS_PER_PAGE || slot >= cachedPage.size()) {
            return;
        }

        RedeemCode code = cachedPage.get(slot);
        if (click.isShiftClick()) {
            service.removeCode(code.getCode());
            player.sendMessage(ChatColor.GREEN + "[GeoTowny] 已删除兑换码: " + code.getCode());
            reopen(page);
            return;
        }

        player.sendMessage(ChatColor.GOLD + "======== 兑换码详情 ========");
        player.sendMessage(ChatColor.YELLOW + "码: " + ChatColor.WHITE + code.getCode());
        player.sendMessage(ChatColor.YELLOW + "类型: " + ChatColor.WHITE + code.getType());
        if (code.getType() == RedeemCode.RedeemType.GEO) {
            player.sendMessage(ChatColor.YELLOW + "奖励: " + ChatColor.WHITE
                    + code.getValue().format() + " " + config.currencyName());
        } else if (code.getType() == RedeemCode.RedeemType.ITEM) {
            player.sendMessage(ChatColor.YELLOW + "物品数: " + ChatColor.WHITE + code.getItemData().size());
        } else {
            player.sendMessage(ChatColor.YELLOW + "指令: " + ChatColor.WHITE + code.getCommand());
        }
        player.sendMessage(ChatColor.YELLOW + "使用次数: " + ChatColor.WHITE
                + code.getUsedCount() + "/" + (code.getMaxUses() <= 0 ? "∞" : code.getMaxUses()));
        player.sendMessage(ChatColor.YELLOW + "状态: " + (code.isExpired() ? ChatColor.RED + "已过期"
                : code.hasUsesRemaining() ? ChatColor.GREEN + "可用" : ChatColor.YELLOW + "已用完"));
        refresh();
    }

    private void generate(String kind, Money amount, String command) {
        String code = service.generateRandomCode();
        if (code == null) {
            player.sendMessage(ChatColor.RED + "[GeoTowny] 生成兑换码失败。");
            return;
        }
        boolean created = switch (kind) {
            case "geo" -> service.createGeo(code, amount, 1) != null;
            case "command" -> service.createCommand(code, command, 1) != null;
            default -> false;
        };
        if (!created) {
            player.sendMessage(ChatColor.RED + "[GeoTowny] 生成兑换码失败。");
            return;
        }
        player.sendMessage(ChatColor.GREEN + "[GeoTowny] 已生成兑换码: " + code);
        reopen(0);
    }

    private void reopen(int newPage) {
        player.closeInventory();
        plugin.getServer().getScheduler().runTask(plugin,
                () -> new RedeemGui(plugin, player, service, config, newPage).open());
    }

    private ItemStack icon(RedeemCode code) {
        ItemStack stack;
        switch (code.getType()) {
            case GEO -> {
                stack = new ItemStack(Material.GOLD_NUGGET);
                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.GOLD + code.getCode());
                    meta.setLore(List.of(
                            ChatColor.YELLOW + "类型: Geo",
                            ChatColor.GREEN + "奖励: " + code.getValue().format() + " " + config.currencyName(),
                            ChatColor.AQUA + "使用: " + code.getUsedCount() + "/"
                                    + (code.getMaxUses() <= 0 ? "∞" : code.getMaxUses()),
                            ChatColor.RED + "Shift+左键: 删除"));
                    stack.setItemMeta(meta);
                }
            }
            case ITEM -> {
                stack = new ItemStack(Material.CHEST);
                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.AQUA + code.getCode());
                    meta.setLore(List.of(
                            ChatColor.YELLOW + "类型: 物品",
                            ChatColor.GREEN + "物品数: " + code.getItemData().size(),
                            ChatColor.AQUA + "使用: " + code.getUsedCount() + "/"
                                    + (code.getMaxUses() <= 0 ? "∞" : code.getMaxUses()),
                            ChatColor.RED + "Shift+左键: 删除"));
                    stack.setItemMeta(meta);
                }
            }
            case COMMAND -> {
                stack = new ItemStack(Material.COMMAND_BLOCK);
                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.LIGHT_PURPLE + code.getCode());
                    meta.setLore(List.of(
                            ChatColor.YELLOW + "类型: 指令",
                            ChatColor.GREEN + "指令: " + code.getCommand(),
                            ChatColor.AQUA + "使用: " + code.getUsedCount() + "/"
                                    + (code.getMaxUses() <= 0 ? "∞" : code.getMaxUses()),
                            ChatColor.RED + "Shift+左键: 删除"));
                    stack.setItemMeta(meta);
                }
            }
            default -> stack = new ItemStack(Material.BARRIER);
        }
        return stack;
    }

    private static ItemStack pane() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        return pane;
    }
}