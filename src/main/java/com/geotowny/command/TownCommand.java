package com.geotowny.command;

import com.geotowny.GeoTownyPlugin;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.Town;
import com.geotowny.domain.model.TownLog;
import com.geotowny.domain.model.WorldLocation;
import com.geotowny.domain.money.Money;
import com.geotowny.gui.TownMemberGui;
import com.geotowny.service.ClaimService;
import com.geotowny.service.TownLogService;
import com.geotowny.service.TownService;
import com.geotowny.util.Result;
import com.geotowny.world.WorldRegistry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /town 命令（P3 核心子命令）。
 * <p>已实现：new/info/list/members/claim/unclaim/invite/accept/decline/join/leave/kick/
 * deposit/withdraw/delete/rename/set/spawn。
 * <p>merge/split 等复杂操作在 P3 后续或 P6 接入。
 */
public final class TownCommand implements CommandExecutor, TabCompleter {

    private static final long INVITE_TIMEOUT_MS = 60_000L;
    private static final long DELETE_TIMEOUT_MS = 30_000L;
    private static final long SPAWN_COOLDOWN_MS = 10_000L;

    private static final List<String> SUBCOMMANDS = List.of(
            "new", "info", "list", "members", "claim", "unclaim",
            "invite", "accept", "decline", "join", "leave", "kick",
            "deposit", "withdraw", "delete", "rename", "set", "spawn",
            "merge", "split", "logs");

    private static final List<String> SET_OPTIONS = List.of(
            "board", "taxes", "open", "public", "mayor", "spawn", "code", "border", "plotprice");

    private final GeoTownyPlugin plugin;
    private final GeoTownyCore core;
    private final TownService townService;
    private final ClaimService claimService;
    private final TownLogService logService;
    private final WorldRegistry worlds;

    private final Map<UUID, String[]> pendingInvites = new ConcurrentHashMap<>();
    private final Map<UUID, String[]> pendingDeletes = new ConcurrentHashMap<>();
    private final Map<UUID, String[]> pendingMerges = new ConcurrentHashMap<>();
    private final Map<UUID, Long> spawnCooldowns = new ConcurrentHashMap<>();

