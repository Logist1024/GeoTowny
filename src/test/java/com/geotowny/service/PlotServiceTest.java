package com.geotowny.service;

import com.geotowny.config.PluginConfig;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.PlotType;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.Town;
import com.geotowny.domain.model.TownBlock;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlotServiceTest {

    @TempDir
    Path tempDir;

    private StorageService storage;
    private GeoTownyCore core;
    private TownService townService;
    private ClaimService claimService;
    private PlotService plotService;
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
        claimService = new ClaimService(core, config);
        plotService = new PlotService(core);

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
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    @Test
    void claimsAndUnclaimsPlot() {
        assertThat(plotService.claimPlot(alice, "minecraft:overworld", 0, 0).isSuccess()).isTrue();
        TownBlock block = core.getTownBlockAt("minecraft:overworld", 0, 0);
        assertThat(block.getOwnerUuid()).isEqualTo(alice.getUuidString());

        assertThat(plotService.claimPlot(bob, "minecraft:overworld", 0, 0).isSuccess()).isFalse();
        assertThat(plotService.unclaimPlot(alice, "minecraft:overworld", 0, 0).isSuccess()).isTrue();
        assertThat(block.getOwnerUuid()).isNull();
    }

    @Test
    void setsPlotTypeAndRejectsDeprecatedTypes() {
        plotService.claimPlot(alice, "minecraft:overworld", 0, 0);

        assertThat(plotService.setPlotType(alice, "minecraft:overworld", 0, 0, PlotType.SHOP).isSuccess()).isTrue();
        assertThat(core.getTownBlockAt("minecraft:overworld", 0, 0).getPlotType()).isEqualTo(PlotType.SHOP);
        assertThat(plotService.setPlotType(alice, "minecraft:overworld", 0, 0, PlotType.JAIL).isSuccess()).isFalse();
    }

    @Test
    void sellsAndBuysPlot() {
        plotService.claimPlot(alice, "minecraft:overworld", 0, 0);
        assertThat(plotService.setForSale(alice, "minecraft:overworld", 0, 0, Money.of(100.0)).isSuccess()).isTrue();

        Result bought = plotService.buyPlot(bob, "minecraft:overworld", 0, 0);

        assertThat(bought.isSuccess()).as(bought.getMessage()).isTrue();
        TownBlock block = core.getTownBlockAt("minecraft:overworld", 0, 0);
        assertThat(block.getOwnerUuid()).isEqualTo(bob.getUuidString());
        assertThat(block.isForSale()).isFalse();
        assertThat(bob.getBalance()).isEqualTo(Money.of(500.0));
        assertThat(alice.getBalance()).isEqualTo(Money.of(4200.0));
    }

    @Test
    void managesTrustList() {
        plotService.claimPlot(alice, "minecraft:overworld", 0, 0);

        assertThat(plotService.trust(alice, "minecraft:overworld", 0, 0, bob).isSuccess()).isTrue();
        TownBlock block = core.getTownBlockAt("minecraft:overworld", 0, 0);
        assertThat(block.isTrusted(bob.getUuidString())).isTrue();

        assertThat(plotService.untrust(alice, "minecraft:overworld", 0, 0, bob).isSuccess()).isTrue();
        assertThat(block.isTrusted(bob.getUuidString())).isFalse();
    }

    @Test
    void cannotUnclaimOtherPlayersPlot() {
        plotService.claimPlot(alice, "minecraft:overworld", 0, 0);

        assertThat(plotService.unclaimPlot(bob, "minecraft:overworld", 0, 0).isSuccess()).isFalse();
    }
}