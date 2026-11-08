package com.geotowny.service;

import com.geotowny.config.PluginConfig;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.Town;
import com.geotowny.domain.model.TownRank;
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

class TownMergeSplitTest {

    @TempDir
    Path tempDir;

    private StorageService storage;
    private GeoTownyCore core;
    private TownService townService;
    private ClaimService claimService;
    private Resident alice;
    private Resident bob;

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
        bob.deposit(Money.of(5000));
        core.saveResident(bob);
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    private WorldLocation spawn() {
        return new WorldLocation("minecraft:overworld", 8, 64, 8, 0, 0);
    }

    private Town createTown(Resident mayor, String name, int chunkX, int chunkZ) {
        Result result = townService.createTown(mayor, name, "minecraft:overworld", chunkX, chunkZ, spawn());
        assertThat(result.isSuccess()).as(result.getMessage()).isTrue();
        return core.getTown(mayor.getTownUuid());
    }

    @Test
    void mergesSourceIntoTarget() {
        Town source = createTown(alice, "柏林", 0, 0);
        Town target = createTown(bob, "巴黎", 1, 0);
        claimService.claim(alice, source, "minecraft:overworld", 0, 1);
        claimService.claim(bob, target, "minecraft:overworld", 1, 1);

        Result result = townService.mergeTowns(alice, source, target);

        assertThat(result.isSuccess()).as(result.getMessage()).isTrue();
        assertThat(core.getTown(source.getUuid())).isNull();
        assertThat(target.getClaimCount()).isEqualTo(4);
        assertThat(target.getBalance()).isEqualTo(Money.of(1900.0));
        assertThat(alice.getTownUuid()).isEqualTo(target.getUuid());
        assertThat(alice.getTownRank()).isEqualTo(TownRank.RESIDENT);
        assertThat(core.getTownBlockAt("minecraft:overworld", 0, 0).getTownUuid())
                .isEqualTo(target.getUuid());
    }

    @Test
    void splitCreatesNewTownAndKeepsFundsConserved() {
        Town source = createTown(alice, "柏林", 0, 0);
        claimService.claim(alice, source, "minecraft:overworld", 0, 1);

        Result result = townService.splitTown(alice, source, "新柏林",
                "minecraft:overworld", 0, 1, spawn());

        assertThat(result.isSuccess()).as(result.getMessage()).isTrue();
        Town split = core.getTownByName("新柏林");
        assertThat(split).isNotNull();
        assertThat(split.getMayorUuid()).isEqualTo(alice.getUuidString());
        assertThat(split.getHomeBlockKey()).isEqualTo("minecraft:overworld#0#1");
        assertThat(split.getBalance()).isEqualTo(Money.of(950.0));
        assertThat(source.getBalance()).isEqualTo(Money.ZERO);
        assertThat(source.getResidentUuids()).isEmpty();
        assertThat(source.getMayorUuid()).isNull();
        assertThat(alice.getTownUuid()).isEqualTo(split.getUuid());
    }

    @Test
    void splitRejectsHomeBlockAndDuplicateName() {
        Town source = createTown(alice, "柏林", 0, 0);

        Result home = townService.splitTown(alice, source, "中心",
                "minecraft:overworld", 0, 0, spawn());
        assertThat(home.isSuccess()).isFalse();
        assertThat(home.getMessage()).contains("中心");

        Result duplicate = townService.splitTown(alice, source, "柏林",
                "minecraft:overworld", 0, 0, spawn());
        assertThat(duplicate.isSuccess()).isFalse();
        assertThat(duplicate.getMessage()).contains("已存在");
    }
}