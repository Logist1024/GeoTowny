package com.geotowny.service;

import com.geotowny.config.PluginConfig;
import com.geotowny.domain.checkin.CheckInRecord;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.money.Money;
import com.geotowny.storage.CheckInStore;
import com.geotowny.storage.StorageLayout;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CheckInServiceTest {

    @TempDir
    Path tempDir;

    private CheckInService service;
    private CheckInStore store;
    private AtomicReference<LocalDate> date;
    private Resident resident;

    @BeforeEach
    void setUp() {
        PluginConfig config = new PluginConfig(new YamlConfiguration());
        store = new CheckInStore(new StorageLayout(tempDir.resolve("plugin")));
        date = new AtomicReference<>(LocalDate.of(2026, 1, 1));
        service = new CheckInService(config, store, value -> {
        }, date::get);
        service.load();
        resident = new Resident(UUID.randomUUID(), "Alice", Money.ZERO);
    }

    @Test
    void firstCheckInStartsStreakAndRewards() {
        CheckInService.CheckInResult result = service.checkIn(resident);

        assertThat(result.success()).isTrue();
        assertThat(result.streak()).isEqualTo(1);
        assertThat(result.reward()).isEqualTo(Money.of(50.0));
        assertThat(resident.getBalance()).isEqualTo(Money.of(50.0));
        assertThat(service.hasCheckedInToday(resident.getUuidString())).isTrue();
    }

    @Test
    void sameDayCannotCheckInTwice() {
        service.checkIn(resident);
        Money balance = resident.getBalance();

        CheckInService.CheckInResult second = service.checkIn(resident);

        assertThat(second.success()).isFalse();
        assertThat(second.message()).contains("已经签到");
        assertThat(resident.getBalance()).isEqualTo(balance);
        assertThat(service.record(resident.getUuidString()).getTotalCheckIns()).isEqualTo(1);
    }

    @Test
    void consecutiveDayIncrementsStreak() {
        service.checkIn(resident);
        date.set(LocalDate.of(2026, 1, 2));

        CheckInService.CheckInResult second = service.checkIn(resident);

        assertThat(second.success()).isTrue();
        assertThat(second.streak()).isEqualTo(2);
        assertThat(second.reward()).isEqualTo(Money.of(80.0));
        assertThat(service.record(resident.getUuidString()).getTotalCheckIns()).isEqualTo(2);
    }

    @Test
    void missingDayResetsStreak() {
        service.checkIn(resident);
        date.set(LocalDate.of(2026, 1, 4));

        CheckInService.CheckInResult result = service.checkIn(resident);

        assertThat(result.success()).isTrue();
        assertThat(result.streak()).isEqualTo(1);
        assertThat(result.reward()).isEqualTo(Money.of(50.0));
    }

    @Test
    void statePersistsAcrossReload() {
        service.checkIn(resident);
        date.set(LocalDate.of(2026, 1, 2));
        service.checkIn(resident);
        service.save();

        CheckInService reloaded = new CheckInService(
                new PluginConfig(new YamlConfiguration()), store, value -> {
        }, date::get);
        reloaded.load();

        CheckInRecord record = reloaded.record(resident.getUuidString());
        assertThat(record.getCurrentStreak()).isEqualTo(2);
        assertThat(record.getTotalCheckIns()).isEqualTo(2);
        assertThat(record.getLastCheckInDate()).isEqualTo("2026-01-02");
    }

    @Test
    void rewardsFollowConfiguredCycle() {
        assertThat(service.rewardFor(1)).isEqualTo(Money.of(50.0));
        assertThat(service.rewardFor(7)).isEqualTo(Money.of(500.0));
        assertThat(service.rewardFor(8)).isEqualTo(Money.of(500.0));
    }
}