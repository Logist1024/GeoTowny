package com.geotowny.gui;

import com.geotowny.config.PluginConfig;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.market.MarketItem;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.money.Money;
import com.geotowny.service.FreeMarketService;
import com.geotowny.service.MarketService;
import com.geotowny.util.Result;
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

public final class ShopGui extends AbstractGui {

    public static final int SIZE = 54;
    public static final int ITEMS_PER_PAGE = 45;
    public static final int SLOT_PREV = 45;
    public static final int SLOT_INFO = 49;
    public static final int SLOT_FREE_MARKET = 50;
    public static final int SLOT_NEXT = 53;
    public static final int SHIFT_MULTIPLIER = 16;

    private final MarketService market;
    private final FreeMarketService freeMarket;
    private final GeoTownyCore core;
    private final PluginConfig config;
    private int page;

    public ShopGui(Plugin plugin, Player player, MarketService market, FreeMarketService freeMarket,
                   GeoTownyCore core, PluginConfig config, int page) {
        super(plugin, player, SIZE, ChatColor.DARK_AQUA + "GeoTowny 商店");
        this.market = market;
        this.freeMarket = freeMarket;
        this.core = core;
        this.config = config;
        this.page = Math.max(0, page);
        refresh();
    }

    public void refresh() {
        List<MarketItem> items = market.items();
        int pages = totalPages(items.size());
        this.page = Math.min(page, pages - 1);
        getInventory().clear();

        Resident resident = core.getResident(player.getUniqueId());
        Money balance = resident == null ? Money.ZERO : resident.getBalance();
        String uuid = player.getUniqueId().toString();
        int playerBuy = market.playerRemainingBuy(uuid);
        int playerSell = market.playerRemainingSell(uuid);
        int serverBuy = market.serverRemainingBuy();
        int serverSell = market.serverRemainingSell();

        for (int slot = 0; slot < ITEMS_PER_PAGE; slot++) {
            int index = page * ITEMS_PER_PAGE + slot;
            if (index >= items.size()) {
                break;
            }
            getInventory().setItem(slot, icon(items.get(index), playerBuy, playerSell, serverBuy, serverSell));
        }

        ItemStack pane = pane();
        for (int slot = 46; slot <= 48; slot++) {
            getInventory().setItem(slot, pane);
        }
        for (int slot = 51; slot <= 52; slot++) {
            getInventory().setItem(slot, pane);
        }

        if (config.freeMarketEnabled()) {
            ItemStack freeMarketButton = new ItemStack(Material.EMERALD);
            ItemMeta meta = freeMarketButton.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.LIGHT_PURPLE + "自由市场");
                meta.setLore(List.of(
                        ChatColor.GRAY + "玩家间物品交易",
                        ChatColor.AQUA + "当前挂单: " + freeMarket.allListings().size(),
                        ChatColor.YELLOW + "点击打开自由市场"));
                freeMarketButton.setItemMeta(meta);
            }
            getInventory().setItem(SLOT_FREE_MARKET, freeMarketButton);
        } else {
            getInventory().setItem(SLOT_FREE_MARKET, pane);
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
            infoMeta.setDisplayName(ChatColor.GREEN + "市场信息");
            infoMeta.setLore(List.of(
                    ChatColor.GOLD + "余额: " + balance.format() + " " + config.currencyName(),
                    ChatColor.AQUA + "个人剩余 购/售: " + playerBuy + "/" + playerSell,
                    ChatColor.AQUA + "全服剩余 购/售: " + serverBuy + "/" + serverSell,
                    ChatColor.GRAY + "货币: " + config.currencyName()));
            info.setItemMeta(infoMeta);
        }
        getInventory().setItem(SLOT_INFO, info);

        boolean hasNext = page + 1 < pages;
        ItemStack next = new ItemStack(hasNext ? Material.SPECTRAL_ARROW : Material.ARROW);
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
        ClickType click = event.getClick();
        int slot = event.getSlot();

        if (slot == SLOT_PREV) {
            if (page > 0) {
                reopen(page - 1);
            }
            return;
        }
        if (slot == SLOT_NEXT) {
            if (page + 1 < totalPages(market.items().size())) {
                reopen(page + 1);
            }
            return;
        }
        if (slot == SLOT_INFO) {
            refresh();
            return;
        }
        if (slot == SLOT_FREE_MARKET) {
            if (config.freeMarketEnabled()) {
                new FreeMarketGui(plugin, player, freeMarket, core, config, 0, 0).open();
            }
            return;
        }
        if (slot >= ITEMS_PER_PAGE) {
            return;
        }
        if (click != ClickType.LEFT && click != ClickType.RIGHT
                && click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) {
            return;
        }

        int index = page * ITEMS_PER_PAGE + slot;
        List<MarketItem> items = market.items();
        if (index >= items.size()) {
            return;
        }
        MarketItem item = items.get(index);
        int quantity = config.shopTradeStep() * (click.isShiftClick() ? SHIFT_MULTIPLIER : 1);
        Resident resident = core.getOrCreateResident(player.getUniqueId(), player.getName());
        Result result = click.isRightClick()
                ? market.sell(player, resident, item.getItemId(), quantity)
                : market.buy(player, resident, item.getItemId(), quantity);
        player.sendMessage((result.isSuccess() ? ChatColor.GREEN : ChatColor.RED)
                + "[GeoTowny] " + result.getMessage());
        refresh();
    }

    private void reopen(int newPage) {
        player.closeInventory();
        plugin.getServer().getScheduler().runTask(plugin,
                () -> new ShopGui(plugin, player, market, freeMarket, core, config, newPage).open());
    }

    private static int totalPages(int itemCount) {
        return Math.max(1, (itemCount + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
    }

    private ItemStack icon(MarketItem item, int playerBuy, int playerSell, int serverBuy, int serverSell) {
        Material material = MarketService.resolveMaterial(item.getItemId());
        ItemStack stack = material == null ? new ItemStack(Material.BARRIER) : new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        if (material == null) {
            meta.setDisplayName(ChatColor.RED + item.getDisplayName() + "（未注册）");
        } else {
            meta.setDisplayName(ChatColor.AQUA + item.getDisplayName());
        }

        Money buy = market.currentBuyPrice(item);
        Money sell = market.currentSellPrice(item);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.YELLOW + "买价: " + buy.format() + " " + config.currencyName());
        lore.add(ChatColor.GOLD + "卖价: " + sell.format() + " " + config.currencyName());
        int trend = item.getPriceTrend();
        if (trend > 0) {
            lore.add(ChatColor.RED + "趋势: ↑ 上涨 " + String.format("%.1f%%", item.getPriceChangePercent()));
        } else if (trend < 0) {
            lore.add(ChatColor.GREEN + "趋势: ↓ 下跌 "
                    + String.format("%.1f%%", Math.abs(item.getPriceChangePercent())));
        } else {
            lore.add(ChatColor.GRAY + "趋势: — 持平");
        }
        if (Math.abs(item.getPriceAdjust() - 1.0d) > 0.001d) {
            lore.add((item.getPriceAdjust() > 1.0d ? ChatColor.RED : ChatColor.GREEN)
                    + String.format("价格微调: ×%.2f", item.getPriceAdjust()));
        }
        lore.add(ChatColor.DARK_AQUA + "——— 今日限量 ———");
        lore.add(ChatColor.AQUA + "个人剩余 购/售: " + playerBuy + "/" + playerSell);
        lore.add(ChatColor.AQUA + "全服剩余 购/售: " + serverBuy + "/" + serverSell);
        lore.add(ChatColor.DARK_GRAY + "本商品今日 购/售: " + item.getDailyBought() + "/" + item.getDailySold());
        lore.add(ChatColor.DARK_GRAY + "——— 操作 ———");
        lore.add(ChatColor.GRAY + "左键购买 1 个 | 右键出售 1 个");
        lore.add(ChatColor.DARK_GRAY + "Shift+左键购买一组 | Shift+右键出售一组");
        lore.add(ChatColor.DARK_GRAY + "累计买/卖: " + item.getTotalBought() + "/" + item.getTotalSold());
        meta.setLore(lore);
        stack.setItemMeta(meta);
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