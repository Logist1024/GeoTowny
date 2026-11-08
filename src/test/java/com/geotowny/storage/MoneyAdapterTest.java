package com.geotowny.storage;

import com.geotowny.domain.money.Money;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyAdapterTest {

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Money.class, new MoneyAdapter())
            .create();

    @Test
    void readsLegacyDouble() {
        assertThat(gson.fromJson("123.45", Money.class)).isEqualTo(Money.ofCents(12_345));
    }

    @Test
    void readsStringAndObjectForms() {
        assertThat(gson.fromJson("\"12.30\"", Money.class)).isEqualTo(Money.ofCents(1_230));
        assertThat(gson.fromJson("{\"cents\":12345}", Money.class)).isEqualTo(Money.ofCents(12_345));
    }

    @Test
    void writesCentsAsObject() {
        assertThat(gson.toJson(Money.ofCents(12_345))).isEqualTo("{\"cents\":12345}");
    }

    @Test
    void nullBecomesZero() {
        assertThat(gson.fromJson("null", Money.class)).isEqualTo(Money.ZERO);
    }
}