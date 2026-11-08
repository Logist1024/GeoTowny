package com.geotowny.service;

import com.geotowny.config.PluginConfig;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.Ideology;
import com.geotowny.domain.model.Nation;
import com.geotowny.domain.model.NationRank;
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

class IdeologyServiceTest {

    @TempDir
    Path tempDir;

    private StorageService storage;
    private GeoTownyCore core;
    private IdeologyService service;
    private Resident alice;
    private Town town;
    private Nation nation;

    @BeforeEach
    void setUp() {
        PluginConfig config = new PluginConfig(new YamlConfiguration());
        storage = new StorageService(tempDir.resolve("plugin"));
        storage.start();
        core = new GeoTownyCore(storage, config);
        core.load();
        TownService townService = new TownService(core, config);
        NationService nationService = new NationService(core, config);
        service = new IdeologyService(core, config);

        alice = core.getOrCreateResident(UUID.randomUUID(), "Alice");
        alice.deposit(Money.of(300_000));
        core.saveResident(alice);
        Result townResult = townService.createTown(alice, "A城", "minecraft:overworld", 0, 0,
                new WorldLocation("minecraft:overworld", 8, 64, 8, 0, 0));
        assertThat(townResult.isSuccess()).as(townResult.getMessage()).isTrue();
        town = core.getTown(alice.getTownUuid());
        Result nationResult = nationService.createNation(alice, town, "A国");
        assertThat(nationResult.isSuccess()).as(nationResult.getMessage()).isTrue();
        nation = core.getNationByName("A国");
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    @Test
    void onlyFourCoreIdeologiesAreSupported() {
        assertThat(service.isCoreIdeology(Ideology.DEMOCRACY)).isTrue();
        assertThat(service.isCoreIdeology(Ideology.EMPIRE)).isTrue();
        assertThat(service.isCoreIdeology(Ideology.FEDERATION)).isTrue();
        assertThat(service.isCoreIdeology(Ideology.THEOCRACY)).isTrue();
        assertThat(service.isCoreIdeology(Ideology.MONARCHY)).isFalse();
        assertThat(service.cosmeticWarning(Ideology.MONARCHY)).contains("仅保留称呼");
        assertThat(service.cosmeticWarning(Ideology.DEMOCRACY)).isNull();
    }

    @Test
    void federationAllowsFreeLeaveOnly() {
        assertThat(service.canFreeLeave(town)).isFalse();
        nation.setIdeology(Ideology.FEDERATION);
        core.saveNation(nation);
        assertThat(service.canFreeLeave(town)).isTrue();
    }

    @Test
    void empireProvinceReceivesTaxBonus() {
        nation.setIdeology(Ideology.EMPIRE);
        nation.getProvinceTowns().add(town.getUuid());
        core.saveNation(nation);
        assertThat(service.provinceTaxBonus(town)).isEqualTo(0.5);
        nation.getProvinceTowns().clear();
        assertThat(service.provinceTaxBonus(town)).isZero();
    }

    @Test
    void theocracyGetsHolyWarBonusOnlyAgainstNonTheocracy() {
        nation.setIdeology(Ideology.THEOCRACY);
        Nation monarchy = new Nation();
        monarchy.setUuid(UUID.randomUUID().toString());
        monarchy.setIdeology(Ideology.MONARCHY);
        Nation theocracy = new Nation();
        theocracy.setUuid(UUID.randomUUID().toString());
        theocracy.setIdeology(Ideology.THEOCRACY);
        assertThat(service.holyWarBonus(nation, monarchy)).isEqualTo(0.2);
        assertThat(service.holyWarBonus(nation, theocracy)).isZero();
    }

    @Test
    void democracyCanImpeachLeader() {
        nation.setIdeology(Ideology.DEMOCRACY);
        core.saveNation(nation);
        assertThat(service.startImpeachment(alice, nation).isSuccess()).isTrue();
        assertThat(service.castImpeachmentVote(alice, nation, true).isSuccess()).isTrue();
        assertThat(nation.getKingUuid()).isNull();
        assertThat(alice.getNationRank()).isEqualTo(NationRank.CITIZEN);
    }

    @Test
    void termLimitForcesLeaderStepDown() {
        nation.setIdeology(Ideology.DEMOCRACY);
        nation.setTermStart(System.currentTimeMillis() - 10_000L);
        nation.setTermDuration(1_000L);
        core.saveNation(nation);

        service.dailyTick();

        assertThat(nation.getKingUuid()).isNull();
        assertThat(alice.getNationRank()).isEqualTo(NationRank.CITIZEN);
    }
}