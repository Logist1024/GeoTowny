package com.geotowny.p8;

import com.geotowny.config.PluginConfig;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.Nation;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.Town;
import com.geotowny.domain.model.WorldLocation;
import com.geotowny.domain.money.Money;
import com.geotowny.domain.war.Campaign;
import com.geotowny.service.ClaimService;
import com.geotowny.service.KillDropService;
import com.geotowny.service.NationService;
import com.geotowny.service.TownService;
import com.geotowny.service.WarService;
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

class P8SmokeTest {

    @TempDir
    Path tempDir;

    private StorageService storage;
    private GeoTownyCore core;
    private TownService townService;
    private ClaimService claimService;
    private NationService nationService;
    private WarService warService;
    private Resident alice;
    private Resident bob;
    private Town townA;
    private Town townB;
    private Nation nationA;
    private Nation nationB;

    @BeforeEach
    void setUp() {
        PluginConfig config = new PluginConfig(new YamlConfiguration());
        storage = new StorageService(tempDir.resolve("plugin"));
        storage.start();
        core = new GeoTownyCore(storage, config);
        core.load();
        townService = new TownService(core, config);
        claimService = new ClaimService(core, config);
        nationService = new NationService(core, config);
        warService = new WarService(null, core, config, new WorldRegistry(new YamlConfiguration()),
                townService, new KillDropService(core, config));

        alice = resident("Alice", 0, 0);
        bob = resident("Bob", 5, 0);
        townA = core.getTown(alice.getTownUuid());
        townB = core.getTown(bob.getTownUuid());
        nationA = createNation(alice, "A国");
        nationB = createNation(bob, "B国");
        nationA.deposit(Money.of(5000));
        core.saveNation(nationA);
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    @Test
    void buildClaimNationWarOccupyDissolveChain() {
        Result claim = claimService.claim(alice, townA, "minecraft:overworld", 0, 1);
        assertThat(claim.isSuccess()).as(claim.getMessage()).isTrue();
        assertThat(townA.getClaimCount()).isEqualTo(2);

        Result war = warService.declareWar(alice, nationA, nationB);
        assertThat(war.isSuccess()).as(war.getMessage()).isTrue();

        Campaign campaign = new Campaign(Campaign.Type.SIEGE, nationA.getUuid(), nationB.getUuid(),
                townB.getUuid(), 10, 5,
                new WorldLocation("minecraft:overworld", 32, 64, 0, 0, 0));
        Money attackerBefore = nationA.getBalance();
        Money targetTreasury = townB.getBalance();
        warService.endCampaign(campaign, Campaign.Status.SUCCESS);

        assertThat(townB.getNationUuid()).isEqualTo(nationA.getUuid());
        assertThat(nationA.getTownUuids()).contains(townB.getUuid());
        assertThat(nationA.getBalance()).isGreaterThanOrEqualTo(attackerBefore.plus(targetTreasury));
        assertThat(nationB.isExiled()).isTrue();
        assertThat(bob.getTownRank().name()).isEqualTo("RESIDENT");
        assertThat(bob.getNationRank().name()).isEqualTo("CITIZEN");

        Result nationDissolve = nationService.dissolveNation(alice, nationA);
        assertThat(nationDissolve.isSuccess()).as(nationDissolve.getMessage()).isTrue();
        assertThat(townA.getNationUuid()).isNull();
        assertThat(townB.getNationUuid()).isNull();

        Result townDissolve = townService.dissolveTown(townA);
        assertThat(townDissolve.isSuccess()).as(townDissolve.getMessage()).isTrue();
        assertThat(core.getTown(townA.getUuid())).isNull();
        assertThat(core.getTownBlockAt("minecraft:overworld", 0, 0)).isNull();
        assertThat(core.getTownBlockAt("minecraft:overworld", 0, 1)).isNull();
        assertThat(alice.hasTown()).isFalse();
    }

    @Test
    void townWithoutMayorDissolvesTreasuryIntoNation() {
        Town orphan = new Town();
        orphan.setUuid(UUID.randomUUID().toString());
        orphan.setName("无主城");
        orphan.setNationUuid(nationB.getUuid());
        orphan.setBalance(Money.of(100.0));
        nationB.getTownUuids().add(orphan.getUuid());
        core.registerTown(orphan);
        core.saveNation(nationB);
        Money before = nationB.getBalance();

        Result result = townService.dissolveTown(orphan);

        assertThat(result.isSuccess()).as(result.getMessage()).isTrue();
        assertThat(core.getTown(orphan.getUuid())).isNull();
        assertThat(nationB.getBalance()).isEqualTo(before.plus(Money.of(100.0)));
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

    private Nation createNation(Resident founder, String name) {
        Result result = nationService.createNation(founder, core.getTown(founder.getTownUuid()), name);
        assertThat(result.isSuccess()).as(result.getMessage()).isTrue();
        return core.getNationByName(name);
    }
}