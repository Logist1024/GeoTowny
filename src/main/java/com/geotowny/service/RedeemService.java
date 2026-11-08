package com.geotowny.service;

import com.geotowny.Log;
import com.geotowny.config.PluginConfig;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.money.Money;
import com.geotowny.domain.redeem.RedeemCode;
import com.geotowny.storage.RedeemStore;
import com.geotowny.util.ItemCodec;
import com.geotowny.util.ItemDelivery;
import com.geotowny.util.Result;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class RedeemService {

    private static final char[] CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PluginConfig config;
    private final RedeemStore store;
    private final Consumer<Resident> residentSaver;
    private final Map<String, RedeemCode> codes = new HashMap<>();

    public RedeemService(PluginConfig config, RedeemStore store, Consumer<Resident> residentSaver) {
        this.config = config;
        this.store = store;
        this.residentSaver = residentSaver;
    }

    public void load() {
        codes.clear();
        Map<String, RedeemCode> loaded = store.load();
        for (Map.Entry<String, RedeemCode> entry : loaded.entrySet()) {
            RedeemCode code = entry.getValue();
            if (code == null || code.getCode() == null) {
                continue;
            }
            if (code.getType() == RedeemCode.RedeemType.ITEM && code.getItemData().isEmpty()) {
                Log.warn("兑换码 {} 为旧物品格式且无法迁移，已安全丢弃。", code.getCode());
                continue;
            }
            codes.put(code.getCode().toUpperCase(Locale.ROOT), code);
        }
        save();
        Log.info("兑换码已加载：{} 个。", codes.size());
    }

    public boolean addCode(RedeemCode code) {
        if (code == null || code.getCode() == null || code.getCode().isBlank()) {
            return false;
        }
        String key = code.getCode().toUpperCase(Locale.ROOT);
        if (codes.containsKey(key)) {
            return false;
        }
        codes.put(key, code);
        save();
        return true;
    }

    public boolean removeCode(String code) {
        if (code == null) {
            return false;
        }
        boolean removed = codes.remove(code.toUpperCase(Locale.ROOT)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public RedeemCode getCode(String code) {
        return code == null ? null : codes.get(code.toUpperCase(Locale.ROOT));
    }

    public Collection<RedeemCode> getAllCodes() {
        return Collections.unmodifiableCollection(new ArrayList<>(codes.values()));
    }

    public Result redeem(Player player, Resident resident, String codeText) {
        if (!config.redeemEnabled()) {
            return Result.fail("兑换码功能已禁用。");
        }
        if (codeText == null || codeText.isBlank()) {
            return Result.fail("兑换码不能为空。");
        }
        RedeemCode code = getCode(codeText);
        if (code == null) {
            return Result.fail("兑换码不存在。");
        }
        if (code.isExpired()) {
            return Result.fail("兑换码已过期。");
        }
        if (!code.hasUsesRemaining()) {
            return Result.fail("兑换码已达到使用次数上限。");
        }
        String uuid = player.getUniqueId().toString();
        if (code.getUsedByPlayerUuids().contains(uuid)) {
            return Result.fail("你已经使用过这个兑换码了。");
        }

        code.incrementUsedCount();
        code.getUsedByPlayerUuids().add(uuid);
        save();

        try {
            return switch (code.getType()) {
                case GEO -> {
                    resident.deposit(code.getValue());
                    residentSaver.accept(resident);
                    yield Result.ok("兑换成功！获得 " + code.getValue().format() + " "
                            + config.currencyName() + "。");
                }
                case ITEM -> {
                    int given = 0;
                    for (String data : code.getItemData()) {
                        ItemStack stack = ItemCodec.decode(data);
                        if (stack != null) {
                            ItemDelivery.give(player, stack);
                            given++;
                        }
                    }
                    if (given == 0) {
                        yield Result.fail("兑换码没有可发放的物品。");
                    }
                    yield Result.ok("兑换成功！获得 " + given + " 个物品。");
                }
                case COMMAND -> {
                    String command = code.getCommand().startsWith("/")
                            ? code.getCommand().substring(1) : code.getCommand();
                    boolean success = Bukkit.dispatchCommand(player, command);
                    yield success ? Result.ok("兑换成功！奖励已发放。")
                            : Result.fail("指令执行失败，请联系管理员。");
                }
            };
        } catch (RuntimeException e) {
            Log.error("兑换码 {} 发奖失败: {}", code.getCode(), e.getMessage());
            return Result.fail("奖励发放失败，请联系管理员。");
        }
    }

    public String generateRandomCode() {
        return generateRandomCode(8);
    }

    public String generateRandomCode(int length) {
        int size = Math.max(4, Math.min(16, length));
        for (int attempt = 0; attempt < 100; attempt++) {
            StringBuilder builder = new StringBuilder(size);
            for (int i = 0; i < size; i++) {
                builder.append(CODE_CHARS[RANDOM.nextInt(CODE_CHARS.length)]);
            }
            String candidate = builder.toString();
            if (!codes.containsKey(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    public RedeemCode createGeo(String code, Money value, int maxUses) {
        RedeemCode redeemCode = new RedeemCode(code, RedeemCode.RedeemType.GEO);
        redeemCode.setValue(value);
        redeemCode.setMaxUses(maxUses);
        return addCode(redeemCode) ? redeemCode : null;
    }

    public RedeemCode createItem(String code, ItemStack stack, int maxUses) {
        String data = ItemCodec.encode(stack);
        if (data == null) {
            return null;
        }
        RedeemCode redeemCode = new RedeemCode(code, RedeemCode.RedeemType.ITEM);
        redeemCode.setItemData(List.of(data));
        redeemCode.setMaxUses(maxUses);
        return addCode(redeemCode) ? redeemCode : null;
    }

    public RedeemCode createCommand(String code, String command, int maxUses) {
        RedeemCode redeemCode = new RedeemCode(code, RedeemCode.RedeemType.COMMAND);
        redeemCode.setCommand(command);
        redeemCode.setMaxUses(maxUses);
        return addCode(redeemCode) ? redeemCode : null;
    }

    public boolean setMaxUses(String code, int maxUses) {
        RedeemCode redeemCode = getCode(code);
        if (redeemCode == null) {
            return false;
        }
        redeemCode.setMaxUses(maxUses);
        save();
        return true;
    }

    public void save() {
        store.save(codes);
    }
}