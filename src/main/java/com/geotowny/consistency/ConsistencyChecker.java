package com.geotowny.consistency;

import com.geotowny.domain.model.Nation;
import com.geotowny.domain.model.NationRank;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.Town;
import com.geotowny.domain.model.TownBlock;
import com.geotowny.domain.model.TownRank;
import com.geotowny.domain.money.Money;
import com.geotowny.storage.DataSnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据一致性检查与修复。
 * <p>覆盖：孤儿领地、claimKeys 双向不一致、居民归属、市长、国家归属、
 * 首都、国王 rank、负余额、重名。
 */
public final class ConsistencyChecker {

    public enum Severity {
        ERROR,
        WARN
    }

    public enum IssueType {
        ORPHAN_BLOCK,
        CLAIM_KEY_MISSING_IN_BLOCKS,
        CLAIM_KEY_MISSING_IN_TOWN,
        RESIDENT_TOWN_MISSING,
        MAYOR_MISSING,
        MAYOR_RANK_INCONSISTENT,
        NATION_MISSING,
        TOWN_NATION_INCONSISTENT,
        CAPITAL_MISSING,
        KING_MISSING,
        KING_RANK_INCONSISTENT,
        NEGATIVE_BALANCE,
        DUPLICATE_TOWN_NAME,
        DUPLICATE_NATION_NAME
    }

