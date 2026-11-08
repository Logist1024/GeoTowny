package com.geotowny.service;

import com.geotowny.Log;
import com.geotowny.config.PluginConfig;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.market.MarketItem;
import com.geotowny.domain.market.MarketSnapshot;
import com.geotowny.domain.market.ShopCatalog;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.money.Money;
import com.geotowny.storage.MarketStore;
import com.geotowny.util.Result;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

public final class MarketService {

    private static final Money MIN_PRICE = Money.ofCents(1);

    private final GeoTownyCore core;
    private final PluginConfig config;
    private final MarketStore store;
    private final FestivalService festival;
    private final Map<String, MarketItem> itemIndex = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Random random = new Random();

    private MarketSnapshot snapshot = new MarketSnapshot();

    public MarketService(GeoTownyCore core, PluginConfig config, MarketStore store) {
        this(core, config, store, null);
    }

    public MarketService(GeoTownyCore core, PluginConfig config, MarketStore store, FestivalService festival) {
        this.core = core;
        this.config = config;
        this.store = store;
        this.festival = festival;
    }

    public void load() {
        lock.lock();
        try {
            MarketSnapshot loaded = store.load();
            if (loaded == null) {
                snapshot = new MarketSnapshot();
                snapshot.setItems(ShopCatalog.defaults());
                rollDailyMultipliers();
                snapshot.setLastResetDay(0L);
            } else {
                snapshot = loaded;
                mergeCatalog();
            }
            rebuildIndex();
            validateConfig();
            save();
            Log.info("市场已加载：商品 {} 项，上次重置日 {}。", items().size(), snapshot.getLastResetDay());
        } finally {
            lock.unlock();
        }
    }

    public void initializeDay(long today) {
        lock.lock();
        try {
            if (snapshot.getLastResetDay() <= 0L) {
                snapshot.setLastResetDay(today);
                save();
                return;
            }
            resetDaily(today);
        } finally {
            lock.unlock();
        }
    }

    public boolean resetDaily(long today) {
        lock.lock();
        try {
            if (today <= snapshot.getLastResetDay()) {
                return false;
            }
            double decay = config.shopVolumeDecay();
            for (MarketItem item : snapshot.getItems()) {
                item.setTotalBought((long) (item.getTotalBought() * decay));
                item.setTotalSold((long) (item.getTotalSold() * decay));
                item.setDailyBought(0);
                item.setDailySold(0);
                item.setDailyRandomMultiplier(rollDailyMultiplier());
                item.clearPriceHistory();
                item.recordPriceSnapshot(currentBuyPrice(item), currentSellPrice(item));
            }
            snapshot.getServer().setDailyBought(0);
            snapshot.getServer().setDailySold(0);
            for (MarketSnapshot.PlayerQuota quota : snapshot.getPlayers().values()) {
                quota.setDailyBought(0);
                quota.setDailySold(0);
            }
            snapshot.getPlayers().entrySet().removeIf(entry -> {
                MarketSnapshot.PlayerQuota quota = entry.getValue();
                return quota.getDailyBought() == 0 && quota.getDailySold() == 0;
            });
            snapshot.setLastResetDay(today);
            save();
            Log.info("市场每日重置完成：累计买卖量衰减 {}，每日随机倍率已刷新。", decay);
            return true;
        } finally {
            lock.unlock();
        }
    }

    public List<MarketItem> items() {
        return Collections.unmodifiableList(snapshot.getItems());
    }

    public MarketItem find(String itemId) {
        return itemId == null ? null : itemIndex.get(itemId);
    }

    public Money currentBuyPrice(MarketItem item) {
        double factor = 1.0d + config.shopPriceElasticity() * item.getNetBought();
        factor = Math.max(config.shopPriceFloorMultiplier(), Math.min(config.shopPriceCeilingMultiplier(), factor));
        double multiplier = factor * item.getDailyRandomMultiplier() * item.getPriceAdjust();
        if (festival != null) {
            multiplier *= festival.getShopPriceMultiplier();
        }
        Money price = item.getBasePrice().times(multiplier);
        return price.cents() < MIN_PRICE.cents() ? MIN_PRICE : price;
    }

    public Money currentSellPrice(MarketItem item) {
        Money price = currentBuyPrice(item).times(config.shopSellBuyRatio());
        return price.cents() < MIN_PRICE.cents() ? MIN_PRICE : price;
    }

    public int playerRemainingBuy(String uuid) {
        if (uuid == null) {
            return 0;
        }
        MarketSnapshot.PlayerQuota quota = playerQuota(uuid);
        return Math.max(0, config.shopPlayerDailyBuyLimit() - (int) quota.getDailyBought());
    }

