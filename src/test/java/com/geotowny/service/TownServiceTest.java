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

class TownServiceTest {

    @TempDir
    Path tempDir;

    private StorageService storage;
    private GeoTownyCore core;
    private TownService townService;
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

        alice = core.getOrCreateResident(UUID.randomUUID(), "Alice");
        alice.deposit(Money.of(5000));
        core.saveResident(alice);

        bob = core.getOrCreateResident(UUID.randomUUID(), "Bob");
        bob.deposit(Money.of(500));
        core.saveResident(bob);
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    private WorldLocation spawn() {
        return new WorldLocation("minecraft:overworld", 8, 64, 8, 0, 0);
    }

    private Town createTown() {
        Result result = townService.createTown(alice, "柏林", "minecraft:overworld", 0, 0, spawn());
        assertThat(result.isSuccess()).as(result.getMessage()).isTrue();
        return core.getTown(alice.getTownUuid());
    }

    @Test
    void createsTownWithHomeBlockAndCode() {
        Town town = createTown();

        assertThat(town).isNotNull();
        assertThat(town.getName()).isEqualTo("柏林");
        assertThat(town.getCode()).hasSize(3);
        assertThat(town.getMayorUuid()).isEqualTo(alice.getUuidString());
        assertThat(town.getBalance()).isEqualTo(Money.of(1000.0));
        assertThat(town.getClaimCount()).isEqualTo(1);
        assertThat(town.getHomeBlockKey()).isEqualTo("minecraft:overworld#0#0");
        assertThat(core.getTownBlockAt("minecraft:overworld", 0, 0)).isNotNull();
        assertThat(alice.getTownRank()).isEqualTo(TownRank.MAYOR);
        assertThat(alice.getBalance()).isEqualTo(Money.of(4100.0));
    }

    @Test
    void rejectsDuplicateNameAndInsufficientFundsAndExistingTown() {
        createTown();

        Result duplicate = townService.createTown(bob, "柏林", "minecraft:overworld", 0, 1, spawn());
        assertThat(duplicate.isSuccess()).isFalse();
        assertThat(duplicate.getMessage()).contains("已存在");

        Resident carol = core.getOrCreateResident(UUID.randomUUID(), "Carol");
        Result poor = townService.createTown(carol, "新城", "minecraft:overworld", 0, 2, spawn());
        assertThat(poor.isSuccess()).isFalse();
        assertThat(poor.getMessage()).contains("余额不足");

        Result again = townService.createTown(alice, "另一城", "minecraft:overworld", 0, 3, spawn());
        assertThat(again.isSuccess()).isFalse();
        assertThat(again.getMessage()).contains("已属于城镇");
    }

    @Test
    void rejectsNetherAndEndDimensions() {
        Result nether = townService.createTown(bob, "下界城", "minecraft:the_nether", 0, 0, spawn());
        assertThat(nether.isSuccess()).isFalse();
        assertThat(nether.getMessage()).contains("下界");

        Result end = townService.createTown(bob, "末地城", "minecraft:the_end", 0, 0, spawn());
        assertThat(end.isSuccess()).isFalse();
    }

    @Test
    void joinLeaveAndKick() {
        Town town = createTown();
        townService.setOpen(alice, town, true);

        assertThat(townService.joinTown(bob, town, true).isSuccess()).isTrue();
        assertThat(bob.getTownUuid()).isEqualTo(town.getUuid());
        assertThat(town.getResidentUuids()).contains(bob.getUuidString());

        assertThat(townService.leaveTown(bob, town).isSuccess()).isTrue();
        assertThat(bob.hasTown()).isFalse();

        assertThat(townService.joinTown(bob, town, true).isSuccess()).isTrue();
        assertThat(townService.kickMember(alice, town, bob).isSuccess()).isTrue();
        assertThat(bob.hasTown()).isFalse();
    }

    @Test
    void mayorCannotLeaveAndCanTransfer() {
        Town town = createTown();
        assertThat(townService.leaveTown(alice, town).isSuccess()).isFalse();

        townService.setOpen(alice, town, true);
        townService.joinTown(bob, town, true);

        assertThat(townService.setMayor(alice, town, bob).isSuccess()).isTrue();
        assertThat(town.getMayorUuid()).isEqualTo(bob.getUuidString());
        assertThat(bob.getTownRank()).isEqualTo(TownRank.MAYOR);
        assertThat(alice.getTownRank()).isEqualTo(TownRank.RESIDENT);
    }

    @Test
    void depositAndWithdraw() {
        Town town = createTown();

        assertThat(townService.deposit(alice, town, Money.of(100.0)).isSuccess()).isTrue();
        assertThat(town.getBalance()).isEqualTo(Money.of(1100.0));
        assertThat(alice.getBalance()).isEqualTo(Money.of(4000.0));

        assertThat(townService.withdraw(alice, town, Money.of(100.0)).isSuccess()).isTrue();
        assertThat(town.getBalance()).isEqualTo(Money.of(1000.0));
        assertThat(alice.getBalance()).isEqualTo(Money.of(4100.0));

        townService.setOpen(alice, town, true);
        townService.joinTown(bob, town, true);
        assertThat(townService.withdraw(bob, town, Money.of(10.0)).isSuccess()).isFalse();
    }

    @Test
    void renameUpdatesIndexAndCode() {
        Town town = createTown();
        String oldCode = town.getCode();

        assertThat(townService.renameTown(alice, town, "新柏林").isSuccess()).isTrue();

        assertThat(core.getTownByName("柏林")).isNull();
        assertThat(core.getTownByName("新柏林")).isSameAs(town);
        assertThat(town.getCode()).hasSize(3);
        assertThat(town.getCode()).isNotEqualTo(oldCode);
    }

    @Test
    void deleteRefundsTreasuryAndReleasesClaims() {
        Town town = createTown();
        townService.deposit(alice, town, Money.of(100.0));
        Money before = alice.getBalance();

        Result result = townService.deleteTown(alice, town);

        assertThat(result.isSuccess()).isTrue();
        assertThat(core.getTown(town.getUuid())).isNull();
        assertThat(core.getTownBlockAt("minecraft:overworld", 0, 0)).isNull();
        assertThat(alice.getBalance()).isEqualTo(before.plus(Money.of(1100.0)));
        assertThat(alice.hasTown()).isFalse();
    }
}