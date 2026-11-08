package com.geotowny.command;

import com.geotowny.GeoTownyPlugin;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.money.Money;
import com.geotowny.domain.redeem.RedeemCode;
import com.geotowny.gui.RedeemGui;
import com.geotowny.service.RedeemService;
import com.geotowny.util.Result;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

public final class RedeemCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "create", "generate", "list", "delete", "setmax", "gui");

    private final GeoTownyPlugin plugin;
    private final RedeemService service;
    private final GeoTownyCore core;

    public RedeemCommand(GeoTownyPlugin plugin, RedeemService service, GeoTownyCore core) {
        this.plugin = plugin;
        this.service = service;
        this.core = core;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            usage(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(sender, args);
            case "generate" -> generate(sender, args);
            case "list" -> list(sender);
            case "delete" -> delete(sender, args);
            case "setmax" -> setMax(sender, args);
            case "gui" -> gui(sender);
            default -> redeem(sender, args[0]);
        }
        return true;
    }

    private void redeem(CommandSender sender, String code) {
        if (!(sender instanceof Player player)) {
            send(sender, "只有玩家可以兑换兑换码。");
            return;
        }
        if (!player.hasPermission("geotowny.redeem.use")) {
            send(player, "你没有权限兑换兑换码。");
            return;
        }
        Resident resident = core.getOrCreateResident(player.getUniqueId(), player.getName());
        Result result = service.redeem(player, resident, code);
        send(sender, (result.isSuccess() ? ChatColor.GREEN : ChatColor.RED) + result.getMessage());
    }

    private void create(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || args.length < 3) {
            if (args.length < 3) {
                send(sender, "用法: /redeem create <码> <geo|item|command> ...");
            }
            return;
        }
        String code = args[1];
        String type = args[2].toLowerCase(Locale.ROOT);
        switch (type) {
            case "geo" -> {
                if (args.length < 4) {
                    send(sender, "用法: /redeem create <码> geo <金额>");
                    return;
                }
                Money amount;
                try {
                    amount = Money.parse(args[3]);
                } catch (RuntimeException e) {
                    send(sender, "金额格式无效。");
                    return;
                }
                RedeemCode created = service.createGeo(code, amount, 1);
                send(sender, created == null ? "兑换码已存在或创建失败。" :
                        "Geo 兑换码已创建: " + code + "（奖励 " + amount.format() + "）");
            }
            case "item" -> {
                if (!(sender instanceof Player player)) {
                    send(sender, "只有玩家可以使用手持物品创建物品兑换码。");
                    return;
                }
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType() == Material.AIR) {
                    send(sender, "请手持要作为奖励的物品。");
                    return;
                }
                RedeemCode created = service.createItem(code, hand, 1);
                send(sender, created == null ? "兑换码已存在或创建失败。" :
                        "物品兑换码已创建: " + code + "（" + hand.getType() + "）");
            }
            case "command" -> {
                if (args.length < 4) {
                    send(sender, "用法: /redeem create <码> command <指令>");
                    return;
                }
                RedeemCode created = service.createCommand(code, String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)), 1);
                send(sender, created == null ? "兑换码已存在或创建失败。" :
                        "指令兑换码已创建: " + code);
            }
            default -> send(sender, "类型必须是 geo、item 或 command。");
        }
    }

    private void generate(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || args.length < 2) {
            if (args.length < 2) {
                send(sender, "用法: /redeem generate <geo|item|command> ...");
            }
            return;
        }
        String type = args[1].toLowerCase(Locale.ROOT);
        String code = service.generateRandomCode();
        if (code == null) {
            send(sender, "生成兑换码失败，请重试。");
            return;
        }
        switch (type) {
            case "geo" -> {
                if (args.length < 3) {
                    send(sender, "用法: /redeem generate geo <金额> [次数]");
                    return;
                }
                Money amount;
                int maxUses;
                try {
                    amount = Money.parse(args[2]);
                    maxUses = args.length > 3 ? Integer.parseInt(args[3]) : 1;
                } catch (RuntimeException e) {
                    send(sender, "金额或次数格式无效。");
                    return;
                }
                if (service.createGeo(code, amount, maxUses) == null) {
                    send(sender, "生成兑换码失败。");
                    return;
                }
                send(sender, "随机 Geo 兑换码: " + code + "（" + amount.format()
                        + "，可用 " + maxUses + " 次）");
            }
            case "item" -> {
                if (!(sender instanceof Player player)) {
                    send(sender, "只有玩家可以生成手持物品兑换码。");
                    return;
                }
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType() == Material.AIR) {
                    send(sender, "请手持要作为奖励的物品。");
                    return;
                }
                int maxUses = args.length > 2 ? parseInt(args[2], 1) : 1;
                if (service.createItem(code, hand, maxUses) == null) {
                    send(sender, "生成兑换码失败。");
                    return;
                }
                send(sender, "随机物品兑换码: " + code + "（" + hand.getType() + "，可用 " + maxUses + " 次）");
            }
            case "command" -> {
                if (args.length < 3) {
                    send(sender, "用法: /redeem generate command <指令>");
                    return;
                }
                String command = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                if (service.createCommand(code, command, 1) == null) {
                    send(sender, "生成兑换码失败。");
                    return;
                }
                send(sender, "随机指令兑换码: " + code);
            }
            default -> send(sender, "类型必须是 geo、item 或 command。");
        }
    }

    private void list(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        send(sender, ChatColor.GOLD + "===== 兑换码列表 (" + service.getAllCodes().size() + " 个) =====");
        for (RedeemCode code : service.getAllCodes()) {
            String status = code.isExpired() ? "已过期" : code.hasUsesRemaining() ? "可用" : "已用完";
            send(sender, code.getCode() + " [" + code.getType() + "] 已用:"
                    + code.getUsedCount() + "/" + (code.getMaxUses() <= 0 ? "∞" : code.getMaxUses())
                    + " " + status);
        }
    }

    private void delete(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || args.length < 2) {
            if (args.length < 2) {
                send(sender, "用法: /redeem delete <码>");
            }
            return;
        }
        send(sender, service.removeCode(args[1]) ? "兑换码已删除: " + args[1] : "兑换码不存在: " + args[1]);
    }

    private void setMax(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || args.length < 3) {
            if (args.length < 3) {
                send(sender, "用法: /redeem setmax <码> <次数>");
            }
            return;
        }
        int max;
        try {
            max = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            send(sender, "次数格式无效。");
            return;
        }
        send(sender, service.setMaxUses(args[1], max)
                ? "兑换码 " + args[1] + " 的使用次数已设为 " + max
                : "兑换码不存在: " + args[1]);
    }

    private void gui(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            send(sender, "只有玩家可以打开兑换码管理 GUI。");
            return;
        }
        new RedeemGui(plugin, player, service, plugin.config(), 0).open();
    }

    private boolean requireAdmin(CommandSender sender) {
        if (!sender.hasPermission("geotowny.redeem.admin")) {
            send(sender, "你没有权限执行该操作。");
            return false;
        }
        return true;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void usage(CommandSender sender) {
        send(sender, "/redeem <兑换码> - 兑换奖励");
        send(sender, "/redeem create <码> <geo|item|command> ...");
        send(sender, "/redeem generate <geo|item|command> ...");
        send(sender, "/redeem list|delete|setmax|gui");
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.GOLD + "[GeoTowny] " + ChatColor.RESET + message);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            return List.of("geo", "item", "command").stream()
                    .filter(value -> value.startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("generate")) {
            return List.of("geo", "item", "command").stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}