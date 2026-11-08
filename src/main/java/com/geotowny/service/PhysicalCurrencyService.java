package com.geotowny.service;

import com.geotowny.GeoTownyPlugin;
import com.geotowny.Log;
import com.geotowny.config.PluginConfig;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.money.Money;
import com.geotowny.storage.PhysicalCurrencyStore;
import com.geotowny.util.ItemDelivery;
import com.geotowny.util.Result;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PhysicalCurrencyService {

    public static final Money[] DENOMINATIONS = {
            Money.ofCents(10), Money.ofCents(50), Money.ofCents(500), Money.ofCents(1000),
            Money.ofCents(2000), Money.ofCents(5000), Money.ofCents(10000), Money.ofCents(100000)
    };
    private static final byte CURRENCY_MARK = 1;

    private final GeoTownyPlugin plugin;
    private final GeoTownyCore core;
    private final PluginConfig config;
    private final PhysicalCurrencyStore store;
    private final NamespacedKey currencyKey;
    private final NamespacedKey denominationKey;
    private final NamespacedKey idKey;
    private final Map<Long, Long> issued = new LinkedHashMap<>();

    public PhysicalCurrencyService(GeoTownyPlugin plugin, GeoTownyCore core,
                                   PluginConfig config, PhysicalCurrencyStore store) {
        this.plugin = plugin;
        this.core = core;
        this.config = config;
        this.store = store;
        this.currencyKey = new NamespacedKey(plugin, "physical_currency");
        this.denominationKey = new NamespacedKey(plugin, "physical_denomination");
        this.idKey = new NamespacedKey(plugin, "physical_currency_id");
    }

    public void load() {
        issued.clear();
        issued.putAll(store.load());
        for (Money denomination : DENOMINATIONS) {
            issued.putIfAbsent(denomination.cents(), 0L);
        }
        save();
        Log.info("实体货币已加载：{} 种面额。", DENOMINATIONS.length);
    }

    public void save() {
        store.save(issued);
    }

    public boolean enabled() {
        return config.physicalCurrencyEnabled();
    }

    public static boolean isValidDenomination(Money amount) {
        if (amount == null) {
            return false;
        }
        for (Money denomination : DENOMINATIONS) {
            if (denomination.cents() == amount.cents()) {
                return true;
            }
        }
        return false;
    }

    public static Money fee(Money amount, double rate) {
        return amount == null ? Money.ZERO : amount.times(Math.max(0.0d, Math.min(1.0d, rate)));
    }

    public static Money withdrawCost(Money amount, double rate) {
        return amount == null ? Money.ZERO : amount.plus(fee(amount, rate));
    }

    public Money feeFor(Money amount) {
        return fee(amount, config.physicalCurrencyFeeRate());
    }

    public Money withdrawCost(Money amount) {
        return withdrawCost(amount, config.physicalCurrencyFeeRate());
    }

    public String denominationName(Money denomination) {
        return denomination == null ? "?" : denomination.format() + " " + config.currencyName();
    }

    public String validDenominations() {
        List<String> names = new ArrayList<>();
        for (Money denomination : DENOMINATIONS) {
            names.add(denominationName(denomination));
        }
        return String.join(" / ", names);
    }

    public ItemStack createCurrency(Money denomination) {
        if (!enabled() || !isValidDenomination(denomination)) {
            return null;
        }
        long current = issued.getOrDefault(denomination.cents(), 0L);
        if (current >= config.physicalCurrencyMaxIssued()) {
            Log.warn("面额 {} 已达最大发行量 {}", denominationName(denomination),
                    config.physicalCurrencyMaxIssued());
            return null;
        }
        ItemStack stack = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        meta.setDisplayName(ChatColor.GOLD.toString() + ChatColor.BOLD
                + denominationName(denomination) + " 纸币");
        String currencyId = UUID.randomUUID().toString();
        meta.setLore(List.of(
                ChatColor.GRAY + "地缘政治货币",
                ChatColor.GRAY + "面额: " + ChatColor.WHITE + denominationName(denomination),
                ChatColor.GRAY + "ID: " + ChatColor.DARK_GRAY + currencyId.substring(0, 8) + "...",
                ChatColor.GRAY + "可用于交易或兑换"));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(currencyKey, PersistentDataType.BYTE, CURRENCY_MARK);
        pdc.set(denominationKey, PersistentDataType.STRING, Long.toString(denomination.cents()));
        pdc.set(idKey, PersistentDataType.STRING, currencyId);
        stack.setItemMeta(meta);
        issued.put(denomination.cents(), current + 1);
        save();
        return stack;
    }

    public boolean isPhysicalCurrency(ItemStack stack) {
        if (stack == null || stack.getType() != Material.PAPER) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte mark = meta.getPersistentDataContainer().get(currencyKey, PersistentDataType.BYTE);
        return mark != null && mark == CURRENCY_MARK;
    }

    public Money getDenomination(ItemStack stack) {
        if (!isPhysicalCurrency(stack)) {
            return Money.ZERO;
        }
        String value = stack.getItemMeta().getPersistentDataContainer()
                .get(denominationKey, PersistentDataType.STRING);
        if (value == null) {
            return Money.ZERO;
        }
        try {
            Money denomination = Money.ofCents(Long.parseLong(value));
            return isValidDenomination(denomination) ? denomination : Money.ZERO;
        } catch (NumberFormatException e) {
            return Money.ZERO;
        }
    }

    public Result withdraw(Player player, Resident resident, Money denomination) {
        if (!enabled()) {
            return Result.fail("实体货币系统已禁用。");
        }
        if (!isValidDenomination(denomination)) {
            return Result.fail("无效面额，可选: " + validDenominations());
        }
        Money fee = feeFor(denomination);
        Money total = withdrawCost(denomination);
        if (!resident.withdraw(total)) {
            return Result.fail("余额不足，需要 " + total.format() + "（含手续费 "
                    + fee.format() + "），当前 " + resident.getBalance().format() + "。");
        }
        ItemStack item = createCurrency(denomination);
        if (item == null) {
            resident.deposit(total);
            core.saveResident(resident);
            return Result.fail("纸币创建失败（可能已达发行上限）。");
        }
        ItemDelivery.give(player, item);
        core.saveResident(resident);
        return Result.ok("已提现 " + denominationName(denomination) + "，手续费 "
                + fee.format() + "，共扣 " + total.format() + " " + config.currencyName() + "。");
    }

    public Result deposit(Player player, Resident resident, Money denomination, int count) {
        if (!enabled()) {
            return Result.fail("实体货币系统已禁用。");
        }
        if (!isValidDenomination(denomination)) {
            return Result.fail("无效面额，可选: " + validDenominations());
        }
        if (count <= 0 || count > 64) {
            return Result.fail("张数必须在 1-64 之间。");
        }
        int found = count(player, denomination);
        if (found < count) {
            return Result.fail("背包中只有 " + found + " 张 " + denominationName(denomination)
                    + " 纸币，需要 " + count + " 张。");
        }
        remove(player, denomination, count);
        Money total = Money.ofCents(Math.multiplyExact(denomination.cents(), (long) count));
        resident.deposit(total);
        core.saveResident(resident);
        return Result.ok("已充值 " + total.format() + " " + config.currencyName()
                + "，" + count + " 张 " + denominationName(denomination) + " 纸币已回收。");
    }

    public int count(Player player, Money denomination) {
        if (player == null || denomination == null) {
            return 0;
        }
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && isPhysicalCurrency(stack)
                    && getDenomination(stack).cents() == denomination.cents()) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    public Money totalValue(Player player) {
        if (player == null) {
            return Money.ZERO;
        }
        long total = 0L;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && isPhysicalCurrency(stack)) {
                total += Math.multiplyExact(getDenomination(stack).cents(), (long) stack.getAmount());
            }
        }
        return Money.ofCents(total);
    }

    public long getIssued(Money denomination) {
        return issued.getOrDefault(denomination.cents(), 0L);
    }

    public void resetIssued() {
        for (Money denomination : DENOMINATIONS) {
            issued.put(denomination.cents(), 0L);
        }
        save();
    }

    private void remove(Player player, Money denomination, int count) {
        int remaining = count;
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || !isPhysicalCurrency(stack)
                    || getDenomination(stack).cents() != denomination.cents()) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            int left = stack.getAmount() - take;
            if (left <= 0) {
                player.getInventory().setItem(slot, null);
            } else {
                stack.setAmount(left);
                player.getInventory().setItem(slot, stack);
            }
            remaining -= take;
        }
    }
}