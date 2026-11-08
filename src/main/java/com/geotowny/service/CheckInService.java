package com.geotowny.service;

import com.geotowny.Log;
import com.geotowny.config.PluginConfig;
import com.geotowny.domain.checkin.CheckInRecord;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.money.Money;
import com.geotowny.storage.CheckInStore;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class CheckInService {

    private final PluginConfig config;
    private final CheckInStore store;
    private final Consumer<Resident> residentSaver;
    private final Supplier<LocalDate> dateSupplier;
    private final Map<String, CheckInRecord> records = new HashMap<>();

    public CheckInService(PluginConfig config, CheckInStore store, Consumer<Resident> residentSaver) {
        this(config, store, residentSaver, LocalDate::now);
    }

    public CheckInService(PluginConfig config, CheckInStore store,
                          Consumer<Resident> residentSaver, Supplier<LocalDate> dateSupplier) {
        this.config = config;
        this.store = store;
        this.residentSaver = residentSaver;
        this.dateSupplier = dateSupplier;
    }

    public void load() {
        records.clear();
        records.putAll(store.load());
        save();
        Log.info("签到数据已加载：{} 名玩家。", records.size());
    }

    public CheckInRecord record(String uuid) {
        return records.computeIfAbsent(uuid, key -> {
            CheckInRecord record = new CheckInRecord(key);
            save();
            return record;
        });
    }

    public boolean hasCheckedInToday(String uuid) {
        return dateSupplier.get().toString().equals(record(uuid).getLastCheckInDate());
    }

    public Money nextReward(String uuid) {
        CheckInRecord record = record(uuid);
        return rewardFor(record.getCurrentStreak() + 1);
    }

    public Money rewardFor(int streak) {
        double[] rewards = config.checkInStreakRewards();
        if (rewards == null || rewards.length == 0) {
            return Money.of(config.checkInBaseReward());
        }
        int cycle = Math.max(1, Math.min(config.checkInCycleDays(), rewards.length));
        int index = Math.min(Math.max(0, streak - 1), cycle - 1);
        return Money.of(rewards[index]);
    }

    public CheckInResult checkIn(Resident resident) {
        if (!config.checkInEnabled()) {
            return new CheckInResult(false, 0, Money.ZERO, "签到系统已禁用。");
        }
        String uuid = resident.getUuidString();
        CheckInRecord record = record(uuid);
        LocalDate today = dateSupplier.get();
        String todayText = today.toString();
        if (todayText.equals(record.getLastCheckInDate())) {
            return new CheckInResult(false, record.getCurrentStreak(), Money.ZERO, "今天已经签到过了。");
        }

        String yesterday = today.minusDays(1).toString();
        int streak = yesterday.equals(record.getLastCheckInDate()) ? record.getCurrentStreak() + 1 : 1;
        Money reward = rewardFor(streak);

        record.setLastCheckInDate(todayText);
        record.setCurrentStreak(streak);
        record.setTotalCheckIns(record.getTotalCheckIns() + 1);
        save();

        resident.deposit(reward);
        residentSaver.accept(resident);

        return new CheckInResult(true, streak, reward,
                "签到成功！连续签到第 " + streak + " 天，获得 " + reward.format() + " "
                        + config.currencyName() + "。");
    }

    public void save() {
        store.save(records);
    }

    public int cycleDays() {
        return config.checkInCycleDays();
    }

    public record CheckInResult(boolean success, int streak, Money reward, String message) {
    }
}