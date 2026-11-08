package com.geotowny.service;

import com.geotowny.config.PluginConfig;
import com.geotowny.core.GeoTownyCore;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimServiceTest {

    @TempDir
    Path tempDir;

    private StorageService storage;
    private GeoTownyCore core;
    private TownService townService;
    private ClaimService claimService;
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

        alice = core.getOrCreateResident(UUID.randomUUID(), "Alice");
        alice.deposit(Money.of(5000));
        core.saveResident(alice);

        bob = core.getOrCreateResident(UUID.randomUUID(), "Bob");

        Result result = townService.createTown(alice, "柏林", "minecraft:overworld", 0, 0,
                new WorldLocation("minecraft:overworld", 8, 64, 8, 0, 0));
        assertThat(result.isSuccess()).as(result.getMessage()).isTrue();
        town = core.getTown(alice.getTownUuid());
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    @Test
    void claimsAdjacentChunkAndChargesTreasury() {
        Result result = claimService.claim(alice, town, "minecraft:overworld", 0, 1);

        assertThat(result.isSuccess()).as(result.getMessage()).isTrue();
        assertThat(town.getClaimCount()).isEqualTo(2);
        assertThat(town.getBalance()).isEqualTo(Money.of(950.0));
        assertThat(core.getTownBlockAt("minecraft:overworld", 0, 1)).isNotNull();
    }

    @Test
    void rejectsOccupiedNonAdjacentAndForeignClaims() {
        assertThat(claimService.claim(alice, town, "minecraft:overworld", 0, 0).isSuccess()).isFalse();
        assertThat(claimService.claim(alice, town, "minecraft:overworld", 5, 5).isSuccess()).isFalse();
        assertThat(claimService.claim(bob, town, "minecraft:overworld", 0, 1).isSuccess()).isFalse();
    }

    @Test
    void cannotUnclaimHomeButCanUnclaimOtherChunks() {
        claimService.claim(alice, town, "minecraft:overworld", 0, 1);

        assertThat(claimService.unclaim(alice, town, "minecraft:overworld", 0, 0).isSuccess()).isFalse();
        assertThat(claimService.unclaim(alice, town, "minecraft:overworld", 0, 1).isSuccess()).isTrue();
        assertThat(core.getTownBlockAt("minecraft:overworld", 0, 1)).isNull();
    }

    @Test
    void maxClaimsGrowsWithResidentsAndRespectsCap() {
        int base = claimService.maxClaims(town);
        townService.setOpen(alice, town, true);
        townService.joinTown(bob, town, true);

        assertThat(claimService.maxClaims(town)).isEqualTo(base + 8);
    }
}