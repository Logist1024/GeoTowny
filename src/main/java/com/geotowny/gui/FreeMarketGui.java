package com.geotowny.gui;

import com.geotowny.config.PluginConfig;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.market.Listing;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.money.Money;
import com.geotowny.service.FreeMarketService;
import com.geotowny.service.MarketService;
import com.geotowny.util.ItemCodec;
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

public final class FreeMarketGui extends AbstractGui {

    public static final int SIZE = 54;
    public static final int ITEMS_PER_PAGE = 45;
    public static final int SLOT_PREV = 45;
    public static final int SLOT_INFO = 49;
    public static final int SLOT_MY_LISTINGS = 50;
    public static final int SLOT_NEXT = 53;
    public static final int SHIFT_MULTIPLIER = 16;

    private final FreeMarketService market;
    private final GeoTownyCore core;
    private final PluginConfig config;
    private final int viewMode;
    private int page;

    public FreeMarketGui(Plugin plugin, Player player, FreeMarketService market, GeoTownyCore core,
                         PluginConfig config, int page, int viewMode) {
        super(plugin, player, SIZE, ChatColor.LIGHT_PURPLE + "自由市场");
        this.market = market;
        this.core = core;
        this.config = config;
        this.page = Math.max(0, page);
        this.viewMode = viewMode == 1 ? 1 : 0;
        refresh();
    }

    public void refresh() {
        List<Listing> listings = currentListings();
        int pages = totalPages(listings.size());
        this.page = Math.min(page, pages - 1);
        getInventory().clear();

        Resident resident = core.getResident(player.getUniqueId());
        Money balance = resident == null ? Money.ZERO : resident.getBalance();
        int myCount = market.countBySeller(player.getUniqueId().toString());

        for (int slot = 0; slot < ITEMS_PER_PAGE; slot++) {
            int index = page * ITEMS_PER_PAGE + slot;
            if (index >= listings.size()) {
                break;
            }
            getInventory().setItem(slot, icon(listings.get(index)));
        }

        ItemStack pane = pane();
        for (int slot = 46; slot <= 48; slot++) {
            getInventory().setItem(slot, pane);
        }
        for (int slot = 51; slot <= 52; slot++) {
            getInventory().setItem(slot, pane);
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
            infoMeta.setDisplayName(ChatColor.GREEN + "自由市场");
            infoMeta.setLore(List.of(
                    ChatColor.GOLD + "余额: " + balance.format() + " " + config.currencyName(),
                    ChatColor.AQUA + "我的挂单: " + myCount + " / " + config.freeMarketMaxListingsPerPlayer(),
                    ChatColor.AQUA + "全服挂单: " + market.allListings().size(),
                    ChatColor.GRAY + "上架命令: /fm sell <物品> <数量> <单价>"));
            info.setItemMeta(infoMeta);
        }
        getInventory().setItem(SLOT_INFO, info);

        ItemStack my = new ItemStack(viewMode == 1 ? Material.WRITTEN_BOOK : Material.BOOK);
        ItemMeta myMeta = my.getItemMeta();
        if (myMeta != null) {
            myMeta.setDisplayName(ChatColor.LIGHT_PURPLE + (viewMode == 1 ? "返回全部挂单" : "我的挂单"));
            myMeta.setLore(List.of(ChatColor.GRAY
                    + (viewMode == 1 ? "点击查看全部挂单" : "点击查看自己的挂单")));
            my.setItemMeta(myMeta);
        }
        getInventory().setItem(SLOT_MY_LISTINGS, my);

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
                reopen(page - 1, viewMode);
            }
            return;
        }
        if (slot == SLOT_NEXT) {
            if (page + 1 < totalPages(currentListings().size())) {
                reopen(page + 1, viewMode);
            }
            return;
        }
        if (slot == SLOT_INFO) {
            refresh();
            return;
        }
        if (slot == SLOT_MY_LISTINGS) {
            reopen(0, viewMode == 1 ? 0 : 1);
            return;
        }
        if (slot >= ITEMS_PER_PAGE) {
            return;
        }
        if (click != ClickType.LEFT && click != ClickType.RIGHT
                && click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) {
            return;
        }

        List<Listing> listings = currentListings();
        int index = page * ITEMS_PER_PAGE + slot;
        if (index >= listings.size()) {
            return;
        }
        Listing listing = listings.get(index);
        boolean mine = listing.getSellerUuid().equals(player.getUniqueId().toString());
        Result result;
        if (mine && click.isShiftClick()) {
            result = market.cancel(player, listing.getId());
        } else {
            int quantity = click.isShiftClick()
                    ? Math.min(SHIFT_MULTIPLIER, listing.getQuantity()) : 1;
            Resident resident = core.getOrCreateResident(player.getUniqueId(), player.getName());
            result = market.buy(player, resident, listing.getId(), quantity);
        }
        player.sendMessage((result.isSuccess() ? ChatColor.GREEN : ChatColor.RED)
                + "[GeoTowny] " + result.getMessage());
        refresh();
    }

    private List<Listing> currentListings() {
        return viewMode == 1
                ? market.listingsBySeller(player.getUniqueId().toString())
                : market.allListings();
    }

    private void reopen(int newPage, int newViewMode) {
        player.closeInventory();
        plugin.getServer().getScheduler().runTask(plugin,
                () -> new FreeMarketGui(plugin, player, market, core, config, newPage, newViewMode).open());
    }

    private static int totalPages(int count) {
        return Math.max(1, (count + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
    }

    private ItemStack icon(Listing listing) {
        ItemStack stack = ItemCodec.decode(listing.getItemData());
        if (stack == null) {
            Material material = MarketService.resolveMaterial(listing.getItemId());
            stack = material == null ? new ItemStack(Material.BARRIER) : new ItemStack(material);
        }
        stack.setAmount(Math.max(1, Math.min(64, listing.getQuantity())));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        if (!meta.hasDisplayName()) {
            meta.setDisplayName(ChatColor.AQUA + listing.getDisplayName());
        }
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.AQUA + "卖家: " + listing.getSellerName());
        lore.add(ChatColor.YELLOW + "单价: " + listing.getUnitPrice().format()
                + " " + config.currencyName());
        lore.add(ChatColor.WHITE + "剩余: " + listing.getQuantity() + " 件");
        Money shiftTotal = Money.ofCents(Math.multiplyExact(
                listing.getUnitPrice().cents(), (long) Math.min(SHIFT_MULTIPLIER, listing.getQuantity())));
        lore.add(ChatColor.GOLD + "Shift 一组(16): " + shiftTotal.format()
                + " " + config.currencyName());
        if (config.freeMarketTax() > 0) {
            lore.add(ChatColor.DARK_GRAY + String.format("税率: %.0f%%", config.freeMarketTax() * 100));
        }
        if (listing.getSellerUuid().equals(player.getUniqueId().toString())) {
            lore.add(ChatColor.GREEN + "——— 这是你的挂单 ———");
            lore.add(ChatColor.RED + "Shift+左键取消挂单并退回物品");
        } else {
            lore.add(ChatColor.DARK_GRAY + "——— 操作 ———");
            lore.add(ChatColor.GRAY + "左键购买 1 个 | Shift+左键购买 16 个");
        }
        lore.add(ChatColor.DARK_GRAY + "挂单 #" + listing.getId());
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