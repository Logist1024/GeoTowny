package com.geotowny.service;

import com.geotowny.config.PluginConfig;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.Nation;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.Town;
import com.geotowny.domain.model.WorldLocation;
import com.geotowny.domain.money.Money;
import com.geotowny.storage.StorageService;
import com.geotowny.util.Result;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InfluenceServiceTest {

    @TempDir
    Path tempDir;

    private StorageService storage;
    private GeoTownyCore core;
    private Nation nation;
    private Town town;
    private InfluenceService influenceService;

    @BeforeEach
    void setUp() {
        PluginConfig config = new PluginConfig(new YamlConfiguration());
        storage = new StorageService(tempDir.resolve("plugin"));
        storage.start();
        core = new GeoTownyCore(storage, config);
        core.load();
        TownService townService = new TownService(core, config);
        NationService nationService = new NationService(core, config);

        Resident alice = core.getOrCreateResident(UUID.randomUUID(), "Alice");
        alice.deposit(Money.of(300_000));
        core.saveResident(alice);
        Result townResult = townService.createTown(alice, "A城", "minecraft:overworld", 0, 0,
                new WorldLocation("minecraft:overworld", 8, 64, 8, 0, 0));
        assertThat(townResult.isSuccess()).as(townResult.getMessage()).isTrue();
        town = core.getTown(alice.getTownUuid());
        Result nationResult = nationService.createNation(alice, town, "A国");
        assertThat(nationResult.isSuccess()).as(nationResult.getMessage()).isTrue();
        nation = core.getNationByName("A国");

        FestivalService festival = new FestivalService(config, () -> LocalDate.of(2026, 1, 6));
        influenceService = new InfluenceService(core, config, festival);
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    @Test
    void dailyGrowthIncreasesTownAndNationInfluence() {
        double beforeTown = town.getInfluence();
        double beforeNation = nation.getInfluence();

        influenceService.applyDailyGrowth();

        assertThat(town.getInfluence()).isGreaterThan(beforeTown);
        assertThat(nation.getInfluence()).isGreaterThan(beforeNation);
    }

    @Test
    void rankingSortsByInfluenceDescending() {
        Nation second = new Nation();
        second.setUuid(UUID.randomUUID().toString());
        second.setName("B国");
        second.setInfluence(-1);
        core.registerNation(second);
        nation.setInfluence(100);
        core.saveNation(nation);

        assertThat(influenceService.rankNations()).first().isEqualTo(nation);
    }

    @Test
    void spendInfluenceFailsWhenInsufficient() {
        nation.setInfluence(10);
        core.saveNation(nation);
        assertThat(influenceService.spendInfluence(nation, 5)).isTrue();
        assertThat(nation.getInfluence()).isEqualTo(5);
        assertThat(influenceService.spendInfluence(nation, 10)).isFalse();
    }
}