    public Report check(DataSnapshot snapshot) {
        List<Issue> issues = new ArrayList<>();
        Map<String, Town> towns = snapshot.towns();
        Map<String, Nation> nations = snapshot.nations();
        Map<String, Resident> residents = snapshot.residents();
        Map<String, TownBlock> blocks = snapshot.townBlocks();

        for (TownBlock block : blocks.values()) {
            if (block.getTownUuid() == null || !towns.containsKey(block.getTownUuid())) {
                issues.add(error(IssueType.ORPHAN_BLOCK, "领地没有有效城镇: " + block.key(), block.key()));
                continue;
            }
            Town town = towns.get(block.getTownUuid());
            if (!town.getClaimBlockKeys().contains(block.key())) {
                issues.add(warn(IssueType.CLAIM_KEY_MISSING_IN_TOWN,
                        "领地 " + block.key() + " 未记录在城镇 " + town.getName() + " 的 claimKeys 中", town.getUuid()));
            }
        }

        for (Town town : towns.values()) {
            for (String key : town.getClaimBlockKeys()) {
                TownBlock block = blocks.get(key);
                if (block == null) {
                    issues.add(warn(IssueType.CLAIM_KEY_MISSING_IN_BLOCKS,
                            "城镇 " + town.getName() + " 引用了不存在的领地 " + key, town.getUuid()));
                } else if (!town.getUuid().equals(block.getTownUuid())) {
                    issues.add(error(IssueType.CLAIM_KEY_MISSING_IN_BLOCKS,
                            "城镇 " + town.getName() + " 引用了属于其他城镇的领地 " + key, town.getUuid()));
                }
            }
            if (town.getMayorUuid() != null) {
                Resident mayor = residents.get(town.getMayorUuid());
                if (mayor == null) {
                    issues.add(error(IssueType.MAYOR_MISSING,
                            "城镇 " + town.getName() + " 的市长不存在: " + town.getMayorUuid(), town.getUuid()));
                } else {
                    if (!town.getUuid().equals(mayor.getTownUuid())) {
                        issues.add(error(IssueType.MAYOR_MISSING,
                                "城镇 " + town.getName() + " 的市长不属于该城镇", town.getUuid()));
                    }
                    if (mayor.getTownRank() != TownRank.MAYOR) {
                        issues.add(warn(IssueType.MAYOR_RANK_INCONSISTENT,
                                "城镇 " + town.getName() + " 的市长职位不是 MAYOR", town.getUuid()));
                    }
                }
            }
            if (town.getNationUuid() != null && !nations.containsKey(town.getNationUuid())) {
                issues.add(error(IssueType.NATION_MISSING,
                        "城镇 " + town.getName() + " 引用了不存在的国家", town.getUuid()));
            }
        }

        for (Resident resident : residents.values()) {
            if (resident.getTownUuid() != null && !towns.containsKey(resident.getTownUuid())) {
                issues.add(error(IssueType.RESIDENT_TOWN_MISSING,
                        "居民 " + resident.getName() + " 引用了不存在的城镇", resident.getUuidString()));
            }
            if (resident.getBalance().isNegative()) {
                issues.add(error(IssueType.NEGATIVE_BALANCE,
                        "居民 " + resident.getName() + " 余额为负: " + resident.getBalance().format(), resident.getUuidString()));
            }
        }

        Set<String> kingUuids = new HashSet<>();
        for (Nation nation : nations.values()) {
            if (nation.getKingUuid() != null) {
                kingUuids.add(nation.getKingUuid());
                Resident king = residents.get(nation.getKingUuid());
                if (king == null) {
                    issues.add(error(IssueType.KING_MISSING,
                            "国家 " + nation.getName() + " 的国王不存在: " + nation.getKingUuid(), nation.getUuid()));
                } else if (king.getNationRank() != NationRank.KING) {
                    issues.add(warn(IssueType.KING_RANK_INCONSISTENT,
                            "国家 " + nation.getName() + " 的国王职位不是 KING", nation.getUuid()));
                }
            }
            if (nation.getCapitalTownUuid() != null) {
                Town capital = towns.get(nation.getCapitalTownUuid());
                if (capital == null || !nation.getUuid().equals(capital.getNationUuid())) {
                    issues.add(error(IssueType.CAPITAL_MISSING,
                            "国家 " + nation.getName() + " 的首都无效", nation.getUuid()));
                }
            }
            for (String townUuid : nation.getTownUuids()) {
                Town town = towns.get(townUuid);
                if (town == null || !nation.getUuid().equals(town.getNationUuid())) {
                    issues.add(error(IssueType.TOWN_NATION_INCONSISTENT,
                            "国家 " + nation.getName() + " 的成员城镇列表包含无效城镇 " + townUuid, nation.getUuid()));
                }
            }
            for (Town town : towns.values()) {
                if (nation.getUuid().equals(town.getNationUuid()) && !nation.getTownUuids().contains(town.getUuid())) {
                    issues.add(warn(IssueType.TOWN_NATION_INCONSISTENT,
                            "城镇 " + town.getName() + " 属于国家 " + nation.getName() + "，但不在成员列表中",
                            nation.getUuid()));
                }
            }
        }

        for (Resident resident : residents.values()) {
            if (resident.getNationRank() == NationRank.KING && !kingUuids.contains(resident.getUuidString())) {
                issues.add(warn(IssueType.KING_RANK_INCONSISTENT,
                        "居民 " + resident.getName() + " 拥有 KING 职位，但不是任何国家的国王", resident.getUuidString()));
            }
        }

        addDuplicateNameIssues(issues, towns.values().stream().map(Town::getName).toList(),
                IssueType.DUPLICATE_TOWN_NAME, "城镇");
        addDuplicateNameIssues(issues, nations.values().stream().map(Nation::getName).toList(),
                IssueType.DUPLICATE_NATION_NAME, "国家");

        return new Report(List.copyOf(issues));
    }