    public int playerRemainingSell(String uuid) {
        if (uuid == null) {
            return 0;
        }
        MarketSnapshot.PlayerQuota quota = playerQuota(uuid);
        return Math.max(0, config.shopPlayerDailySellLimit() - (int) quota.getDailySold());
    }

    public int serverRemainingBuy() {
        return Math.max(0, config.shopServerDailyBuyLimit() - (int) snapshot.getServer().getDailyBought());
    }

    public int serverRemainingSell() {
        return Math.max(0, config.shopServerDailySellLimit() - (int) snapshot.getServer().getDailySold());
    }

    public Result buy(Player player, Resident resident, String itemId, int quantity) {
        lock.lock();
        try {
            if (!config.shopEnabled()) {
                return Result.fail("商店已禁用。");
            }
            if (quantity <= 0) {
                return Result.fail("数量必须大于 0。");
            }
            MarketItem item = find(itemId);
            if (item == null) {
                return Result.fail("商品不存在：" + itemId);
            }
            Material material = resolveMaterial(itemId);
            if (material == null || material == Material.AIR) {
                return Result.fail("物品未注册：" + itemId);
            }
            int playerRemaining = playerRemainingBuy(player.getUniqueId().toString());
            int serverRemaining = serverRemainingBuy();
            if (quantity > playerRemaining) {
                return Result.fail("今日个人购买额度不足：剩余 " + playerRemaining + " 件。");
            }
            if (quantity > serverRemaining) {
                return Result.fail("今日全服购买额度不足：剩余 " + serverRemaining + " 件。");
            }
            Money unitPrice = currentBuyPrice(item);
            Money total = Money.ofCents(Math.multiplyExact(unitPrice.cents(), (long) quantity));
            if (!resident.withdraw(total)) {
                return Result.fail("余额不足，需要 " + total.format() + " " + config.currencyName() + "。");
            }
            deliver(player, material, quantity);

            item.setTotalBought(item.getTotalBought() + quantity);
            item.setDailyBought(item.getDailyBought() + quantity);
            MarketSnapshot.PlayerQuota quota = playerQuota(player.getUniqueId().toString());
            quota.setDailyBought(quota.getDailyBought() + quantity);
            snapshot.getServer().setDailyBought(snapshot.getServer().getDailyBought() + quantity);
            item.recordPriceSnapshot(currentBuyPrice(item), currentSellPrice(item));

            core.saveResident(resident);
            save();
            return Result.ok("已购入 " + quantity + " × " + item.getDisplayName()
                    + "，单价 " + unitPrice.format() + "，共扣 " + total.format() + " " + config.currencyName() + "。");
        } catch (ArithmeticException e) {
            return Result.fail("交易金额过大，已取消。");
        } finally {
            lock.unlock();
        }
    }

    public Result sell(Player player, Resident resident, String itemId, int quantity) {
        lock.lock();
        try {
            if (!config.shopEnabled()) {
                return Result.fail("商店已禁用。");
            }
            if (quantity <= 0) {
                return Result.fail("数量必须大于 0。");
            }
            MarketItem item = find(itemId);
            if (item == null) {
                return Result.fail("商品不存在：" + itemId);
            }
            Material material = resolveMaterial(itemId);
            if (material == null || material == Material.AIR) {
                return Result.fail("物品未注册：" + itemId);
            }
            int playerRemaining = playerRemainingSell(player.getUniqueId().toString());
            int serverRemaining = serverRemainingSell();
            if (quantity > playerRemaining) {
                return Result.fail("今日个人出售额度不足：剩余 " + playerRemaining + " 件。");
            }
            if (quantity > serverRemaining) {
                return Result.fail("今日全服出售额度不足：剩余 " + serverRemaining + " 件。");
            }
            int held = countMaterial(player.getInventory(), material);
            if (held < quantity) {
                return Result.fail("背包中只有 " + held + " 件该物品，不足以出售 " + quantity + " 件。");
            }

            item.setTotalSold(item.getTotalSold() + quantity);
            item.setDailySold(item.getDailySold() + quantity);
            MarketSnapshot.PlayerQuota quota = playerQuota(player.getUniqueId().toString());
            quota.setDailySold(quota.getDailySold() + quantity);
            snapshot.getServer().setDailySold(snapshot.getServer().getDailySold() + quantity);

            Money unitPrice = currentSellPrice(item);
            Money total = Money.ofCents(Math.multiplyExact(unitPrice.cents(), (long) quantity));
            removeMaterial(player.getInventory(), material, quantity);

            resident.deposit(total);
            item.recordPriceSnapshot(currentBuyPrice(item), currentSellPrice(item));

            core.saveResident(resident);
            save();
            return Result.ok("已售出 " + quantity + " × " + item.getDisplayName()
                    + "，单价 " + unitPrice.format() + "，共得 " + total.format() + " " + config.currencyName() + "。");
        } catch (ArithmeticException e) {
            return Result.fail("交易金额过大，已取消。");
        } finally {
            lock.unlock();
        }
    }

