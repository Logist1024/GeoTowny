package com.geotowny.service;

import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.Resident;
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

class ResidentServiceTest {

    @TempDir
    Path tempDir;

    private StorageService storage;
    private GeoTownyCore core;
    private ResidentService residentService;
    private Resident alice;
    private Resident bob;

    @BeforeEach
    void setUp() {
        storage = new StorageService(tempDir.resolve("plugin"));
        storage.start();
        core = new GeoTownyCore(storage, new com.geotowny.config.PluginConfig(new YamlConfiguration()));
        core.load();
        residentService = new ResidentService(core);

        alice = core.getOrCreateResident(UUID.randomUUID(), "Alice");
        alice.deposit(Money.of(500));
        core.saveResident(alice);

        bob = core.getOrCreateResident(UUID.randomUUID(), "Bob");
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    @Test
    void transfersMoneyBetweenPlayers() {
        Result result = residentService.pay(alice, bob, Money.of(100.0));

        assertThat(result.isSuccess()).isTrue();
        assertThat(alice.getBalance()).isEqualTo(Money.of(500.0));
        assertThat(bob.getBalance()).isEqualTo(Money.of(200.0));
    }

    @Test
    void rejectsSelfTransferAndInsufficientFunds() {
        assertThat(residentService.pay(alice, alice, Money.of(10.0)).isSuccess()).isFalse();
        assertThat(residentService.pay(alice, bob, Money.of(99999.0)).isSuccess()).isFalse();
        assertThat(alice.getBalance()).isEqualTo(Money.of(600.0));
    }
}