    /**
     * 修复检查出的问题，返回修复后的快照与残留问题。
     * <p>注意：会直接修改传入快照中的可变模型对象。
     */
    public RepairResult repair(DataSnapshot snapshot) {
        Report before = check(snapshot);
        List<Issue> fixed = new ArrayList<>();
        Map<String, Town> towns = snapshot.towns();
        Map<String, Nation> nations = snapshot.nations();
        Map<String, Resident> residents = snapshot.residents();
        Map<String, TownBlock> blocks = new LinkedHashMap<>(snapshot.townBlocks());

        blocks.entrySet().removeIf(entry -> {
            TownBlock block = entry.getValue();
            boolean orphan = block.getTownUuid() == null || !towns.containsKey(block.getTownUuid());
            if (orphan) {
                fixed.add(warn(IssueType.ORPHAN_BLOCK, "已移除孤儿领地 " + block.key(), block.key()));
            }
            return orphan;
        });

        for (Town town : towns.values()) {
            List<String> keys = new ArrayList<>(town.getClaimBlockKeys());
            for (String key : keys) {
                TownBlock block = blocks.get(key);
                if (block == null || !town.getUuid().equals(block.getTownUuid())) {
                    town.getClaimBlockKeys().remove(key);
                    fixed.add(warn(IssueType.CLAIM_KEY_MISSING_IN_BLOCKS, "已从 " + town.getName() + " 移除失效 claimKey " + key, town.getUuid()));
                }
            }
            for (TownBlock block : blocks.values()) {
                if (town.getUuid().equals(block.getTownUuid()) && !town.getClaimBlockKeys().contains(block.key())) {
                    town.getClaimBlockKeys().add(block.key());
                    fixed.add(warn(IssueType.CLAIM_KEY_MISSING_IN_TOWN, "已为 " + town.getName() + " 补回 claimKey " + block.key(), town.getUuid()));
                }
            }
        }

        for (Resident resident : residents.values()) {
            if (resident.getTownUuid() != null && !towns.containsKey(resident.getTownUuid())) {
                resident.leaveTown();
                fixed.add(error(IssueType.RESIDENT_TOWN_MISSING, "已清除居民 " + resident.getName() + " 的失效城镇归属", resident.getUuidString()));
            }
        }

        for (Town town : towns.values()) {
            if (town.getMayorUuid() == null) {
                continue;
            }
            Resident mayor = residents.get(town.getMayorUuid());
            if (mayor == null || !town.getUuid().equals(mayor.getTownUuid())) {
                town.setMayorUuid(null);
                fixed.add(error(IssueType.MAYOR_MISSING, "已清空城镇 " + town.getName() + " 的无效市长", town.getUuid()));
            } else if (mayor.getTownRank() != TownRank.MAYOR) {
                mayor.setTownRank(TownRank.MAYOR);
                fixed.add(warn(IssueType.MAYOR_RANK_INCONSISTENT, "已恢复市长 " + mayor.getName() + " 的 MAYOR 职位", town.getUuid()));
            }
        }

        for (Town town : towns.values()) {
            if (town.getNationUuid() != null && !nations.containsKey(town.getNationUuid())) {
                town.setNationUuid(null);
                town.setCapital(false);
                fixed.add(error(IssueType.NATION_MISSING, "已清除城镇 " + town.getName() + " 的失效国家归属", town.getUuid()));
            }
        }

        for (Nation nation : nations.values()) {
            List<String> members = new ArrayList<>(nation.getTownUuids());
            for (String townUuid : members) {
                Town town = towns.get(townUuid);
                if (town == null || !nation.getUuid().equals(town.getNationUuid())) {
                    nation.getTownUuids().remove(townUuid);
                    fixed.add(error(IssueType.TOWN_NATION_INCONSISTENT, "已从国家 " + nation.getName() + " 移除无效城镇", nation.getUuid()));
                }
            }
            for (Town town : towns.values()) {
                if (nation.getUuid().equals(town.getNationUuid()) && !nation.getTownUuids().contains(town.getUuid())) {
                    nation.getTownUuids().add(town.getUuid());
                    fixed.add(warn(IssueType.TOWN_NATION_INCONSISTENT, "已把城镇 " + town.getName() + " 补入国家 " + nation.getName(), nation.getUuid()));
                }
            }
            if (nation.getCapitalTownUuid() != null) {
                Town capital = towns.get(nation.getCapitalTownUuid());
                if (capital == null || !nation.getUuid().equals(capital.getNationUuid())) {
                    nation.setCapitalTownUuid(null);
                    if (capital != null) {
                        capital.setCapital(false);
                    }
                    fixed.add(error(IssueType.CAPITAL_MISSING, "已清空国家 " + nation.getName() + " 的无效首都", nation.getUuid()));
                }
            }
            if (nation.getKingUuid() != null) {
                Resident king = residents.get(nation.getKingUuid());
                if (king == null) {
                    nation.setKingUuid(null);
                    fixed.add(error(IssueType.KING_MISSING, "已清空国家 " + nation.getName() + " 的无效国王", nation.getUuid()));
                } else if (king.getNationRank() != NationRank.KING) {
                    king.setNationRank(NationRank.KING);
                    fixed.add(warn(IssueType.KING_RANK_INCONSISTENT, "已恢复 " + king.getName() + " 的 KING 职位", nation.getUuid()));
                }
            }
        }

        Set<String> validKings = nations.values().stream()
                .map(Nation::getKingUuid)
                .filter(uuid -> uuid != null)
                .collect(Collectors.toSet());
        for (Resident resident : residents.values()) {
            if (resident.getNationRank() == NationRank.KING && !validKings.contains(resident.getUuidString())) {
                resident.setNationRank(null);
                fixed.add(warn(IssueType.KING_RANK_INCONSISTENT, "已清除 " + resident.getName() + " 的悬空 KING 职位", resident.getUuidString()));
            }
        }

        for (Resident resident : residents.values()) {
            if (resident.getBalance().isNegative()) {
                resident.setBalance(Money.ZERO);
                fixed.add(error(IssueType.NEGATIVE_BALANCE, "已把居民 " + resident.getName() + " 的负余额归零", resident.getUuidString()));
            }
        }
        for (Town town : towns.values()) {
            if (town.getBalance().isNegative()) {
                town.setBalance(Money.ZERO);
                fixed.add(error(IssueType.NEGATIVE_BALANCE, "已把城镇 " + town.getName() + " 的负余额归零", town.getUuid()));
            }
        }
        for (Nation nation : nations.values()) {
            if (nation.getBalance().isNegative()) {
                nation.setBalance(Money.ZERO);
                fixed.add(error(IssueType.NEGATIVE_BALANCE, "已把国家 " + nation.getName() + " 的负余额归零", nation.getUuid()));
            }
        }

        DataSnapshot repaired = new DataSnapshot(towns, nations, residents, blocks, snapshot.alliances());
        return new RepairResult(repaired, before, List.copyOf(fixed), check(repaired));
    }

