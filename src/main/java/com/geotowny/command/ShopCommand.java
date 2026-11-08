package com.geotowny.command;

import com.geotowny.GeoTownyPlugin;
import com.geotowny.config.PluginConfig;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.market.MarketItem;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.money.Money;
import com.geotowny.gui.ShopGui;
import com.geotowny.service.MarketService;
import com.geotowny.service.FreeMarketService;
import com.geotowny.util.Result;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class ShopCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "page", "prices", "quota", "buy", "sell", "reload",
            "adjust", "resetprice", "resetvolume");

    private final GeoTownyPlugin plugin;
    private final MarketService market;
    private final FreeMarketService freeMarket;
    private final GeoTownyCore core;
    private final PluginConfig config;

    public ShopCommand(GeoTownyPlugin plugin, MarketService market, FreeMarketService freeMarket,
                       GeoTownyCore core, PluginConfig config) {
        this.plugin = plugin;
        this.market = market;
        this.freeMarket = freeMarket;
        this.core = core;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            open(sender, 0);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "page" -> {
                int page = args.length > 1 ? parsePage(args[1]) : 0;
                open(sender, page);
            }
            case "prices" -> prices(sender, args.length > 1 ? args[1] : null);
            case "quota" -> quota(sender);
            case "buy" -> trade(sender, args, true);
            case "sell" -> trade(sender, args, false);
            case "reload" -> reload(sender);
            case "adjust" -> adjust(sender, args);
            case "resetprice" -> resetPrice(sender, args);
            case "resetvolume" -> resetVolume(sender, args);
            default -> usage(sender, label);
        }
        return true;
    }

    private void open(CommandSender sender, int page) {
        if (!(sender instanceof Player player)) {
            send(sender, "只有玩家可以打开商店 GUI。");
            return;
        }
        if (!player.hasPermission("geotowny.shop.use")) {
            send(player, "你没有权限使用商店。");
            return;
        }
        if (!config.shopEnabled()) {
            send(player, "商店已禁用。");
            return;
        }
        new ShopGui(plugin, player, market, freeMarket, core, config, page).open();
    }

    private void prices(CommandSender sender, String keyword) {
        send(sender, ChatColor.GOLD + "=== 商店价格 ===");
        String filter = keyword == null ? null : keyword.toLowerCase(Locale.ROOT);
        int shown = 0;
        for (MarketItem item : market.items()) {
            if (filter != null
                    && !item.getDisplayName().toLowerCase(Locale.ROOT).contains(filter)
                    && !item.getItemId().toLowerCase(Locale.ROOT).contains(filter)) {
                continue;
            }
            send(sender, " - " + item.getDisplayName() + " [" + item.getItemId() + "] 买 "
                    + market.currentBuyPrice(item).format() + " / 卖 "
                    + market.currentSellPrice(item).format() + " " + config.currencyName()
                    + "（累计买/卖 " + item.getTotalBought() + "/" + item.getTotalSold() + "）");
            shown++;
            if (shown >= 30) {
                send(sender, "（仅显示前 30 条，使用 /shop prices <关键词> 过滤）");
                break;
            }
        }
        if (shown == 0) {
            send(sender, "未找到匹配商品。");
        }
    }

    private void quota(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, "只有玩家可以查询个人市场额度。");
            return;
        }
        String uuid = player.getUniqueId().toString();
        send(sender, ChatColor.GOLD + "=== 今日市场额度 ===");
        send(sender, "个人剩余 购买: " + market.playerRemainingBuy(uuid)
                + " | 出售: " + market.playerRemainingSell(uuid));
        send(sender, "全服剩余 购买: " + market.serverRemainingBuy()
                + " | 出售: " + market.serverRemainingSell());
    }

    private void trade(CommandSender sender, String[] args, boolean buy) {
        if (!(sender instanceof Player player)) {
            send(sender, "只有玩家可以执行市场交易。");
            return;
        }
        if (!player.hasPermission("geotowny.shop.use")) {
            send(player, "你没有权限使用商店。");
            return;
        }
        if (args.length < 3) {
            send(sender, "用法: /shop " + (buy ? "buy" : "sell") + " <物品ID> <数量>");
            return;
        }
        int quantity;
        try {
            quantity = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            send(sender, "数量格式无效: " + args[2]);
            return;
        }
        Resident resident = core.getOrCreateResident(player.getUniqueId(), player.getName());
        Result result = buy
                ? market.buy(player, resident, args[1], quantity)
                : market.sell(player, resident, args[1], quantity);
        send(sender, (result.isSuccess() ? ChatColor.GREEN : ChatColor.RED) + result.getMessage());
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("geotowny.shop.admin")) {
            send(sender, "你没有权限执行该操作。");
            return;
        }
        market.save();
        send(sender, "商店数据已保存。");
    }

    private void adjust(CommandSender sender, String[] args) {
        if (!sender.hasPermission("geotowny.shop.admin")) {
            send(sender, "你没有权限执行该操作。");
            return;
        }
        if (args.length < 3) {
            send(sender, "用法: /shop adjust <物品ID> <倍率>");
            return;
        }
        double multiplier;
        try {
            multiplier = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            send(sender, "倍率格式无效: " + args[2]);
            return;
        }
        Result result = market.adjustPrice(args[1], multiplier);
        send(sender, (result.isSuccess() ? ChatColor.GREEN : ChatColor.RED) + result.getMessage());
    }

    private void resetPrice(CommandSender sender, String[] args) {
        if (!sender.hasPermission("geotowny.shop.admin")) {
            send(sender, "你没有权限执行该操作。");
            return;
        }
        if (args.length < 2) {
            send(sender, "用法: /shop resetprice <物品ID>");
            return;
        }
        Result result = market.resetPrice(args[1]);
        send(sender, (result.isSuccess() ? ChatColor.GREEN : ChatColor.RED) + result.getMessage());
    }

    private void resetVolume(CommandSender sender, String[] args) {
        if (!sender.hasPermission("geotowny.shop.admin")) {
            send(sender, "你没有权限执行该操作。");
            return;
        }
        if (args.length < 2) {
            send(sender, "用法: /shop resetvolume <物品ID>");
            return;
        }
        Result result = market.resetVolume(args[1]);
        send(sender, (result.isSuccess() ? ChatColor.GREEN : ChatColor.RED) + result.getMessage());
    }

    private static int parsePage(String value) {
        try {
            return Math.max(0, Integer.parseInt(value) - 1);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void usage(CommandSender sender, String label) {
        send(sender, "/" + label + " [page <n>|prices [关键词]|quota]");
        send(sender, "/" + label + " buy|sell <物品ID> <数量>");
        send(sender, "/" + label + " adjust|resetprice|resetvolume <物品ID> [倍率]");
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.GOLD + "[GeoTowny] " + ChatColor.RESET + message);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("buy") || sub.equals("sell") || sub.equals("adjust")
                    || sub.equals("resetprice") || sub.equals("resetvolume")) {
                return filter(market.items().stream().map(MarketItem::getItemId).toList(), args[1]);
            }
        }
        return List.of();
    }

    private static List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}