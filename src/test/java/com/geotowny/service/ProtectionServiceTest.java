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

class ProtectionServiceTest {

    @TempDir
    Path tempDir;

    private StorageService storage;
    private GeoTownyCore core;
    private TownService townService;
    private ProtectionService protectionService;
    private Resident alice;
    private Resident bob;
    private Resident carol;
    private Town town;

    @BeforeEach
    void setUp() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("protection.allow-foreigner-actions", false);
        PluginConfig config = new PluginConfig(yaml);

        storage = new StorageService(tempDir.resolve("plugin"));
        storage.start();
        core = new GeoTownyCore(storage, config);
        core.load();
        townService = new TownService(core, config);
        protectionService = new ProtectionService(core, config);

        alice = core.getOrCreateResident(UUID.randomUUID(), "Alice");
        alice.deposit(Money.of(5000));
        core.saveResident(alice);

        bob = core.getOrCreateResident(UUID.randomUUID(), "Bob");
        carol = core.getOrCreateResident(UUID.randomUUID(), "Carol");

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
    void membersCanBuildInsideTown() {
        assertThat(protectionService.canBreak(alice, "minecraft:overworld", 0, 0)).isTrue();
        assertThat(protectionService.canPlace(bob, "minecraft:overworld", 0, 0)).isTrue();
        assertThat(protectionService.canInteract(alice, "minecraft:overworld", 0, 0)).isTrue();
    }

    @Test
    void foreignersAreBlockedWhenConfigured() {
        assertThat(protectionService.canBreak(carol, "minecraft:overworld", 0, 0)).isFalse();
        assertThat(protectionService.canPlace(carol, "minecraft:overworld", 0, 0)).isFalse();
        assertThat(protectionService.canInteract(carol, "minecraft:overworld", 0, 0)).isFalse();
    }

    @Test
    void wildernessIsAlwaysAllowed() {
        assertThat(protectionService.canBreak(carol, "minecraft:overworld", 100, 100)).isTrue();
    }

    @Test
    void pvpInsideTownIsAllowed() {
        assertThat(protectionService.canPvp(alice, "minecraft:overworld", 0, 0)).isTrue();
        assertThat(protectionService.canPvp(alice, "minecraft:overworld", 100, 100)).isTrue();
    }
}