    private static void addDuplicateNameIssues(List<Issue> issues, List<String> names, IssueType type, String label) {
        Map<String, Long> counts = names.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.groupingBy(name -> name, Collectors.counting()));
        counts.forEach((name, count) -> {
            if (count > 1) {
                issues.add(warn(type, label + "名称重复: " + name + " (" + count + ")", name));
            }
        });
    }

    private static Issue error(IssueType type, String message, String refId) {
        return new Issue(Severity.ERROR, type, message, refId);
    }

    private static Issue warn(IssueType type, String message, String refId) {
        return new Issue(Severity.WARN, type, message, refId);
    }

    public record Issue(Severity severity, IssueType type, String message, String refId) {
    }

    public record Report(List<Issue> issues) {
        public long errorCount() {
            return issues.stream().filter(issue -> issue.severity() == Severity.ERROR).count();
        }

        public long warnCount() {
            return issues.stream().filter(issue -> issue.severity() == Severity.WARN).count();
        }

        public boolean hasErrors() {
            return errorCount() > 0;
        }

        public String summary() {
            return "共 " + issues.size() + " 项（错误 " + errorCount() + "，警告 " + warnCount() + "）";
        }
    }

    public record RepairResult(
            DataSnapshot snapshot,
            Report before,
            List<Issue> fixed,
            Report residual
    ) {
        public String summary() {
            return "检查 " + before.summary() + "，修复 " + fixed.size() + " 项，残留 " + residual.summary();
        }
    }
}