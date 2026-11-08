package com.geotowny.service;

import com.geotowny.Log;
import com.geotowny.config.PluginConfig;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.market.FreeMarketSnapshot;
import com.geotowny.domain.market.Listing;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.money.Money;
import com.geotowny.storage.FreeMarketStore;
import com.geotowny.util.ItemCodec;
import com.geotowny.util.ItemDelivery;
import com.geotowny.util.Result;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public final class FreeMarketService {

    private final GeoTownyCore core;
    private final PluginConfig config;
    private final FreeMarketStore store;
    private final ReentrantLock lock = new ReentrantLock();

    private FreeMarketSnapshot snapshot = new FreeMarketSnapshot();

    public FreeMarketService(GeoTownyCore core, PluginConfig config, FreeMarketStore store) {
        this.core = core;
        this.config = config;
        this.store = store;
    }

    public void load() {
        lock.lock();
        try {
            FreeMarketSnapshot loaded = store.load();
            snapshot = loaded == null ? new FreeMarketSnapshot() : loaded;
            List<Listing> valid = new ArrayList<>();
            int discarded = 0;
            long maxId = 0L;
            for (Listing listing : snapshot.getListings()) {
                if (listing == null || listing.getSellerUuid() == null || listing.getQuantity() <= 0) {
                    discarded++;
                    continue;
                }
                if (listing.getItemData() == null || listing.getItemData().isBlank()) {
                    discarded++;
                    continue;
                }
                valid.add(listing);
                maxId = Math.max(maxId, listing.getId());
            }
            if (discarded > 0) {
                Log.warn("自由市场加载时安全丢弃 {} 个旧格式/损坏挂单（旧 NBT 无法迁移）。", discarded);
            }
            snapshot.setListings(valid);
            snapshot.setNextId(Math.max(snapshot.getNextId(), maxId + 1L));
            cleanupExpired(false);
            save();
            Log.info("自由市场已加载：活跃挂单 {} 项。", snapshot.getListings().size());
        } finally {
            lock.unlock();
        }
    }

    public List<Listing> allListings() {
        return Collections.unmodifiableList(snapshot.getListings());
    }

    public List<Listing> listingsBySeller(String sellerUuid) {
        List<Listing> result = new ArrayList<>();
        if (sellerUuid == null) {
            return result;
        }
        for (Listing listing : snapshot.getListings()) {
            if (sellerUuid.equals(listing.getSellerUuid())) {
                result.add(listing);
            }
        }
        return result;
    }

    public int countBySeller(String sellerUuid) {
        int count = 0;
        for (Listing listing : snapshot.getListings()) {
            if (sellerUuid != null && sellerUuid.equals(listing.getSellerUuid())) {
                count++;
            }
        }
        return count;
    }

    public Listing find(long id) {
        for (Listing listing : snapshot.getListings()) {
            if (listing.getId() == id) {
                return listing;
            }
        }
        return null;
    }

    public Result list(Player player, String itemId, int quantity, Money unitPrice) {
        lock.lock();
        try {
            if (!config.freeMarketEnabled()) {
                return Result.fail("自由市场已禁用。");
            }
            if (quantity <= 0) {
                return Result.fail("数量必须大于 0。");
            }
            if (unitPrice == null || !unitPrice.isPositive()) {
                return Result.fail("单价必须大于 0。");
            }
            Money minPrice = Money.of(config.freeMarketMinPrice());
            Money maxPrice = Money.of(config.freeMarketMaxPrice());
            if (unitPrice.compareTo(minPrice) < 0) {
                return Result.fail("单价不能低于 " + minPrice.format() + "。");
            }
            if (unitPrice.compareTo(maxPrice) > 0) {
                return Result.fail("单价不能高于 " + maxPrice.format() + "。");
            }
            Material material = MarketService.resolveMaterial(itemId);
            if (material == null || material == Material.AIR) {
                return Result.fail("物品未注册：" + itemId);
            }
            String sellerUuid = player.getUniqueId().toString();
            int maxListings = config.freeMarketMaxListingsPerPlayer();
            if (maxListings > 0 && countBySeller(sellerUuid) >= maxListings) {
                return Result.fail("已达到挂单上限 " + maxListings + " 个，请先取消部分挂单。");
            }

            ItemStack reference = findReference(player.getInventory(), material);
            if (reference == null) {
                return Result.fail("背包中没有该物品。");
            }
            int matching = countMatching(player.getInventory(), reference);
            if (matching < quantity) {
                return Result.fail("背包中与目标物品 NBT 匹配的数量不足（" + matching + " < " + quantity + "）。");
            }
            String itemData = ItemCodec.encode(ItemCodec.single(reference));
            if (itemData == null) {
                return Result.fail("物品序列化失败，无法上架。");
            }
            removeMatching(player.getInventory(), reference, quantity);

            long id = snapshot.getNextId();
            snapshot.setNextId(id + 1L);
            Listing listing = new Listing(id, sellerUuid, player.getName(), itemId,
                    reference.hasItemMeta() && reference.getItemMeta() != null
                            && reference.getItemMeta().hasDisplayName()
                            ? reference.getItemMeta().getDisplayName() : material.name(),
                    itemData, quantity, unitPrice, System.currentTimeMillis());
            snapshot.getListings().add(listing);
            save();
            return Result.ok("已上架 " + quantity + " × " + listing.getDisplayName()
                    + "，单价 " + unitPrice.format() + " " + config.currencyName()
                    + "（挂单 #" + id + "）。");
        } finally {
            lock.unlock();
        }
    }

    public Result buy(Player buyer, Resident buyerResident, long listingId, int quantity) {
        lock.lock();
        try {
            if (!config.freeMarketEnabled()) {
                return Result.fail("自由市场已禁用。");
            }
            if (quantity <= 0) {
                return Result.fail("数量必须大于 0。");
            }
            Listing listing = find(listingId);
            if (listing == null) {
                return Result.fail("挂单不存在或已下架。");
            }
            if (listing.getQuantity() < quantity) {
                return Result.fail("该挂单剩余 " + listing.getQuantity() + " 件，不足以购买 " + quantity + " 件。");
            }
            String buyerUuid = buyer.getUniqueId().toString();
            if (buyerUuid.equals(listing.getSellerUuid())) {
                return Result.fail("不能购买自己的挂单。请使用 /fm cancel 取消挂单。");
            }
            ItemStack template = ItemCodec.decode(listing.getItemData());
            if (template == null) {
                Material material = MarketService.resolveMaterial(listing.getItemId());
                if (material == null || material == Material.AIR) {
                    return Result.fail("挂单物品已无法解析，交易已取消。");
                }
                template = new ItemStack(material);
            }
            Money total = Money.ofCents(Math.multiplyExact(listing.getUnitPrice().cents(), (long) quantity));
            Money tax = total.times(config.freeMarketTax());
            Money sellerReceive = total.minus(tax);

            if (!buyerResident.withdraw(total)) {
                return Result.fail("余额不足，需要 " + total.format() + " " + config.currencyName() + "。");
            }
            Resident seller;
            try {
                seller = core.getResident(UUID.fromString(listing.getSellerUuid()));
            } catch (IllegalArgumentException e) {
                seller = null;
            }
            if (seller == null) {
                buyerResident.deposit(total);
                core.saveResident(buyerResident);
                return Result.fail("卖家数据不存在，交易已取消。");
            }
            seller.deposit(sellerReceive);
            core.saveResident(buyerResident);
            core.saveResident(seller);
            ItemDelivery.give(buyer, template, quantity);

            listing.setQuantity(listing.getQuantity() - quantity);
            if (listing.getQuantity() <= 0) {
                snapshot.getListings().remove(listing);
            }
            save();

            Player sellerOnline = Bukkit.getPlayer(UUID.fromString(listing.getSellerUuid()));
            if (sellerOnline != null) {
                sellerOnline.sendMessage(org.bukkit.ChatColor.GOLD + "[GeoTowny] " + buyer.getName()
                        + " 购买了你的挂单 #" + listing.getId() + "：" + quantity + " × "
                        + listing.getDisplayName() + "，你收到 " + sellerReceive.format() + " "
                        + config.currencyName() + "。");
            }
            return Result.ok("已从 " + listing.getSellerName() + " 购买 " + quantity + " × "
                    + listing.getDisplayName() + "，共付 " + total.format() + " " + config.currencyName() + "。");
        } catch (ArithmeticException e) {
            return Result.fail("交易金额过大，已取消。");
        } finally {
            lock.unlock();
        }
    }

    public Result cancel(Player seller, long listingId) {
        lock.lock();
        try {
            if (!config.freeMarketEnabled()) {
                return Result.fail("自由市场已禁用。");
            }
            Listing listing = find(listingId);
            if (listing == null) {
                return Result.fail("挂单不存在。");
            }
            if (!listing.getSellerUuid().equals(seller.getUniqueId().toString())) {
                return Result.fail("只能取消自己的挂单。");
            }
            ItemStack template = ItemCodec.decode(listing.getItemData());
            if (template == null) {
                Material material = MarketService.resolveMaterial(listing.getItemId());
                if (material == null || material == Material.AIR) {
                    return Result.fail("挂单物品已无法解析，无法退还。");
                }
                template = new ItemStack(material);
            }
            ItemDelivery.give(seller, template, listing.getQuantity());
            snapshot.getListings().remove(listing);
            save();
            return Result.ok("已取消挂单 #" + listingId + "，退还 " + listing.getQuantity()
                    + " × " + listing.getDisplayName() + "。");
        } finally {
            lock.unlock();
        }
    }

    public void cleanupExpired(boolean persist) {
        int days = config.freeMarketExpireDays();
        if (days <= 0) {
            return;
        }
        long expireMs = days * 24L * 60L * 60L * 1000L;
        long now = System.currentTimeMillis();
        int removed = 0;
        Iterator<Listing> iterator = snapshot.getListings().iterator();
        while (iterator.hasNext()) {
            Listing listing = iterator.next();
            if (now - listing.getListedAt() > expireMs) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) {
            Log.warn("自由市场清理了 {} 个过期挂单（物品未退还）。", removed);
            if (persist) {
                save();
            }
        }
    }

    public void save() {
        store.save(snapshot);
    }

    private static ItemStack findReference(Inventory inventory, Material material) {
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack != null && stack.getType() == material) {
                return stack.clone();
            }
        }
        return null;
    }

    private static int countMatching(Inventory inventory, ItemStack reference) {
        int count = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack != null && stack.isSimilar(reference)) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    private static void removeMatching(Inventory inventory, ItemStack reference, int quantity) {
        int remaining = quantity;
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || !stack.isSimilar(reference)) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            int left = stack.getAmount() - take;
            if (left <= 0) {
                inventory.setItem(slot, null);
            } else {
                stack.setAmount(left);
                inventory.setItem(slot, stack);
            }
            remaining -= take;
        }
    }
}