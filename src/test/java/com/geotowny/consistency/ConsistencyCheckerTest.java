package com.geotowny.consistency;

import com.geotowny.consistency.ConsistencyChecker.IssueType;
import com.geotowny.consistency.ConsistencyChecker.RepairResult;
import com.geotowny.consistency.ConsistencyChecker.Report;
import com.geotowny.domain.model.Nation;
import com.geotowny.domain.model.NationRank;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.Town;
import com.geotowny.domain.model.TownBlock;
import com.geotowny.domain.model.TownRank;
import com.geotowny.domain.money.Money;
import com.geotowny.storage.DataSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConsistencyCheckerTest {

    private final ConsistencyChecker checker = new ConsistencyChecker();

    private DataSnapshot brokenSnapshot() {
        Town town = new Town();
        town.setUuid("t1");
        town.setName("柏林");
        town.setMayorUuid("u-mayor");
        town.setNationUuid("n1");
        town.getClaimBlockKeys().add("w#0#0");

        Nation nation = new Nation();
        nation.setUuid("n1");
        nation.setName("德意志");
        nation.setCapitalTownUuid("missing-town");
        nation.setKingUuid("u-king");
        nation.getTownUuids().add("t1");
        nation.getTownUuids().add("missing-town");

        Resident mayor = new Resident(UUID.randomUUID(), "Mayor", Money.of(10.0));
        mayor.setUuidString("u-mayor");
        mayor.setTownUuid("t1");
        mayor.setTownRank(TownRank.RESIDENT);

        Resident king = new Resident(UUID.randomUUID(), "King", Money.of(10.0));
        king.setUuidString("u-king");
        king.setTownUuid("t1");
        king.setNationRank(NationRank.CITIZEN);

        Resident orphan = new Resident(UUID.randomUUID(), "Orphan", Money.ofCents(-500));
        orphan.setUuidString("u-orphan");
        orphan.setTownUuid("missing-town");

        Resident fakeKing = new Resident(UUID.randomUUID(), "FakeKing", Money.of(1.0));
        fakeKing.setUuidString("u-fake-king");
        fakeKing.setNationRank(NationRank.KING);

        TownBlock valid = new TownBlock("w", 0, 0, "t1");
        TownBlock orphanBlock = new TownBlock("w", 1, 1, "missing-town");

        return new DataSnapshot(
                Map.of("t1", town),
                Map.of("n1", nation),
                Map.of("u-mayor", mayor, "u-king", king, "u-orphan", orphan, "u-fake-king", fakeKing),
                Map.of(valid.key(), valid, orphanBlock.key(), orphanBlock),
                Map.of());
    }

    @Test
    void detectsInconsistencies() {
        Report report = checker.check(brokenSnapshot());
        List<IssueType> types = report.issues().stream().map(ConsistencyChecker.Issue::type).toList();

        assertThat(types).contains(
                IssueType.ORPHAN_BLOCK,
                IssueType.MAYOR_RANK_INCONSISTENT,
                IssueType.CAPITAL_MISSING,
                IssueType.TOWN_NATION_INCONSISTENT,
                IssueType.RESIDENT_TOWN_MISSING,
                IssueType.NEGATIVE_BALANCE,
                IssueType.KING_RANK_INCONSISTENT);
        assertThat(report.errorCount()).isGreaterThan(0);
    }

    @Test
    void repairsInconsistencies() {
        RepairResult result = checker.repair(brokenSnapshot());

        assertThat(result.fixed()).isNotEmpty();
        assertThat(result.residual().errorCount()).isZero();

        DataSnapshot repaired = result.snapshot();
        assertThat(repaired.townBlocks()).hasSize(1);
        assertThat(repaired.townBlocks()).containsKey("w#0#0");

        Resident orphan = repaired.residents().get("u-orphan");
        assertThat(orphan.hasTown()).isFalse();
        assertThat(orphan.getBalance()).isEqualTo(Money.ZERO);

        Resident mayor = repaired.residents().get("u-mayor");
        assertThat(mayor.getTownRank()).isEqualTo(TownRank.MAYOR);

        Resident king = repaired.residents().get("u-king");
        assertThat(king.getNationRank()).isEqualTo(NationRank.KING);

        Resident fakeKing = repaired.residents().get("u-fake-king");
        assertThat(fakeKing.getNationRank()).isNull();

        Nation nation = repaired.nations().get("n1");
        assertThat(nation.getCapitalTownUuid()).isNull();
        assertThat(nation.getTownUuids()).containsExactly("t1");
    }
}