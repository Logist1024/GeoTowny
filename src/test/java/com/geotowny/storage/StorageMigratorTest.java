package com.geotowny.storage;

import com.geotowny.domain.model.Nation;
import com.geotowny.domain.model.PlotType;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.Town;
import com.geotowny.domain.model.TownBlock;
import com.geotowny.domain.money.Money;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StorageMigratorTest {

    @Test
    void normalizesLegacyPlotTypesAndDanglingReferences() {
        Town town = new Town();
        town.setUuid("t1");
        town.setName("柏林");
        town.setNationUuid("missing-nation");

        TownBlock block = new TownBlock("minecraft:overworld", 0, 0, "t1");
        block.setPlotType(PlotType.JAIL);

        Resident resident = new Resident(UUID.randomUUID(), "Alice", Money.of(10.0));
        resident.setTownUuid("missing-town");

        Nation nation = new Nation();
        nation.setUuid("n1");
        nation.setName("德意志");
        nation.setCapitalTownUuid("missing-capital");

        DataSnapshot snapshot = new DataSnapshot(
                Map.of("t1", town),
                Map.of("n1", nation),
                Map.of(resident.getUuidString(), resident),
                Map.of(block.key(), block),
                Map.of());

        StorageMigrator.NormalizationReport report = StorageMigrator.normalize(snapshot);

        assertThat(block.getPlotType()).isEqualTo(PlotType.DEFAULT);
        assertThat(resident.hasTown()).isFalse();
        assertThat(nation.getCapitalTownUuid()).isNull();
        assertThat(town.getNationUuid()).isNull();
        assertThat(report.plotTypesRemapped()).isEqualTo(1);
        assertThat(report.residentsDetachedFromMissingTowns()).isEqualTo(1);
        assertThat(report.capitalsCleared()).isEqualTo(1);
        assertThat(report.townsDetachedFromMissingNations()).isEqualTo(1);
    }

    @Test
    void reportsNoChangesForConsistentData() {
        Town town = new Town();
        town.setUuid("t1");
        town.setName("柏林");

        DataSnapshot snapshot = new DataSnapshot(
                Map.of("t1", town), Map.of(), Map.of(), Map.of(), Map.of());

        StorageMigrator.NormalizationReport report = StorageMigrator.normalize(snapshot);

        assertThat(report.hasChanges()).isFalse();
    }
}