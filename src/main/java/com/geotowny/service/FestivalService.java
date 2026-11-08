package com.geotowny.service;

import com.geotowny.Log;
import com.geotowny.config.PluginConfig;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Supplier;

public final class FestivalService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final PluginConfig config;
    private final Supplier<LocalDate> dateSupplier;

    public FestivalService(PluginConfig config) {
        this(config, LocalDate::now);
    }

    public FestivalService(PluginConfig config, Supplier<LocalDate> dateSupplier) {
        this.config = config;
        this.dateSupplier = dateSupplier;
    }

    public boolean isFestival() {
        if (!config.festivalEnabled()) {
            return false;
        }
        LocalDate today = dateSupplier.get();
        Map<String, String> custom = config.festivalCustom();
        if (custom.containsKey(today.format(DATE_FORMAT))) {
            return true;
        }
        if (config.festivalWeekend()) {
            DayOfWeek day = today.getDayOfWeek();
            return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
        }
        return false;
    }

    public String getFestivalName() {
        if (!isFestival()) {
            return null;
        }
        LocalDate today = dateSupplier.get();
        String name = config.festivalCustom().get(today.format(DATE_FORMAT));
        if (name != null) {
            return name;
        }
        return "周末节日";
    }

    public double getTaxMultiplier() {
        return isFestival() ? config.festivalTaxMultiplier() : 1.0d;
    }

    public double getInfluenceMultiplier() {
        return isFestival() ? config.festivalInfluenceMultiplier() : 1.0d;
    }

    public double getShopPriceMultiplier() {
        return isFestival() ? config.festivalShopPriceMultiplier() : 1.0d;
    }

    public void dailyCheck() {
        if (isFestival()) {
            Log.info("今日为节日：{}（税收×{}，影响力×{}，市场×{}）。",
                    getFestivalName(), getTaxMultiplier(), getInfluenceMultiplier(), getShopPriceMultiplier());
        }
    }
}