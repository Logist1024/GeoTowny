package com.geotowny.service;

import com.geotowny.config.PluginConfig;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.Ideology;
import com.geotowny.domain.model.Nation;
import com.geotowny.domain.model.NationRank;
import com.geotowny.domain.model.RelationType;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.Town;
import com.geotowny.domain.model.WarState;
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

class NationServiceTest {

    @TempDir
    Path tempDir;

    private StorageService storage;
    private GeoTownyCore core;
    private TownService townService;
    private NationService nationService;
    private Resident alice;
    private Resident bob;
    private Resident carol;
    private Town townA;
    private Town townB;
    private Town townC;

    @BeforeEach
    void setUp() {
        PluginConfig config = new PluginConfig(new YamlConfiguration());
        storage = new StorageService(tempDir.resolve("plugin"));
        storage.start();
        core = new GeoTownyCore(storage, config);
        core.load();
        townService = new TownService(core, config);
        nationService = new NationService(core, config);

        alice = resident("Alice", 0, 0);
        bob = resident("Bob", 1, 0);
        carol = resident("Carol", 2, 0);
        townA = townOf(alice);
        townB = townOf(bob);
        townC = townOf(carol);
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    private Resident resident(String name, int chunkX, int chunkZ) {
        Resident resident = core.getOrCreateResident(UUID.randomUUID(), name);
        resident.deposit(Money.of(300_000));
        core.saveResident(resident);
        Result result = townService.createTown(resident, name + "城", "minecraft:overworld",
                chunkX, chunkZ, new WorldLocation("minecraft:overworld", 8, 64, 8, 0, 0));
        assertThat(result.isSuccess()).as(result.getMessage()).isTrue();
        return resident;
    }

    private Town townOf(Resident resident) {
        return core.getTown(resident.getTownUuid());
    }

    private Nation createNation(Resident founder, String name) {
        Result result = nationService.createNation(founder, townOf(founder), name);
        assertThat(result.isSuccess()).as(result.getMessage()).isTrue();
        return core.getNationByName(name);
    }

    @Test
    void createsNationAndChargesFounder() {
        Money before = alice.getBalance();
        Nation nation = createNation(alice, "德意志");

        assertThat(nation).isNotNull();
        assertThat(nation.getCode()).hasSize(4);
        assertThat(nation.getKingUuid()).isEqualTo(alice.getUuidString());
        assertThat(nation.getCapitalTownUuid()).isEqualTo(townA.getUuid());
        assertThat(nation.getIdeology()).isEqualTo(Ideology.MONARCHY);
        assertThat(townA.getNationUuid()).isEqualTo(nation.getUuid());
        assertThat(townA.isCapital()).isTrue();
        assertThat(alice.getNationRank()).isEqualTo(NationRank.KING);
        assertThat(alice.getBalance()).isEqualTo(before.minus(Money.of(100_000.0)));
    }

    @Test
    void addsAndRemovesMemberTown() {
        Nation nation = createNation(alice, "德意志");

        assertThat(nationService.addTown(alice, nation, townB).isSuccess()).isTrue();
        assertThat(nation.getTownCount()).isEqualTo(2);
        assertThat(townB.getNationUuid()).isEqualTo(nation.getUuid());

        assertThat(nationService.removeTown(alice, nation, townB, true).isSuccess()).isTrue();
        assertThat(nation.getTownCount()).isEqualTo(1);
        assertThat(townB.hasNation()).isFalse();
    }

    @Test
    void changesCapitalAndKing() {
        Nation nation = createNation(alice, "德意志");
        nationService.addTown(alice, nation, townB);

        assertThat(nationService.setCapital(alice, nation, townB).isSuccess()).isTrue();
        assertThat(nation.getCapitalTownUuid()).isEqualTo(townB.getUuid());
        assertThat(townB.isCapital()).isTrue();
        assertThat(townA.isCapital()).isFalse();

        assertThat(nationService.setKing(alice, nation, bob).isSuccess()).isTrue();
        assertThat(nation.getKingUuid()).isEqualTo(bob.getUuidString());
        assertThat(bob.getNationRank()).isEqualTo(NationRank.KING);
        assertThat(alice.getNationRank()).isEqualTo(NationRank.CITIZEN);
    }

    @Test
    void diplomacyAndWar() {
        Nation nation = createNation(alice, "德意志");
        Nation other = createNation(carol, "法兰西");

        assertThat(nationService.setRelation(alice, nation, other, RelationType.ALLY).isSuccess()).isTrue();
        assertThat(nation.getRelation(other.getUuid())).isEqualTo(RelationType.ALLY);
        assertThat(other.getRelation(nation.getUuid())).isEqualTo(RelationType.ALLY);

        nationService.deposit(alice, nation, Money.of(3000.0));
        assertThat(nationService.declareWar(alice, nation, other).isSuccess()).isTrue();
        assertThat(nation.getRelation(other.getUuid())).isEqualTo(RelationType.AT_WAR);
        assertThat(nation.getWarState()).isEqualTo(WarState.ACTIVE);
        assertThat(nation.getBalance()).isEqualTo(Money.of(1000.0));

        assertThat(nationService.makePeace(alice, nation, other).isSuccess()).isTrue();
        assertThat(nation.getRelation(other.getUuid())).isEqualTo(RelationType.NEUTRAL);
        assertThat(nation.getWarState()).isEqualTo(WarState.ENDED);
    }

    @Test
    void dissolvesNationAndDetachesTowns() {
        Nation nation = createNation(alice, "德意志");
        nationService.addTown(alice, nation, townB);
        nationService.deposit(alice, nation, Money.of(500.0));
        Money before = alice.getBalance();

        assertThat(nationService.dissolveNation(alice, nation).isSuccess()).isTrue();

        assertThat(core.getNation(nation.getUuid())).isNull();
        assertThat(townA.hasNation()).isFalse();
        assertThat(townA.isCapital()).isFalse();
        assertThat(townB.hasNation()).isFalse();
        assertThat(alice.getNationRank()).isNull();
        assertThat(alice.getBalance()).isEqualTo(before.plus(Money.of(500.0)));
    }
}