package com.geotowny.service;

import com.geotowny.config.PluginConfig;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.Nation;
import com.geotowny.domain.model.PlotType;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.Town;
import com.geotowny.domain.model.TownBlock;
import com.geotowny.domain.model.WorldLocation;
import com.geotowny.domain.money.Money;
import com.geotowny.storage.SettlementStateStore;
import com.geotowny.storage.StorageService;
import com.geotowny.util.Result;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EconomyServiceTest {

    @TempDir
    Path tempDir;

    private StorageService storage;
    private GeoTownyCore core;
    private TownService townService;
    private EconomyService economyService;
    private Resident alice;
    private Resident bob;
    private Town town;

    @BeforeEach
    void setUp() {
        PluginConfig config = new PluginConfig(new YamlConfiguration());
        storage = new StorageService(tempDir.resolve("plugin"));
        storage.start();
        core = new GeoTownyCore(storage, config);
        core.load();
        townService = new TownService(core, config);
        economyService = new EconomyService(core, config, townService, new SettlementStateStore(storage.layout()));

        alice = core.getOrCreateResident(UUID.randomUUID(), "Alice");
        alice.deposit(Money.of(5000));
        core.saveResident(alice);

        bob = core.getOrCreateResident(UUID.randomUUID(), "Bob");
        bob.deposit(Money.of(500));
        core.saveResident(bob);

        Result created = townService.createTown(alice, "柏林", "minecraft:overworld", 0, 0,
                new WorldLocation("minecraft:overworld", 8, 64, 8, 0, 0));
        assertThat(created.isSuccess()).as(created.getMessage()).isTrue();
        town = core.getTown(alice.getTownUuid());

        townService.setOpen(alice, town, true);
        townService.joinTown(bob, town, true);
        townService.setTaxes(alice, town, Money.of(10.0));
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    @Test
    void firstCallInitializesWithoutSettling() {
        EconomyService.SettlementReport report = economyService.settleDay(100);

        assertThat(report.skipped()).isTrue();
        assertThat(town.getBalance()).isEqualTo(Money.of(1000.0));
        assertThat(economyService.lastSettledDay()).isEqualTo(100);
    }

    @Test
    void settlesTaxesUpkeepAndIsIdempotent() {
        economyService.settleDay(100);

        EconomyService.SettlementReport report = economyService.settleDay(101);

        assertThat(report.skipped()).isFalse();
        assertThat(report.totalTax()).isEqualTo(Money.of(20.0));
        assertThat(report.totalUpkeep()).isEqualTo(Money.of(1.0));
        assertThat(town.getBalance()).isEqualTo(Money.of(1019.0));
        assertThat(alice.getBalance()).isEqualTo(Money.of(4090.0));
        assertThat(bob.getBalance()).isEqualTo(Money.of(590.0));

        EconomyService.SettlementReport again = economyService.settleDay(101);
        assertThat(again.skipped()).isTrue();
        assertThat(town.getBalance()).isEqualTo(Money.of(1019.0));
    }

    @Test
    void marksBankruptcyWhenTreasuryCannotPayUpkeep() {
        economyService.settleDay(100);
        town.setBalance(Money.ZERO);
        town.setTaxes(Money.ZERO);

        EconomyService.SettlementReport report = economyService.settleDay(101);

        assertThat(town.getBankruptcyDays()).isEqualTo(1);
        assertThat(town.getBankruptcySince()).isPositive();
        assertThat(report.notices()).isNotEmpty();
    }

    @Test
    void addsShopPlotIncomeBeforeUpkeep() {
        economyService.settleDay(100);
        TownBlock shop = new TownBlock("minecraft:overworld", 0, 1, town.getUuid());
        shop.setPlotType(PlotType.SHOP);
        town.getClaimBlockKeys().add(shop.key());
        core.putTownBlock(shop);
        core.saveTownBlocks();
        town.setTaxes(Money.ZERO);
        town.setBalance(Money.of(100.0));

        economyService.settleDay(101);

        // 100 + SHOP 5 - 维护费 2 = 103
        assertThat(town.getBalance()).isEqualTo(Money.of(103.0));
    }

    @Test
    void transfersNationTaxAfterUpkeep() {
        economyService.settleDay(100);
        Nation nation = new Nation();
        nation.setUuid("n1");
        nation.setName("德意志");
        nation.setTaxes(Money.of(5.0));
        nation.getTownUuids().add(town.getUuid());
        core.registerNation(nation);
        town.setNationUuid("n1");
        core.saveTown(town);

        economyService.settleDay(101);

        assertThat(nation.getBalance()).isEqualTo(Money.of(5.0));
        assertThat(town.getBalance()).isEqualTo(Money.of(1014.0));
    }
}