package com.geotowny.service;

import com.geotowny.config.PluginConfig;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.Nation;
import com.geotowny.domain.model.RelationType;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.Town;
import com.geotowny.domain.model.WorldLocation;
import com.geotowny.domain.money.Money;
import com.geotowny.storage.StorageService;
import com.geotowny.util.Result;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KillDropServiceTest {

    @TempDir
    Path tempDir;

    private StorageService storage;
    private GeoTownyCore core;
    private KillDropService service;
    private Resident killer;
    private Resident victim;
    private Resident outsider;

    @BeforeEach
    void setUp() {
        PluginConfig config = new PluginConfig(new YamlConfiguration());
        storage = new StorageService(tempDir.resolve("plugin"));
        storage.start();
        core = new GeoTownyCore(storage, config);
        core.load();
        TownService townService = new TownService(core, config);
        NationService nationService = new NationService(core, config);

        killer = resident(townService, "Killer", 0, 0);
        victim = resident(townService, "Victim", 1, 0);
        outsider = resident(townService, "Outsider", 2, 0);
        Nation attacker = nationService.createNation(killer, townOf(killer), "攻击国").isSuccess()
                ? core.getNationByName("攻击国") : null;
        Nation defender = nationService.createNation(victim, townOf(victim), "防守国").isSuccess()
                ? core.getNationByName("防守国") : null;
        assertThat(attacker).isNotNull();
        assertThat(defender).isNotNull();
        attacker.setRelation(defender.getUuid(), RelationType.AT_WAR);
        defender.setRelation(attacker.getUuid(), RelationType.AT_WAR);
        core.saveNation(attacker);
        core.saveNation(defender);
        victim.setBalance(Money.of(1000));
        killer.setBalance(Money.ZERO);
        core.saveResident(victim);
        core.saveResident(killer);

        service = new KillDropService(core, config, () -> 0.0d);
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    @Test
    void warKillTransfersPercentOfVictimBalance() {
        Player killerPlayer = proxy(killer.getUuid());
        Player victimPlayer = proxy(victim.getUuid());

        Money dropped = service.onPlayerKill(killerPlayer, victimPlayer);

        assertThat(dropped).isEqualTo(Money.of(100.0));
        assertThat(victim.getBalance()).isEqualTo(Money.of(900.0));
        assertThat(killer.getBalance()).isEqualTo(Money.of(100.0));
        assertThat(service.currentWarPercent(killer.getUuid())).isLessThan(0.10);
    }

    @Test
    void normalKillUsesConfiguredChance() {
        victim.setBalance(Money.of(1000));
        outsider.setBalance(Money.ZERO);
        core.saveResident(victim);
        core.saveResident(outsider);
        Player killerPlayer = proxy(outsider.getUuid());
        Player victimPlayer = proxy(victim.getUuid());

        Money dropped = service.onPlayerKill(killerPlayer, victimPlayer);

        assertThat(dropped).isEqualTo(Money.of(10.0));
        assertThat(outsider.getBalance()).isEqualTo(Money.of(10.0));
    }

    private Resident resident(TownService townService, String name, int x, int z) {
        Resident resident = core.getOrCreateResident(UUID.randomUUID(), name);
        resident.deposit(Money.of(300_000));
        core.saveResident(resident);
        Result result = townService.createTown(resident, name + "城", "minecraft:overworld", x, z,
                new WorldLocation("minecraft:overworld", 8, 64, 8, 0, 0));
        assertThat(result.isSuccess()).as(result.getMessage()).isTrue();
        return resident;
    }

    private Town townOf(Resident resident) {
        return core.getTown(resident.getTownUuid());
    }

    private static Player proxy(UUID uuid) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) {
                        return uuid;
                    }
                    if (method.getName().equals("getName")) {
                        return "P";
                    }
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) return false;
                    if (type == int.class) return 0;
                    if (type == long.class) return 0L;
                    if (type == double.class) return 0.0d;
                    return null;
                });
    }
}