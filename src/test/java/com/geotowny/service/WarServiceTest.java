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
import com.geotowny.world.WorldRegistry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WarServiceTest {

    @TempDir
    Path tempDir;

    private StorageService storage;
    private GeoTownyCore core;
    private PluginConfig config;
    private TownService townService;
    private NationService nationService;
    private WarService warService;
    private Resident alice;
    private Resident bob;
    private Resident carol;
    private Town townA;
    private Town townB;
    private Town townC;
    private Nation nationA;
    private Nation nationB;

    @BeforeEach
    void setUp() {
        config = new PluginConfig(new YamlConfiguration());
        storage = new StorageService(tempDir.resolve("plugin"));
        storage.start();
        core = new GeoTownyCore(storage, config);
        core.load();
        townService = new TownService(core, config);
        nationService = new NationService(core, config);

        alice = resident("Alice", 0, 0);
        bob = resident("Bob", 1, 0);
        carol = resident("Carol", 2, 0);
        townA = core.getTown(alice.getTownUuid());
        townB = core.getTown(bob.getTownUuid());
        townC = core.getTown(carol.getTownUuid());

        nationA = createNation(alice, "A国");
        nationB = createNation(bob, "B国");
        assertThat(nationService.addTown(bob, nationB, townC).isSuccess()).isTrue();
        nationA.deposit(Money.of(20_000));
        nationB.deposit(Money.of(20_000));
        core.saveNation(nationA);
        core.saveNation(nationB);

        warService = new WarService(null, core, config,
                new WorldRegistry(new YamlConfiguration()), townService,
                new KillDropService(core, config, () -> 0.0d));
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    @Test
    void declareWarAndMakePeace() {
        Money before = nationA.getBalance();
        Result war = warService.declareWar(alice, nationA, nationB);
        assertThat(war.isSuccess()).as(war.getMessage()).isTrue();
        assertThat(nationA.getRelation(nationB.getUuid())).isEqualTo(RelationType.AT_WAR);
        assertThat(nationA.getWarState()).isEqualTo(WarState.ACTIVE);
        assertThat(nationA.getBalance()).isEqualTo(before.minus(Money.of(config.warDeclareCost())));

        Result peace = warService.makePeace(alice, nationA, nationB);
        assertThat(peace.isSuccess()).as(peace.getMessage()).isTrue();
        assertThat(nationA.getRelation(nationB.getUuid())).isEqualTo(RelationType.NEUTRAL);
        assertThat(nationA.getWarState()).isEqualTo(WarState.NONE);
    }

    @Test
    void democracyRequiresVoteBeforeWar() {
        nationA.setIdeology(Ideology.DEMOCRACY);
        core.saveNation(nationA);

        Result vote = warService.declareWar(alice, nationA, nationB);
        assertThat(vote.isSuccess()).isTrue();
        assertThat(vote.getMessage()).contains("公投");
        assertThat(nationA.getRelation(nationB.getUuid())).isNotEqualTo(RelationType.AT_WAR);

        Result passed = warService.castWarVote(alice, nationA, nationB, true);
        assertThat(passed.isSuccess()).as(passed.getMessage()).isTrue();
        assertThat(nationA.getRelation(nationB.getUuid())).isEqualTo(RelationType.AT_WAR);
    }

    @Test
    void surrenderCedesTownAndPaysReparations() {
        assertThat(warService.declareWar(bob, nationB, nationA).isSuccess()).isTrue();
        Money winnerBefore = nationA.getBalance();
        Money loserBefore = nationB.getBalance();
        townC.setBalance(Money.ZERO);
        core.saveTown(townC);

        Result result = warService.surrender(bob, nationB, nationA, townC, Money.of(100.0));

        assertThat(result.isSuccess()).as(result.getMessage()).isTrue();
        assertThat(townC.getNationUuid()).isEqualTo(nationA.getUuid());
        assertThat(nationA.getTownUuids()).contains(townC.getUuid());
        assertThat(nationB.getTownUuids()).doesNotContain(townC.getUuid());
        assertThat(nationB.getBalance()).isEqualTo(loserBefore.minus(Money.of(100.0)));
        assertThat(nationA.getBalance()).isEqualTo(winnerBefore.plus(Money.of(100.0)));
        assertThat(nationB.getRelation(nationA.getUuid())).isEqualTo(RelationType.NEUTRAL);
    }

    @Test
    void empireCanAnnexWeakEnemy() {
        nationA.setIdeology(Ideology.EMPIRE);
        core.saveNation(nationA);
        assertThat(warService.declareWar(alice, nationA, nationB).isSuccess()).isTrue();
        nationB.setStability(0);
        nationB.setBalance(Money.ZERO);
        core.saveNation(nationB);

        Result result = warService.annexNation(alice, nationA, nationB);

        assertThat(result.isSuccess()).as(result.getMessage()).isTrue();
        assertThat(core.getNation(nationB.getUuid())).isNull();
        assertThat(townB.getNationUuid()).isEqualTo(nationA.getUuid());
        assertThat(nationA.getProvinceTowns()).contains(townB.getUuid(), townC.getUuid());
        assertThat(bob.getNationRank()).isEqualTo(NationRank.CITIZEN);
    }

    private Resident resident(String name, int x, int z) {
        Resident resident = core.getOrCreateResident(UUID.randomUUID(), name);
        resident.deposit(Money.of(300_000));
        core.saveResident(resident);
        Result result = townService.createTown(resident, name + "城", "minecraft:overworld", x, z,
                new WorldLocation("minecraft:overworld", 8, 64, 8, 0, 0));
        assertThat(result.isSuccess()).as(result.getMessage()).isTrue();
        return resident;
    }

    private Nation createNation(Resident founder, String name) {
        Result result = nationService.createNation(founder, core.getTown(founder.getTownUuid()), name);
        assertThat(result.isSuccess()).as(result.getMessage()).isTrue();
        return core.getNationByName(name);
    }
}