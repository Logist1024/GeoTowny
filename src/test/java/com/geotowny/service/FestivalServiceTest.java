package com.geotowny.service;

import com.geotowny.config.PluginConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FestivalServiceTest {

    private static PluginConfig config(boolean weekend) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("festival.enabled", true);
        yaml.set("festival.weekend", weekend);
        yaml.set("festival.custom.2026-01-01", "新年");
        yaml.set("festival.tax-multiplier", 0.5);
        yaml.set("festival.influence-multiplier", 2.0);
        yaml.set("festival.shop-price-multiplier", 0.9);
        return new PluginConfig(yaml);
    }

    @Test
    void recognisesCustomFestival() {
        FestivalService festival = new FestivalService(config(false),
                () -> LocalDate.of(2026, 1, 1));
        assertThat(festival.isFestival()).isTrue();
        assertThat(festival.getFestivalName()).isEqualTo("新年");
        assertThat(festival.getTaxMultiplier()).isEqualTo(0.5);
        assertThat(festival.getInfluenceMultiplier()).isEqualTo(2.0);
        assertThat(festival.getShopPriceMultiplier()).isEqualTo(0.9);
    }

    @Test
    void recognisesWeekendFestival() {
        FestivalService festival = new FestivalService(config(true),
                () -> LocalDate.of(2026, 1, 3));
        assertThat(festival.isFestival()).isTrue();
        assertThat(festival.getFestivalName()).isEqualTo("周末节日");
    }

    @Test
    void normalWeekdayHasNoMultipliers() {
        FestivalService festival = new FestivalService(config(false),
                () -> LocalDate.of(2026, 1, 6));
        assertThat(festival.isFestival()).isFalse();
        assertThat(festival.getFestivalName()).isNull();
        assertThat(festival.getTaxMultiplier()).isEqualTo(1.0);
    }
}