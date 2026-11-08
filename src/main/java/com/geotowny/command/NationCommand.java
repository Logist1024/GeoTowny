package com.geotowny.command;

import com.geotowny.GeoTownyPlugin;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.Ideology;
import com.geotowny.domain.model.Nation;
import com.geotowny.domain.model.RelationType;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.Town;
import com.geotowny.domain.model.WorldLocation;
import com.geotowny.domain.money.Money;
import com.geotowny.domain.war.Campaign;
import com.geotowny.service.IdeologyService;
import com.geotowny.service.InfluenceService;
import com.geotowny.service.NationService;
import com.geotowny.service.WarService;
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

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /nation 命令（P3 核心子命令）。
 * <p>已实现：new/info/list/add/kick/leave/deposit/withdraw/set/ally/enemy/peace/war/relation/rename/spawn。
 * <p>战役、侵略、附庸、吞并在 P6 接入。
 */
public final class NationCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "new", "info", "list", "add", "kick", "leave", "deposit", "withdraw",
            "set", "ally", "enemy", "peace", "war", "relation", "rename", "spawn",
            "campaign", "invade", "siege", "surrender", "restore", "warvote",
            "impeach", "impeachvote", "annex", "influence");

    private static final List<String> SET_OPTIONS = List.of(
            "king", "capital", "taxes", "board", "ideology", "code");

    private final GeoTownyPlugin plugin;
    private final GeoTownyCore core;
    private final NationService nationService;
    private final WarService warService;
    private final IdeologyService ideologyService;
    private final InfluenceService influenceService;
    private final WorldRegistry worlds;

    public NationCommand(GeoTownyPlugin plugin, GeoTownyCore core, NationService nationService,
                         WarService warService, IdeologyService ideologyService,
                         InfluenceService influenceService, WorldRegistry worlds) {
        this.plugin = plugin;
        this.core = core;
        this.nationService = nationService;
        this.warService = warService;
        this.ideologyService = ideologyService;
        this.influenceService = influenceService;
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
            case "new" -> newNation(sender, args);
            case "info" -> info(sender, args.length > 1 ? joinArgs(args, 1) : null);
            case "list" -> list(sender);
            case "add" -> addTown(sender, args);
            case "kick" -> kickTown(sender, args);
            case "leave" -> leaveNation(sender);
            case "deposit" -> deposit(sender, args);
            case "withdraw" -> withdraw(sender, args);
            case "set" -> set(sender, args);
            case "ally" -> setRelation(sender, args, RelationType.ALLY);
            case "enemy" -> setRelation(sender, args, RelationType.HOSTILE);
            case "peace" -> peace(sender, args);
            case "war" -> war(sender, args);
            case "relation" -> relation(sender, args);
            case "rename" -> rename(sender, args);
            case "spawn" -> spawn(sender, args.length > 1 ? joinArgs(args, 1) : null);
            case "campaign" -> campaign(sender, args, null);
            case "invade" -> campaign(sender, args, Campaign.Type.INVASION);
            case "siege" -> campaign(sender, args, Campaign.Type.SIEGE);
            case "surrender" -> surrender(sender, args);
            case "restore" -> restore(sender, args);
            case "warvote" -> warVote(sender, args);
            case "impeach" -> impeach(sender);
            case "impeachvote" -> impeachVote(sender, args);
            case "annex" -> annex(sender, args);
            case "influence" -> influence(sender);
            default -> help(sender, label);
        }
        return true;
    }

    private void newNation(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "geotowny.nation.create")) {
            return;
        }
        if (args.length < 2) {
            send(sender, "用法: /nation new <名称>");
            return;
        }
        Resident resident = resident(player);
        Town town = townOf(resident);
        if (town == null) {
            send(sender, "你必须是城镇市长才能建国。");
            return;
        }
        Result result = nationService.createNation(resident, town, joinArgs(args, 1));
        send(sender, result.getMessage());
    }

    private void info(CommandSender sender, String name) {
        Nation nation;
        if (name != null) {
            nation = core.getNationByName(name);
        } else if (sender instanceof Player player) {
            nation = nationOf(resident(player));
        } else {
            send(sender, "控制台请使用 /nation info <名称>。");
            return;
        }
        if (nation == null) {
            send(sender, "国家不存在。");
            return;
        }
        send(sender, ChatColor.GOLD + "=== " + nation.getName() + " [" + nation.getCode() + "] ===");
        send(sender, "国体: " + nation.getIdeology().getDisplayName()
                + " | 领袖: " + nameOf(nation.getKingUuid())
                + " | 首都: " + nameOfTown(nation.getCapitalTownUuid()));
        send(sender, "城镇: " + nation.getTownCount()
                + " | 国库: " + nation.getBalance().format() + " " + plugin.config().currencyName()
                + " | 国家税: " + nation.getTaxes().format());
        send(sender, "影响力: " + String.format("%.1f", nation.getInfluence())
                + " | 稳定度: " + String.format("%.1f", nation.getStability())
                + " | 战争状态: " + nation.getWarState());
        send(sender, "联盟: " + (nation.hasAlliance() ? nation.getAllianceUuid() : "无"));
        if (nation.getBoard() != null && !nation.getBoard().isBlank()) {
            send(sender, "公告: " + nation.getBoard());
        }
    }

    private void list(CommandSender sender) {
        List<Nation> nations = List.copyOf(core.allNations());
        if (nations.isEmpty()) {
            send(sender, "当前没有国家。");
            return;
        }
        send(sender, "=== 国家列表 (" + nations.size() + ") ===");
        nations.stream().limit(20).forEach(nation -> send(sender,
                " - " + nation.getName() + " [" + nation.getCode() + "] | 城镇 "
                        + nation.getTownCount() + " | 国体 " + nation.getIdeology().getDisplayName()));
    }

    private void addTown(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "geotowny.nation.set")) {
            return;
        }
        if (args.length < 2) {
            send(sender, "用法: /nation add <城镇名>");
            return;
        }
        Resident resident = resident(player);
        Nation nation = requireKing(sender, resident);
        if (nation == null) {
            return;
        }
        Town town = core.getTownByName(joinArgs(args, 1));
        send(sender, nationService.addTown(resident, nation, town).getMessage());
    }

    private void kickTown(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "geotowny.nation.set")) {
            return;
        }
        if (args.length < 2) {
            send(sender, "用法: /nation kick <城镇名>");
            return;
        }
        Resident resident = resident(player);
        Nation nation = requireKing(sender, resident);
        if (nation == null) {
            return;
        }
        Town town = core.getTownByName(joinArgs(args, 1));
        send(sender, nationService.removeTown(resident, nation, town, true).getMessage());
    }

    private void leaveNation(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        Resident resident = resident(player);
        Town town = townOf(resident);
        if (town == null || !town.hasNation()) {
            send(sender, "你的城镇不属于任何国家。");
            return;
        }
        Nation nation = core.getNation(town.getNationUuid());
        send(sender, nationService.removeTown(resident, nation, town, false).getMessage());
    }

    private void deposit(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "geotowny.nation.set")) {
            return;
        }
        Money amount = parseAmount(sender, args);
        if (amount == null) {
            return;
        }
        Resident resident = resident(player);
        Nation nation = nationOf(resident);
        if (nation == null) {
            send(sender, "你不属于任何国家。");
            return;
        }
        send(sender, nationService.deposit(resident, nation, amount).getMessage());
    }

    private void withdraw(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "geotowny.nation.set")) {
            return;
        }
        Money amount = parseAmount(sender, args);
        if (amount == null) {
            return;
        }
        Resident resident = resident(player);
        Nation nation = nationOf(resident);
        if (nation == null) {
            send(sender, "你不属于任何国家。");
            return;
        }
        send(sender, nationService.withdraw(resident, nation, amount).getMessage());
    }

    private void set(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "geotowny.nation.set")) {
            return;
        }
        if (args.length < 3) {
            send(sender, "用法: /nation set <king|capital|taxes|board|ideology|code> <值>");
            return;
        }
        Resident resident = resident(player);
        Nation nation = requireKing(sender, resident);
        if (nation == null) {
            return;
        }
        String option = args[1].toLowerCase(Locale.ROOT);
        switch (option) {
            case "king" -> {
                Resident target = core.getResidentByName(args[2]);
                send(sender, nationService.setKing(resident, nation, target).getMessage());
            }
            case "capital" -> {
                Town town = core.getTownByName(joinArgs(args, 2));
                send(sender, nationService.setCapital(resident, nation, town).getMessage());
            }
            case "taxes" -> {
                Money amount = parseAmount(sender, args, 2);
                if (amount != null) {
                    send(sender, nationService.setTaxes(resident, nation, amount).getMessage());
                }
            }
            case "board" -> send(sender, nationService.setBoard(resident, nation, joinArgs(args, 2)).getMessage());
            case "ideology" -> {
                try {
                    Ideology ideology = Ideology.valueOf(args[2].toUpperCase(Locale.ROOT));
                    send(sender, nationService.setIdeology(resident, nation, ideology).getMessage());
                } catch (IllegalArgumentException e) {
                    send(sender, "未知国体: " + args[2]);
                }
            }
            case "code" -> send(sender, nationService.setCode(resident, nation, args[2]).getMessage());
            default -> send(sender, "未知设置项: " + option);
        }
    }

    private void setRelation(CommandSender sender, String[] args, RelationType type) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "geotowny.nation.diplomacy")) {
            return;
        }
        if (args.length < 2) {
            send(sender, "用法: /nation " + args[0] + " <国家名>");
            return;
        }
        Resident resident = resident(player);
        Nation nation = requireKing(sender, resident);
        if (nation == null) {
            return;
        }
        Nation target = core.getNationByName(joinArgs(args, 1));
        send(sender, nationService.setRelation(resident, nation, target, type).getMessage());
    }

    private void relation(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "geotowny.nation.diplomacy")) {
            return;
        }
        if (args.length < 3) {
            send(sender, "用法: /nation relation <国家> <ALLY|FRIENDLY|NEUTRAL|HOSTILE|TRADE_PACT|NON_AGGRESSION>");
            return;
        }
        RelationType type;
        try {
            type = RelationType.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            send(sender, "未知关系类型: " + args[2]);
            return;
        }
        Resident resident = resident(player);
        Nation nation = requireKing(sender, resident);
        if (nation == null) {
            return;
        }
        Nation target = core.getNationByName(joinArgs(args, 1));
        send(sender, nationService.setRelation(resident, nation, target, type).getMessage());
    }

    private void peace(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "geotowny.nation.war")) {
            return;
        }
        if (args.length < 2) {
            send(sender, "用法: /nation peace <国家名>");
            return;
        }
        Resident resident = resident(player);
        Nation nation = requireKing(sender, resident);
        if (nation == null) {
            return;
        }
        Nation target = core.getNationByName(joinArgs(args, 1));
        send(sender, warService.makePeace(resident, nation, target).getMessage());
    }

    private void war(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "geotowny.nation.war")) {
            return;
        }
        if (args.length < 2) {
            send(sender, "用法: /nation war <国家名>");
            return;
        }
        Resident resident = resident(player);
        Nation nation = requireKing(sender, resident);
        if (nation == null) {
            return;
        }
        Nation target = core.getNationByName(joinArgs(args, 1));
        send(sender, warService.declareWar(resident, nation, target).getMessage());
    }

    private void campaign(CommandSender sender, String[] args, Campaign.Type forcedType) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "geotowny.nation.war")) {
            return;
        }
        if (args.length < 2) {
            send(sender, "用法: /nation campaign <城镇> [invasion|siege]");
            return;
        }
        Campaign.Type type = forcedType;
        if (type == null && args.length > 2) {
            type = args[2].equalsIgnoreCase("siege") ? Campaign.Type.SIEGE : Campaign.Type.INVASION;
        }
        if (type == null) {
            type = Campaign.Type.INVASION;
        }
        Town target = core.getTownByName(args[1]);
        if (target == null) {
            send(sender, "城镇不存在: " + args[1]);
            return;
        }
        Resident resident = resident(player);
        send(sender, warService.startCampaign(player, resident, target, type).getMessage());
    }

    private void surrender(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "geotowny.nation.war")) {
            return;
        }
        if (args.length < 2) {
            send(sender, "用法: /nation surrender <国家> [割让城镇] [赔款]");
            return;
        }
        Resident resident = resident(player);
        Nation loser = nationOf(resident);
        if (loser == null) {
            send(sender, "你不属于任何国家。");
            return;
        }
        Nation winner = core.getNationByName(args[1]);
        Town cede = args.length > 2 ? core.getTownByName(args[2]) : null;
        Money reparations = Money.ZERO;
        if (args.length > 3) {
            try {
                reparations = Money.parse(args[3]);
            } catch (RuntimeException e) {
                send(sender, "赔款格式无效。");
                return;
            }
        }
        send(sender, warService.surrender(resident, loser, winner, cede, reparations).getMessage());
    }

    private void restore(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "geotowny.nation.war")) {
            return;
        }
        if (args.length < 2) {
            send(sender, "用法: /nation restore <国家>");
            return;
        }
        Nation nation = core.getNationByName(joinArgs(args, 1));
        if (nation == null) {
            send(sender, "国家不存在。");
            return;
        }
        send(sender, warService.restoreNation(resident(player), nation.getUuid()).getMessage());
    }

    private void warVote(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "geotowny.nation.war")) {
            return;
        }
        if (args.length < 3) {
            send(sender, "用法: /nation warvote <国家> yes|no");
            return;
        }
        Resident resident = resident(player);
        Nation nation = nationOf(resident);
        Nation target = core.getNationByName(args[1]);
        boolean approve = args[2].equalsIgnoreCase("yes") || args[2].equalsIgnoreCase("true");
        send(sender, warService.castWarVote(resident, nation, target, approve).getMessage());
    }

    private void impeach(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "geotowny.nation.war")) {
            return;
        }
        Resident resident = resident(player);
        Nation nation = nationOf(resident);
        send(sender, ideologyService.startImpeachment(resident, nation).getMessage());
    }

    private void impeachVote(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "geotowny.nation.war")) {
            return;
        }
        if (args.length < 2) {
            send(sender, "用法: /nation impeachvote yes|no");
            return;
        }
        Resident resident = resident(player);
        Nation nation = nationOf(resident);
        boolean approve = args[1].equalsIgnoreCase("yes") || args[1].equalsIgnoreCase("true");
        send(sender, ideologyService.castImpeachmentVote(resident, nation, approve).getMessage());
    }

    private void annex(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "geotowny.nation.war")) {
            return;
        }
        if (args.length < 2) {
            send(sender, "用法: /nation annex <国家>");
            return;
        }
        Resident resident = resident(player);
        Nation overlord = requireKing(sender, resident);
        if (overlord == null) {
            return;
        }
        Nation defeated = core.getNationByName(joinArgs(args, 1));
        send(sender, warService.annexNation(resident, overlord, defeated).getMessage());
    }

    private void influence(CommandSender sender) {
        send(sender, ChatColor.GOLD + "=== 影响力排行 ===");
        List<Nation> nations = influenceService.rankNations();
        int shown = 0;
        for (Nation nation : nations) {
            send(sender, " - " + nation.getName() + " | " + String.format("%.1f", nation.getInfluence())
                    + " | 稳定度 " + String.format("%.1f", nation.getStability()));
            if (++shown >= 20) {
                break;
            }
        }
    }

    private void rename(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !require(sender, "geotowny.nation.set")) {
            return;
        }
        if (args.length < 2) {
            send(sender, "用法: /nation rename <新名称>");
            return;
        }
        Resident resident = resident(player);
        Nation nation = requireKing(sender, resident);
        if (nation == null) {
            return;
        }
        send(sender, nationService.renameNation(resident, nation, joinArgs(args, 1)).getMessage());
    }

    private void spawn(CommandSender sender, String name) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        Resident resident = resident(player);
        Nation nation = name == null ? nationOf(resident) : core.getNationByName(name);
        if (nation == null) {
            send(sender, "国家不存在。");
            return;
        }
        Town capital = core.getTown(nation.getCapitalTownUuid());
        if (capital == null || capital.getSpawn() == null) {
            send(sender, "该国尚未设置首都出生点。");
            return;
        }
        WorldLocation location = capital.getSpawn();
        World world = worlds.toWorld(location.getWorldKey());
        if (world == null) {
            send(sender, "首都所在世界未加载。");
            return;
        }
        player.teleport(new Location(world, location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch()));
        send(sender, "已传送到 " + capital.getName() + "（" + nation.getName() + " 首都）。");
    }

    private void help(CommandSender sender, String label) {
        send(sender, ChatColor.GOLD + "=== 国家命令 ===");
        send(sender, "/" + label + " new <名称> | info [名称] | list");
        send(sender, "/" + label + " add <城镇> | kick <城镇> | leave | spawn [名称]");
        send(sender, "/" + label + " deposit <金额> | withdraw <金额>");
        send(sender, "/" + label + " ally|enemy|peace|war|relation <国家> [类型]");
        send(sender, "/" + label + " campaign <城镇> [invasion|siege] | invade|siege <城镇>");
        send(sender, "/" + label + " surrender <国家> [城镇] [赔款] | restore <国家>");
        send(sender, "/" + label + " warvote <国家> yes|no | impeach | impeachvote yes|no");
        send(sender, "/" + label + " annex <国家> | influence");
        send(sender, "/" + label + " set king|capital|taxes|board|ideology|code ...");
        send(sender, "/" + label + " rename <名称>");
    }

    private Resident resident(Player player) {
        return core.getOrCreateResident(player.getUniqueId(), player.getName());
    }

    private Town townOf(Resident resident) {
        return resident == null || !resident.hasTown() ? null : core.getTown(resident.getTownUuid());
    }

    private Nation nationOf(Resident resident) {
        Town town = townOf(resident);
        return town == null || !town.hasNation() ? null : core.getNation(town.getNationUuid());
    }

    private Nation requireKing(CommandSender sender, Resident resident) {
        Nation nation = nationOf(resident);
        if (nation == null) {
            send(sender, "你不属于任何国家。");
            return null;
        }
        if (!nationService.isKing(resident, nation)) {
            send(sender, "只有" + nation.getIdeology().getLeaderTitle() + "可以执行该操作。");
            return null;
        }
        return nation;
    }

    private String nameOf(String uuid) {
        Resident resident = core.getResident(uuid);
        return resident == null ? "?" : resident.getName();
    }

    private String nameOfTown(String uuid) {
        Town town = core.getTown(uuid);
        return town == null ? "?" : town.getName();
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
            if (sub.equals("info") || sub.equals("ally") || sub.equals("enemy")
                    || sub.equals("peace") || sub.equals("war") || sub.equals("relation")
                    || sub.equals("spawn")) {
                return filter(core.allNations().stream().map(Nation::getName).toList(), args[1]);
            }
            if (sub.equals("add") || sub.equals("kick")
                    || sub.equals("campaign") || sub.equals("invade") || sub.equals("siege")) {
                return filter(core.allTowns().stream().map(Town::getName).toList(), args[1]);
            }
            if (sub.equals("surrender") || sub.equals("restore") || sub.equals("warvote")
                    || sub.equals("annex")) {
                return filter(core.allNations().stream().map(Nation::getName).toList(), args[1]);
            }
            if (sub.equals("set")) {
                return filter(SET_OPTIONS, args[1]);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return switch (args[1].toLowerCase(Locale.ROOT)) {
                case "king" -> filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
                case "capital" -> filter(core.allTowns().stream().map(Town::getName).toList(), args[2]);
                case "ideology" -> filter(Arrays.stream(Ideology.values()).map(Enum::name).toList(), args[2]);
                default -> List.of();
            };
        }
        return List.of();
    }

    private static List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}