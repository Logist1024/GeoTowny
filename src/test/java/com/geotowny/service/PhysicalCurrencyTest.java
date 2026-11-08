package com.geotowny.service;

import com.geotowny.domain.money.Money;
import com.geotowny.storage.PhysicalCurrencyStore;
import com.geotowny.storage.StorageLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PhysicalCurrencyTest {

    @TempDir
    Path tempDir;

    @Test
    void exposesEightDenominationsAndValidatesThem() {
        assertThat(PhysicalCurrencyService.DENOMINATIONS).hasSize(8);
        assertThat(PhysicalCurrencyService.isValidDenomination(Money.of(0.1))).isTrue();
        assertThat(PhysicalCurrencyService.isValidDenomination(Money.of(0.5))).isTrue();
        assertThat(PhysicalCurrencyService.isValidDenomination(Money.of(1000))).isTrue();
        assertThat(PhysicalCurrencyService.isValidDenomination(Money.of(1.0))).isFalse();
        assertThat(PhysicalCurrencyService.isValidDenomination(Money.ZERO)).isFalse();
        assertThat(PhysicalCurrencyService.isValidDenomination(null)).isFalse();
    }

    @Test
    void calculatesTwoPercentWithdrawalFeeInCents() {
        assertThat(PhysicalCurrencyService.fee(Money.of(100.0), 0.02)).isEqualTo(Money.of(2.0));
        assertThat(PhysicalCurrencyService.withdrawCost(Money.of(100.0), 0.02))
                .isEqualTo(Money.of(102.0));
        assertThat(PhysicalCurrencyService.withdrawCost(Money.of(1000.0), 0.02))
                .isEqualTo(Money.of(1020.0));
    }

    @Test
    void persistsIssuedCounts() {
        PhysicalCurrencyStore store = new PhysicalCurrencyStore(new StorageLayout(tempDir));
        store.save(Map.of(1000L, 3L, 100000L, 1L));

        Map<Long, Long> loaded = store.load();

        assertThat(loaded).containsEntry(1000L, 3L).containsEntry(100000L, 1L);
    }
}