    public TownCommand(GeoTownyPlugin plugin, GeoTownyCore core, TownService townService,
                       ClaimService claimService, TownLogService logService, WorldRegistry worlds) {
        this.plugin = plugin;
        this.core = core;
        this.townService = townService;
        this.claimService = claimService;
        this.logService = logService;
        this.worlds = worlds;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            info(sender, null);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "new" -> newTown(sender, args);
            case "info" -> info(sender, args.length > 1 ? joinArgs(args, 1) : null);
            case "list" -> list(sender);
            case "members" -> members(sender);
            case "claim" -> claim(sender);
            case "unclaim" -> unclaim(sender);
            case "invite" -> invite(sender, args);
            case "accept" -> acceptInvite(sender);
            case "decline" -> declineInvite(sender);
            case "join" -> join(sender, args);
            case "leave" -> leave(sender);
            case "kick" -> kick(sender, args);
            case "deposit" -> deposit(sender, args);
            case "withdraw" -> withdraw(sender, args);
            case "delete" -> delete(sender);
            case "rename" -> rename(sender, args);
            case "set" -> set(sender, args);
            case "spawn" -> spawn(sender, args.length > 1 ? joinArgs(args, 1) : null);
            case "merge" -> merge(sender, args);
            case "split" -> split(sender, args);
            case "logs" -> logs(sender, args);
            default -> help(sender, label);
        }
        return true;
    }

    private void newTown(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "geotowny.town.create")) {
            return;
        }
        if (args.length < 2) {
            send(sender, "用法: /town new <名称>");
            return;
        }
        Resident resident = resident(player);
        String name = joinArgs(args, 1);
        Location location = player.getLocation();
        WorldLocation spawn = new WorldLocation(worlds.toInternalKey(location.getWorld()),
                location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        Result result = townService.createTown(resident, name, worlds.toInternalKey(location.getWorld()),
                location.getChunk().getX(), location.getChunk().getZ(), spawn);
        send(sender, result.getMessage());
    }

    private void info(CommandSender sender, String name) {
        Town town;
        if (name != null) {
            town = core.getTownByName(name);
        } else {
            Player player = sender instanceof Player p ? p : null;
            if (player == null) {
                send(sender, "控制台请使用 /town info <名称>。");
                return;
            }
            Resident resident = resident(player);
            town = resident.hasTown() ? core.getTown(resident.getTownUuid()) : null;
        }
        if (town == null) {
            send(sender, "城镇不存在。");
            return;
        }
        send(sender, ChatColor.GOLD + "=== " + town.getName() + " [" + town.getCode() + "] ===");
        send(sender, "市长: " + nameOf(town.getMayorUuid())
                + " | 国家: " + (town.hasNation() ? nameOfNation(town.getNationUuid()) : "无"));
        send(sender, "居民: " + town.getResidentCount() + " | 领地: " + town.getClaimCount()
                + " | 中心: " + town.getHomeBlockKey());
        send(sender, "国库: " + town.getBalance().format() + " " + plugin.config().currencyName()
                + " | 居民税: " + town.getTaxes().format());
        send(sender, "开放加入: " + (town.isOpen() ? "是" : "否")
                + " | 出生点公开: " + (town.isPublic() ? "是" : "否"));
        if (town.getBoard() != null && !town.getBoard().isBlank()) {
            send(sender, "公告: " + town.getBoard());
        }
    }

    private void list(CommandSender sender) {
        List<Town> towns = new ArrayList<>(core.allTowns());
        if (towns.isEmpty()) {
            send(sender, "当前没有城镇。");
            return;
        }
        send(sender, "=== 城镇列表 (" + towns.size() + ") ===");
        towns.stream().limit(20).forEach(town -> send(sender,
                " - " + town.getName() + " [" + town.getCode() + "] | 居民 " + town.getResidentCount()
                        + " | 领地 " + town.getClaimCount()));
    }

    private void members(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        Resident resident = resident(player);
        Town town = townOf(resident);
        if (town == null) {
            send(sender, "你不在任何城镇中。");
            return;
        }
        send(sender, "=== " + town.getName() + " 成员 ===");
        new TownMemberGui(plugin, player, town, core, 0).open();
    }

    private void claim(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "geotowny.town.claim")) {
            return;
        }
        Resident resident = resident(player);
        Town town = townOf(resident);
        if (town == null) {
            send(sender, "你不在任何城镇中。");
            return;
        }
        Location location = player.getLocation();
        Result result = claimService.claim(resident, town, worlds.toInternalKey(location.getWorld()),
                location.getChunk().getX(), location.getChunk().getZ());
        send(sender, result.getMessage());
    }

    private void unclaim(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "geotowny.town.claim")) {
            return;
        }
        Resident resident = resident(player);
        Town town = townOf(resident);
        if (town == null) {
            send(sender, "你不在任何城镇中。");
            return;
        }
        Location location = player.getLocation();
        Result result = claimService.unclaim(resident, town, worlds.toInternalKey(location.getWorld()),
                location.getChunk().getX(), location.getChunk().getZ());
        send(sender, result.getMessage());
    }

    private void invite(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "geotowny.town.invite")) {
            return;
        }
        if (args.length < 2) {
            send(sender, "用法: /town invite <玩家>");
            return;
        }
        Resident resident = resident(player);
        Town town = townOf(resident);
        if (town == null) {
            send(sender, "你不在任何城镇中。");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            send(sender, "目标玩家不在线。");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            send(sender, "不能邀请自己。");
            return;
        }
        pendingInvites.put(target.getUniqueId(), new String[]{town.getUuid(), String.valueOf(System.currentTimeMillis())});
        send(sender, "已向 " + target.getName() + " 发送城镇邀请（60 秒内有效）。");
        send(target, player.getName() + " 邀请你加入城镇 " + town.getName() + "。输入 /town accept 接受，/town decline 拒绝。");
    }

    private void acceptInvite(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        String[] invite = pendingInvites.remove(player.getUniqueId());
        if (invite == null || System.currentTimeMillis() - Long.parseLong(invite[1]) > INVITE_TIMEOUT_MS) {
            send(sender, "你没有待处理的城镇邀请。");
            return;
        }
        Town town = core.getTown(invite[0]);
        if (town == null) {
            send(sender, "邀请的城镇已不存在。");
            return;
        }
        Result result = townService.joinTown(resident(player), town, false);
        send(sender, result.getMessage());
    }

    private void declineInvite(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        String[] invite = pendingInvites.remove(player.getUniqueId());
        if (invite == null) {
            send(sender, "你没有待处理的城镇邀请。");
            return;
        }
        Town town = core.getTown(invite[0]);
        send(sender, "已拒绝加入城镇 " + (town == null ? "?" : town.getName()) + " 的邀请。");
    }

    private void join(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            send(sender, "用法: /town join <城镇名>");
            return;
        }
        Town town = core.getTownByName(joinArgs(args, 1));
        if (town == null) {
            send(sender, "城镇不存在。");
            return;
        }
        Result result = townService.joinTown(resident(player), town, true);
        send(sender, result.getMessage());
    }

    private void leave(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        Resident resident = resident(player);
        Town town = townOf(resident);
        if (town == null) {
            send(sender, "你不在任何城镇中。");
            return;
        }
        Result result = townService.leaveTown(resident, town);
        send(sender, result.getMessage());
    }

    private void kick(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "geotowny.town.kick")) {
            return;
        }
        if (args.length < 2) {
            send(sender, "用法: /town kick <玩家>");
            return;
        }
        Resident resident = resident(player);
        Town town = townOf(resident);
        if (town == null) {
            send(sender, "你不在任何城镇中。");
            return;
        }
        Resident target = core.getResidentByName(args[1]);
        Result result = townService.kickMember(resident, town, target);
        send(sender, result.getMessage());
    }

    private void deposit(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "geotowny.town.deposit")) {
            return;
        }
        Money amount = parseAmount(sender, args);
        if (amount == null) {
            return;
        }
        Resident resident = resident(player);
        Town town = townOf(resident);
        if (town == null) {
            send(sender, "你不在任何城镇中。");
            return;
        }
        Result result = townService.deposit(resident, town, amount);
        send(sender, result.getMessage());
    }

    private void withdraw(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "geotowny.town.withdraw")) {
            return;
        }
        Money amount = parseAmount(sender, args);
        if (amount == null) {
            return;
        }
        Resident resident = resident(player);
        Town town = townOf(resident);
        if (town == null) {
            send(sender, "你不在任何城镇中。");
            return;
        }
        Result result = townService.withdraw(resident, town, amount);
        send(sender, result.getMessage());
    }

    private void delete(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "geotowny.town.set")) {
            return;
        }
        Resident resident = resident(player);
        Town town = townOf(resident);
        if (town == null) {
            send(sender, "你不在任何城镇中。");
            return;
        }
        String[] pending = pendingDeletes.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (pending != null && town.getUuid().equals(pending[0])
                && now - Long.parseLong(pending[1]) <= DELETE_TIMEOUT_MS) {
            pendingDeletes.remove(player.getUniqueId());
            Result result = townService.deleteTown(resident, town);
            send(sender, result.getMessage());
            return;
        }
        pendingDeletes.put(player.getUniqueId(), new String[]{town.getUuid(), String.valueOf(now)});
        send(sender, ChatColor.RED + "警告：即将解散城镇 " + town.getName() + "，所有领地将被释放。");
        send(sender, ChatColor.RED + "请在 30 秒内再次输入 /town delete 确认。");
    }

    private void rename(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "geotowny.town.set")) {
            return;
        }
        if (args.length < 2) {
            send(sender, "用法: /town rename <新名称>");
            return;
        }
        Resident resident = resident(player);
        Town town = townOf(resident);
        if (town == null) {
            send(sender, "你不在任何城镇中。");
            return;
        }
        Result result = townService.renameTown(resident, town, joinArgs(args, 1));
        send(sender, result.getMessage());
    }

    private void set(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "geotowny.town.set")) {
            return;
        }
        if (args.length < 3) {
            send(sender, "用法: /town set <board|taxes|open|public|mayor|spawn|code> <值>");
            return;
        }
        Resident resident = resident(player);
        Town town = townOf(resident);
        if (town == null) {
            send(sender, "你不在任何城镇中。");
            return;
        }
        String option = args[1].toLowerCase(Locale.ROOT);
        switch (option) {
            case "board" -> send(sender, townService.setBoard(resident, town, joinArgs(args, 2)).getMessage());
            case "taxes" -> {
                Money amount = parseAmount(sender, args, 2);
                if (amount != null) {
                    send(sender, townService.setTaxes(resident, town, amount).getMessage());
                }
            }
            case "open" -> send(sender, townService.setOpen(resident, town, parseBoolean(sender, args[2])).getMessage());
            case "public" -> send(sender, townService.setPublic(resident, town, parseBoolean(sender, args[2])).getMessage());
            case "mayor" -> {
                Resident target = core.getResidentByName(args[2]);
                send(sender, townService.setMayor(resident, town, target).getMessage());
            }
            case "spawn" -> {
                Location location = player.getLocation();
                town.setSpawn(new WorldLocation(worlds.toInternalKey(location.getWorld()),
                        location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch()));
                core.saveTown(town);
                send(sender, "城镇出生点已更新。");
            }
            case "code" -> send(sender, townService.setCode(resident, town, args[2]).getMessage());
            case "border" -> setBorder(sender, resident, town, args);
            case "plotprice" -> {
                Money amount = parseAmount(sender, args, 2);
                if (amount != null) {
                    town.setPlotPrice(amount);
                    core.saveTown(town);
                    send(sender, "个人地块价格已设为 " + amount.format() + "。");
                }
            }
            default -> send(sender, "未知设置项: " + option);
        }
    }

    private void spawn(CommandSender sender, String name) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = spawnCooldowns.get(player.getUniqueId());
        if (last != null && now - last < SPAWN_COOLDOWN_MS) {
            send(sender, "传送冷却中，请 " + ((SPAWN_COOLDOWN_MS - (now - last)) / 1000 + 1) + " 秒后再试。");
            return;
        }
        Resident resident = resident(player);
        Town town;
        if (name == null || name.isBlank()) {
            town = townOf(resident);
        } else {
            town = core.getTownByName(name);
            if (town != null && !resident.isInTown(town.getUuid()) && !town.isPublic()) {
                send(sender, "该城镇出生点未公开。");
                return;
            }
        }
        if (town == null) {
            send(sender, "城镇不存在。");
            return;
        }
        WorldLocation location = town.getSpawn();
        if (location == null) {
            send(sender, "该城镇尚未设置出生点。");
            return;
        }
        World world = worlds.toWorld(location.getWorldKey());
        if (world == null) {
            send(sender, "出生点所在世界未加载。");
            return;
        }
        player.teleport(new Location(world, location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch()));
        spawnCooldowns.put(player.getUniqueId(), now);
        send(sender, "已传送到城镇 " + town.getName() + " 的出生点。");
    }

    private void merge(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "geotowny.town.merge")) {
            return;
        }
        if (args.length < 2) {
            send(sender, "用法: /town merge <目标城镇>");
            return;
        }
        Resident resident = resident(player);
        Town source = townOf(resident);
        if (source == null) {
            send(sender, "你不在任何城镇中。");
            return;
        }
        Town target = core.getTownByName(joinArgs(args, 1));
        if (target == null) {
            send(sender, "目标城镇不存在。");
            return;
        }
        if (target.getUuid().equals(source.getUuid())) {
            send(sender, "不能与自己合并。");
            return;
        }

        String[] pending = pendingMerges.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (pending != null && source.getUuid().equals(pending[0]) && target.getUuid().equals(pending[1])
                && now - Long.parseLong(pending[2]) <= DELETE_TIMEOUT_MS) {
            pendingMerges.remove(player.getUniqueId());
            send(sender, townService.mergeTowns(resident, source, target).getMessage());
            return;
        }
        pendingMerges.put(player.getUniqueId(), new String[]{source.getUuid(), target.getUuid(), String.valueOf(now)});
        send(sender, ChatColor.RED + "警告：合并后城镇 " + source.getName() + " 将被删除，资金与领地并入 " + target.getName() + "。");
        send(sender, ChatColor.RED + "请在 30 秒内再次输入 /town merge " + target.getName() + " 确认。");
    }

    private void split(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "geotowny.town.split")) {
            return;
        }
        if (args.length < 2) {
            send(sender, "用法: /town split <新城镇名>");
            return;
        }
        Resident resident = resident(player);
        Town town = townOf(resident);
        if (town == null) {
            send(sender, "你不在任何城镇中。");
            return;
        }
        Location location = player.getLocation();
        WorldLocation spawn = new WorldLocation(worlds.toInternalKey(location.getWorld()),
                location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        Result result = townService.splitTown(resident, town, joinArgs(args, 1),
                worlds.toInternalKey(location.getWorld()), location.getChunk().getX(),
                location.getChunk().getZ(), spawn);
        send(sender, result.getMessage());
    }

    private void logs(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        Resident resident = resident(player);
        Town town = townOf(resident);
        if (town == null) {
            send(sender, "你不在任何城镇中。");
            return;
        }
        if (resident.getTownRank() != com.geotowny.domain.model.TownRank.MAYOR
                && resident.getTownRank() != com.geotowny.domain.model.TownRank.ASSISTANT) {
            send(sender, "只有市长或助理可以查看城镇日志。");
            return;
        }
        int page = 1;
        if (args.length > 1) {
            try {
                page = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException e) {
                send(sender, "页码必须是整数。");
                return;
            }
        }
        int total = logService.count(town.getUuid(), null, null, 0);
        List<TownLog> entries = logService.query(town.getUuid(), page, null, null, 0,
                TownLogService.DEFAULT_PAGE_SIZE);
        send(sender, "=== " + town.getName() + " 日志 第 " + page + " 页 / 共 " + total + " 条 ===");
        if (entries.isEmpty()) {
            send(sender, "没有日志记录。");
            return;
        }
        entries.forEach(log -> send(sender, log.format()));
    }

    private void setBorder(CommandSender sender, Resident resident, Town town, String[] args) {
        if (!townService.isMayor(resident, town)) {
            send(sender, "只有市长可以修改边界粒子设置。");
            return;
        }
        if (args.length < 3) {
            send(sender, "用法: /town set border <on|off|color|height|interval> [值]");
            return;
        }
        String option = args[2].toLowerCase(Locale.ROOT);
        switch (option) {
            case "on" -> town.setBorderEnabled(true);
            case "off" -> town.setBorderEnabled(false);
            case "color" -> {
                if (args.length < 4) {
                    send(sender, "用法: /town set border color <#RRGGBB>");
                    return;
                }
                String hex = args[3].replace("#", "");
                try {
                    town.setBorderColor(Integer.parseInt(hex, 16));
                } catch (NumberFormatException e) {
                    send(sender, "颜色格式无效，请使用 #RRGGBB。");
                    return;
                }
            }
            case "height" -> {
                if (args.length < 4) {
                    send(sender, "用法: /town set border height <整数>");
                    return;
                }
                try {
                    town.setBorderHeight(Integer.parseInt(args[3]));
                } catch (NumberFormatException e) {
                    send(sender, "高度必须是整数。");
                    return;
                }
            }
            case "interval" -> {
                if (args.length < 4) {
                    send(sender, "用法: /town set border interval <0.1~1.0>");
                    return;
                }
                try {
                    town.setBorderParticleInterval(Double.parseDouble(args[3]));
                } catch (NumberFormatException e) {
                    send(sender, "间隔必须是数字。");
                    return;
                }
            }
            default -> {
                send(sender, "未知边界设置项: " + option);
                return;
            }
        }
        core.saveTown(town);
        send(sender, "边界粒子设置已更新（渲染在 P4 接入）。");
    }

    private void help(CommandSender sender, String label) {
        send(sender, ChatColor.GOLD + "=== 城镇命令 ===");
        send(sender, "/" + label + " new <名称> | info [名称] | list | members");
        send(sender, "/" + label + " claim | unclaim | spawn [名称]");
        send(sender, "/" + label + " invite <玩家> | accept | decline | join <城镇> | leave | kick <玩家>");
        send(sender, "/" + label + " deposit <金额> | withdraw <金额>");
        send(sender, "/" + label + " delete | rename <名称> | merge <城镇> | split <名称>");
        send(sender, "/" + label + " logs [页码]");
        send(sender, "/" + label + " set board|taxes|open|public|mayor|spawn|code|border|plotprice ...");
    }

    private Resident resident(Player player) {
        return core.getOrCreateResident(player.getUniqueId(), player.getName());
    }

    private Town townOf(Resident resident) {
        return resident == null || !resident.hasTown() ? null : core.getTown(resident.getTownUuid());
    }

    private String nameOf(String uuid) {
        Resident resident = core.getResident(uuid);
        return resident == null ? "?" : resident.getName();
    }

    private String nameOfNation(String uuid) {
        var nation = core.getNation(uuid);
        return nation == null ? "?" : nation.getName();
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        send(sender, "只有玩家可以执行该命令。");
        return null;
    }

    private boolean require(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            send(sender, "你没有权限执行该操作。");
            return false;
        }
        return true;
    }

    private Money parseAmount(CommandSender sender, String[] args) {
        return parseAmount(sender, args, 1);
    }

    private Money parseAmount(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            send(sender, "请提供金额。");
            return null;
        }
        try {
            Money amount = Money.parse(args[index]);
            if (!amount.isPositive()) {
                send(sender, "金额必须为正数。");
                return null;
            }
            return amount;
        } catch (RuntimeException e) {
            send(sender, "金额格式无效: " + args[index]);
            return null;
        }
    }

    private boolean parseBoolean(CommandSender sender, String value) {
        return "true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
    }

    private static String joinArgs(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length));
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
            if (sub.equals("info") || sub.equals("join") || sub.equals("spawn") || sub.equals("merge")) {
                return filter(core.allTowns().stream().map(Town::getName).toList(), args[1]);
            }
            if (sub.equals("invite") || sub.equals("kick") || sub.equals("set")) {
                if (sub.equals("set")) {
                    return filter(SET_OPTIONS, args[1]);
                }
                return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            if (args[1].equalsIgnoreCase("mayor")) {
                return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
            }
            if (args[1].equalsIgnoreCase("open") || args[1].equalsIgnoreCase("public")) {
                return filter(List.of("true", "false"), args[2]);
            }
            if (args[1].equalsIgnoreCase("border")) {
                return filter(List.of("on", "off", "color", "height", "interval"), args[2]);
            }
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("set") && args[1].equalsIgnoreCase("border")) {
            return filter(List.of("on", "off", "color", "height", "interval"), args[3]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}