    public Result adjustPrice(String itemId, double multiplier) {
        lock.lock();
        try {
            MarketItem item = find(itemId);
            if (item == null) {
                return Result.fail("商品不存在：" + itemId);
            }
            if (!Double.isFinite(multiplier) || multiplier < 0.01d || multiplier > 10.0d) {
                return Result.fail("价格倍率必须在 0.01 ~ 10.0 之间。");
            }
            item.setPriceAdjust(multiplier);
            save();
            return Result.ok("已将 " + item.getDisplayName() + " 的价格倍率设为 " + multiplier + "。");
        } finally {
            lock.unlock();
        }
    }

    public Result resetPrice(String itemId) {
        return adjustPrice(itemId, 1.0d);
    }

    public Result resetVolume(String itemId) {
        lock.lock();
        try {
            MarketItem item = find(itemId);
            if (item == null) {
                return Result.fail("商品不存在：" + itemId);
            }
            item.setTotalBought(0);
            item.setTotalSold(0);
            item.setDailyBought(0);
            item.setDailySold(0);
            item.clearPriceHistory();
            save();
            return Result.ok("已重置 " + item.getDisplayName() + " 的累计买卖量。");
        } finally {
            lock.unlock();
        }
    }

    public void save() {
        store.save(snapshot);
    }

    public long lastResetDay() {
        return snapshot.getLastResetDay();
    }

    public static Material resolveMaterial(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        Material material = Material.matchMaterial(itemId);
        if (material == null && itemId.startsWith("minecraft:")) {
            material = Material.matchMaterial(itemId.substring("minecraft:".length()));
        }
        return material;
    }

    private void mergeCatalog() {
        Map<String, MarketItem> loaded = new HashMap<>();
        for (MarketItem item : snapshot.getItems()) {
            if (item.getItemId() != null) {
                loaded.put(item.getItemId(), item);
            }
        }
        List<MarketItem> merged = new ArrayList<>();
        for (MarketItem fresh : ShopCatalog.defaults()) {
            MarketItem old = loaded.get(fresh.getItemId());
            if (old != null) {
                fresh.setTotalBought(old.getTotalBought());
                fresh.setTotalSold(old.getTotalSold());
                fresh.setDailyBought(old.getDailyBought());
                fresh.setDailySold(old.getDailySold());
                fresh.setPriceAdjust(old.getPriceAdjust());
                fresh.setDailyRandomMultiplier(old.getDailyRandomMultiplier());
                fresh.setPriceHistory(old.getPriceHistory());
            }
            merged.add(fresh);
        }
        snapshot.setItems(merged);
    }

    private void rebuildIndex() {
        itemIndex.clear();
        for (MarketItem item : snapshot.getItems()) {
            if (item.getItemId() != null) {
                itemIndex.put(item.getItemId(), item);
            }
        }
    }

    private void rollDailyMultipliers() {
        for (MarketItem item : snapshot.getItems()) {
            item.setDailyRandomMultiplier(rollDailyMultiplier());
        }
    }

    private double rollDailyMultiplier() {
        double range = Math.max(0.0d, Math.min(0.5d, config.shopDailyRandomRange()));
        if (range <= 0.0d) {
            return 1.0d;
        }
        double multiplier = 1.0d + (random.nextDouble() * 2.0d - 1.0d) * range;
        return Math.max(0.5d, Math.min(2.0d, multiplier));
    }

    private MarketSnapshot.PlayerQuota playerQuota(String uuid) {
        return snapshot.getPlayers().computeIfAbsent(uuid, key -> new MarketSnapshot.PlayerQuota());
    }

    private void validateConfig() {
        double elasticity = config.shopPriceElasticity();
        if (elasticity < 0) {
            Log.warn("shop.price-elasticity 为负数，价格将不随交易量变化。");
        }
        if (config.shopSellBuyRatio() >= 1.0d) {
            Log.warn("shop.sell-buy-ratio ≥ 1.0，可能存在买卖套利风险。");
        }
    }

    private static void deliver(Player player, Material material, int quantity) {
        int remaining = quantity;
        int maxStack = Math.max(1, material.getMaxStackSize());
        while (remaining > 0) {
            int amount = Math.min(maxStack, remaining);
            ItemStack stack = new ItemStack(material, amount);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            remaining -= amount;
        }
    }

    private static int countMaterial(Inventory inventory, Material material) {
        int count = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack != null && stack.getType() == material) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    private static void removeMaterial(Inventory inventory, Material material, int quantity) {
        int remaining = quantity;
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType() != material) {
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