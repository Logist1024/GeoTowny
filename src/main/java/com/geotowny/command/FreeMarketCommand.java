package com.geotowny.command;

import com.geotowny.GeoTownyPlugin;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.market.Listing;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.money.Money;
import com.geotowny.gui.FreeMarketGui;
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

public final class FreeMarketCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "page", "mine", "sell", "buy", "cancel", "search", "save");

    private final GeoTownyPlugin plugin;
    private final FreeMarketService market;
    private final GeoTownyCore core;

    public FreeMarketCommand(GeoTownyPlugin plugin, FreeMarketService market, GeoTownyCore core) {
        this.plugin = plugin;
        this.market = market;
        this.core = core;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            open(sender, 0, 0);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "page" -> open(sender, args.length > 1 ? parsePage(args[1]) : 0, 0);
            case "mine" -> open(sender, 0, 1);
            case "sell" -> sell(sender, args);
            case "buy" -> buy(sender, args);
            case "cancel" -> cancel(sender, args);
            case "search" -> search(sender, args.length > 1 ? args[1] : null);
            case "save" -> save(sender);
            default -> usage(sender, label);
        }
        return true;
    }

    private void open(CommandSender sender, int page, int viewMode) {
        if (!(sender instanceof Player player)) {
            send(sender, "只有玩家可以打开自由市场 GUI。");
            return;
        }
        if (!player.hasPermission("geotowny.fm.use")) {
            send(player, "你没有权限使用自由市场。");
            return;
        }
        new FreeMarketGui(plugin, player, market, core, plugin.config(), page, viewMode).open();
    }

    private void sell(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 4) {
            send(sender, "用法: /fm sell <物品ID> <数量> <单价>");
            return;
        }
        int quantity;
        Money unitPrice;
        try {
            quantity = Integer.parseInt(args[2]);
            unitPrice = Money.parse(args[3]);
        } catch (RuntimeException e) {
            send(sender, "数量或单价格式无效。");
            return;
        }
        Result result = market.list(player, args[1], quantity, unitPrice);
        send(sender, (result.isSuccess() ? ChatColor.GREEN : ChatColor.RED) + result.getMessage());
    }

    private void buy(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 3) {
            send(sender, "用法: /fm buy <挂单ID> <数量>");
            return;
        }
        try {
            long id = Long.parseLong(args[1]);
            int quantity = Integer.parseInt(args[2]);
            Resident resident = core.getOrCreateResident(player.getUniqueId(), player.getName());
            Result result = market.buy(player, resident, id, quantity);
            send(sender, (result.isSuccess() ? ChatColor.GREEN : ChatColor.RED) + result.getMessage());
        } catch (NumberFormatException e) {
            send(sender, "挂单 ID 或数量格式无效。");
        }
    }

    private void cancel(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            send(sender, "用法: /fm cancel <挂单ID>");
            return;
        }
        try {
            Result result = market.cancel(player, Long.parseLong(args[1]));
            send(sender, (result.isSuccess() ? ChatColor.GREEN : ChatColor.RED) + result.getMessage());
        } catch (NumberFormatException e) {
            send(sender, "挂单 ID 格式无效。");
        }
    }

    private void search(CommandSender sender, String keyword) {
        String filter = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
        send(sender, ChatColor.GOLD + "=== 自由市场搜索: "
                + (keyword == null ? "全部" : keyword) + " ===");
        int shown = 0;
        for (Listing listing : market.allListings()) {
            if (!filter.isEmpty()
                    && !listing.getDisplayName().toLowerCase(Locale.ROOT).contains(filter)
                    && !listing.getItemId().toLowerCase(Locale.ROOT).contains(filter)
                    && !listing.getSellerName().toLowerCase(Locale.ROOT).contains(filter)) {
                continue;
            }
            send(sender, " - #" + listing.getId() + " " + listing.getDisplayName()
                    + " [" + listing.getItemId() + "] ×" + listing.getQuantity()
                    + " 单价 " + listing.getUnitPrice().format() + " "
                    + plugin.config().currencyName() + "（卖家: " + listing.getSellerName() + "）");
            shown++;
            if (shown >= 30) {
                send(sender, "（仅显示前 30 条）");
                break;
            }
        }
        if (shown == 0) {
            send(sender, "未找到匹配挂单。");
        }
    }

    private void save(CommandSender sender) {
        if (!sender.hasPermission("geotowny.fm.admin")) {
            send(sender, "你没有权限执行该操作。");
            return;
        }
        market.save();
        send(sender, "自由市场数据已保存。");
    }

    private Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, "只有玩家可以执行市场交易。");
            return null;
        }
        if (!player.hasPermission("geotowny.fm.use")) {
            send(player, "你没有权限使用自由市场。");
            return null;
        }
        return player;
    }

    private static int parsePage(String value) {
        try {
            return Math.max(0, Integer.parseInt(value) - 1);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void usage(CommandSender sender, String label) {
        send(sender, "/" + label + " [page <n>|mine|search <关键词>]");
        send(sender, "/" + label + " sell <物品ID> <数量> <单价>");
        send(sender, "/" + label + " buy|cancel <挂单ID> [数量]");
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
            if (sub.equals("sell")) {
                return filter(com.geotowny.domain.market.ShopCatalog.defaults().stream()
                        .map(item -> item.getItemId()).toList(), args[1]);
            }
            if (sub.equals("buy") || sub.equals("cancel")) {
                return filter(market.allListings().stream()
                        .map(listing -> Long.toString(listing.getId())).toList(), args[1]);
            }
        }
        return List.of();
    }

    private static List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}