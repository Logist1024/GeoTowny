package com.geotowny.task;

import com.geotowny.GeoTownyPlugin;
import com.geotowny.Log;
import com.geotowny.service.EconomyService;
import com.geotowny.service.FestivalService;
import com.geotowny.service.IdeologyService;
import com.geotowny.service.InfluenceService;
import com.geotowny.service.KillDropService;
import com.geotowny.service.MarketService;
import com.geotowny.service.WarService;
import com.geotowny.time.GameClock;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

/**
 * 每日结算调度：每秒检查一次日期，日期变化时执行幂等结算。
 */
public final class SettlementScheduler extends BukkitRunnable {

    private final GeoTownyPlugin plugin;
    private final EconomyService economyService;
    private final MarketService marketService;
    private final WarService warService;
    private final IdeologyService ideologyService;
    private final InfluenceService influenceService;
    private final KillDropService killDropService;
    private final FestivalService festivalService;
    private final GameClock clock;
    private long lastP6Day;

    public SettlementScheduler(GeoTownyPlugin plugin, EconomyService economyService,
                               MarketService marketService, WarService warService,
                               IdeologyService ideologyService, InfluenceService influenceService,
                               KillDropService killDropService, FestivalService festivalService,
                               GameClock clock) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.marketService = marketService;
        this.warService = warService;
        this.ideologyService = ideologyService;
        this.influenceService = influenceService;
        this.killDropService = killDropService;
        this.festivalService = festivalService;
        this.clock = clock;
        this.lastP6Day = economyService.lastSettledDay();
    }

    public void start() {
        runTaskTimer(plugin, 20L, 20L);
    }

    @Override
    public void run() {
        long today = clock.today();
        marketService.resetDaily(today);
        if (lastP6Day == 0L) {
            lastP6Day = today;
        } else if (today > lastP6Day) {
            warService.cleanupDaily();
            ideologyService.dailyTick();
            influenceService.applyDailyGrowth();
            killDropService.dailyReset();
            festivalService.dailyCheck();
            lastP6Day = today;
        }
        if (!economyService.shouldSettle(today)) {
            return;
        }
        EconomyService.SettlementReport report;
        try {
            report = economyService.settleDay(today);
        } catch (RuntimeException e) {
            Log.error("每日结算失败: {}", e.getMessage());
            return;
        }
        if (report.skipped()) {
            Log.info("每日结算状态已初始化为第 {} 天。", today);
            return;
        }

        Log.info(report.summary());
        for (EconomyService.BankruptcyNotice notice : report.notices()) {
            notifyMayor(notice);
        }
    }

    private void notifyMayor(EconomyService.BankruptcyNotice notice) {
        if (notice.mayorUuid() == null) {
            return;
        }
        try {
            Player mayor = Bukkit.getPlayer(UUID.fromString(notice.mayorUuid()));
            if (mayor != null) {
                mayor.sendMessage(ChatColor.RED + "[GeoTowny] " + notice.message());
            }
        } catch (IllegalArgumentException ignored) {
            // 非法 UUID，跳过通知。
        }
    }
}