package com.geotowny.service;

import com.geotowny.config.PluginConfig;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.Ideology;
import com.geotowny.domain.model.Nation;
import com.geotowny.domain.model.PlotType;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.Town;
import com.geotowny.domain.model.TownBlock;
import com.geotowny.domain.money.Money;
import com.geotowny.storage.SettlementStateStore;
import com.geotowny.util.Result;

import java.util.ArrayList;
import java.util.List;

/**
 * 经济服务：居民 → 城镇 → 国家税收链、维护费、破产与幂等每日结算。
 * <p>决策 D-02：每日结算按现实日；{@link #settleDay(long)} 对同一天幂等。
 */
public final class EconomyService {

    private static final long MILLIS_PER_DAY = 86_400_000L;

    private final GeoTownyCore core;
    private final PluginConfig config;
    private final TownService townService;
    private final SettlementStateStore stateStore;
    private final FestivalService festival;

    private long lastSettledDay;

    public EconomyService(GeoTownyCore core, PluginConfig config,
                          TownService townService, SettlementStateStore stateStore) {
        this(core, config, townService, stateStore, null);
    }

    public EconomyService(GeoTownyCore core, PluginConfig config, TownService townService,
                          SettlementStateStore stateStore, FestivalService festival) {
        this.core = core;
        this.config = config;
        this.townService = townService;
        this.stateStore = stateStore;
        this.festival = festival;
        this.lastSettledDay = stateStore.loadLastSettledDay();
    }

    public boolean shouldSettle(long today) {
        return lastSettledDay == 0L || today > lastSettledDay;
    }

    public long lastSettledDay() {
        return lastSettledDay;
    }

    /**
     * 执行每日结算。首次调用只初始化日期，不结算。
     */
    public SettlementReport settleDay(long today) {
        if (lastSettledDay == 0L) {
            lastSettledDay = today;
            stateStore.saveLastSettledDay(today);
            return SettlementReport.skipped(today);
        }
        if (today <= lastSettledDay) {
            return SettlementReport.skipped(today);
        }

        TaxResult tax = collectDailyTaxes();
        List<BankruptcyNotice> notices = new ArrayList<>(lastTaxNotices);
        List<BankruptcyNotice> dissolved = checkBankruptcies();
        notices.addAll(dissolved);
        lastSettledDay = today;
        stateStore.saveLastSettledDay(today);

        return new SettlementReport(today, false, tax.totalTax(), tax.taxedResidents(),
                tax.totalUpkeep(), dissolved.size(), List.copyOf(notices));
    }

    public Money transferResidentToTown(Resident resident, Town town, Money amount) {
        if (amount == null || !amount.isPositive()) {
            return Money.ZERO;
        }
        if (!resident.withdraw(amount)) {
            return Money.ZERO;
        }
        town.deposit(amount);
        core.saveResident(resident);
        core.saveTown(town);
        return amount;
    }

    public Result transferTownToResident(Town town, Resident resident, Money amount) {
        if (amount == null || !amount.isPositive()) {
            return Result.fail("金额必须为正数。");
        }
        if (!town.withdraw(amount)) {
            return Result.fail("城镇国库余额不足。");
        }
        resident.deposit(amount);
        core.saveTown(town);
        core.saveResident(resident);
        return Result.ok("已从城镇国库支付 " + amount.format() + "。");
    }

    public Result transferTownToNation(Town town, Nation nation, Money amount) {
        if (amount == null || !amount.isPositive()) {
            return Result.fail("金额必须为正数。");
        }
        if (!town.withdraw(amount)) {
            return Result.fail("城镇国库余额不足。");
        }
        nation.deposit(amount);
        core.saveTown(town);
        core.saveNation(nation);
        return Result.ok("已向国家缴纳 " + amount.format() + "。");
    }

    public TaxResult collectDailyTaxes() {
        Money totalTax = Money.ZERO;
        Money totalUpkeep = Money.ZERO;
        int taxedResidents = 0;
        List<BankruptcyNotice> notices = new ArrayList<>();

        for (Town town : new ArrayList<>(core.allTowns())) {
            try {
                TaxResult townResult = processTown(town, notices);
                totalTax = totalTax.plus(townResult.totalTax());
                totalUpkeep = totalUpkeep.plus(townResult.totalUpkeep());
                taxedResidents += townResult.taxedResidents();
            } catch (RuntimeException e) {
                com.geotowny.Log.error("城镇 {} 每日税收结算失败: {}", town.getName(), e.getMessage());
            }
        }

        this.lastTaxNotices = notices;
        return new TaxResult(totalTax, taxedResidents, totalUpkeep);
    }

    private List<BankruptcyNotice> lastTaxNotices = List.of();

    public List<BankruptcyNotice> lastTaxNotices() {
        return lastTaxNotices;
    }

    private TaxResult processTown(Town town, List<BankruptcyNotice> notices) {
        Nation nation = town.hasNation() ? core.getNation(town.getNationUuid()) : null;
        Money tax = min(town.getTaxes(), Money.of(config.maxDailyTax()));
        if (festival != null) {
            tax = tax.times(festival.getTaxMultiplier());
        }
        if (nation != null && nation.getIdeology().isProvinceSystem()
                && nation.isProvinceTown(town.getUuid())) {
            tax = tax.times(1.0d + nation.getIdeology().getProvinceTaxBonus());
        }
        boolean clergySystem = nation != null && nation.getIdeology().isClergySystem();

        Money collected = Money.ZERO;
        int taxed = 0;
        List<Resident> changed = new ArrayList<>();

        if (tax.isPositive()) {
            for (String residentUuid : new ArrayList<>(town.getResidentUuids())) {
                Resident resident = core.getResident(residentUuid);
                if (resident == null) {
                    continue;
                }
                Money actual = tax;
                if (clergySystem && nation.getClergy().containsKey(residentUuid)) {
                    actual = tax.times(0.9d);
                }
                if (actual.greaterThan(resident.getBalance())) {
                    actual = resident.getBalance();
                }
                if (actual.isPositive() && resident.withdraw(actual)) {
                    town.deposit(actual);
                    collected = collected.plus(actual);
                    taxed++;
                    changed.add(resident);
                }
            }
        }

        Money plotIncome = computePlotIncome(town);
        town.deposit(plotIncome);
        Money interest = computeBankInterest(town);
        town.deposit(interest);

        Money upkeep = computeUpkeep(town);
        Money paidUpkeep = Money.ZERO;
        if (town.getBalance().atLeast(upkeep)) {
            town.withdraw(upkeep);
            paidUpkeep = upkeep;
            if (town.getBankruptcyDays() > 0) {
                town.setBankruptcyDays(0);
                town.setBankruptcySince(0);
            }
        } else {
            town.setBankruptcyDays(town.getBankruptcyDays() + 1);
            if (town.getBankruptcySince() <= 0) {
                town.setBankruptcySince(System.currentTimeMillis());
            }
            long remainDays = remainingBankruptcyDays(town);
            notices.add(new BankruptcyNotice(town.getUuid(), town.getName(), town.getMayorUuid(),
                    remainDays, "城镇 " + town.getName() + " 国库不足支付维护费 "
                    + upkeep.format() + "，剩余宽限期 " + remainDays + " 天。"));
        }

        if (paidUpkeep.isPositive() && nation != null) {
            Money nationTax = nation.getTaxes();
            if (nationTax.isPositive() && town.withdraw(nationTax)) {
                nation.deposit(nationTax);
                core.saveNation(nation);
            }
        }

        core.saveTown(town);
        if (!changed.isEmpty()) {
            core.saveResidents(changed);
        }
        return new TaxResult(collected, taxed, paidUpkeep);
    }

    public List<BankruptcyNotice> checkBankruptcies() {
        int thresholdDays = config.bankruptcyDays();
        if (thresholdDays <= 0) {
            return List.of();
        }
        long thresholdMs = thresholdDays * MILLIS_PER_DAY;
        long now = System.currentTimeMillis();
        List<BankruptcyNotice> notices = new ArrayList<>();

        for (Town town : new ArrayList<>(core.allTowns())) {
            if (town.getBankruptcySince() <= 0) {
                continue;
            }
            long elapsed = now - town.getBankruptcySince();
            if (elapsed < thresholdMs) {
                continue;
            }
            notices.add(new BankruptcyNotice(town.getUuid(), town.getName(), town.getMayorUuid(),
                    0, "城镇 " + town.getName() + " 连续破产达到 " + thresholdDays + " 天，已强制解散。"));
            try {
                townService.dissolveTown(town);
            } catch (RuntimeException e) {
                com.geotowny.Log.error("解散破产城镇 {} 失败: {}", town.getName(), e.getMessage());
            }
        }
        return notices;
    }

    public Money computeUpkeep(Town town) {
        Money base = Money.of(config.upkeepPerBlock()).times(town.getClaimCount());
        int farm = 0;
        int fort = 0;
        int embassy = 0;
        for (String key : town.getClaimBlockKeys()) {
            TownBlock block = core.getTownBlock(key);
            if (block == null) {
                continue;
            }
            PlotType type = block.getPlotType();
            if (type == PlotType.FARM) {
                farm++;
            } else if (type == PlotType.FORT) {
                fort++;
            } else if (type == PlotType.EMBASSY) {
                embassy++;
            }
        }
        Money discount = min(base, Money.of(config.farmPlotUpkeepDiscount()).times(farm));
        Money result = base.minus(discount);
        result = result.plus(Money.of(config.fortPlotUpkeepExtra()).times(fort));
        result = result.plus(Money.of(config.embassyPlotUpkeep()).times(embassy));
        return result.isNegative() ? Money.ZERO : result;
    }

    public Money computePlotIncome(Town town) {
        Money income = Money.ZERO;
        for (String key : town.getClaimBlockKeys()) {
            TownBlock block = core.getTownBlock(key);
            if (block == null) {
                continue;
            }
            income = switch (block.getPlotType()) {
                case SHOP -> income.plus(Money.of(config.shopPlotDailyIncome()));
                case INN -> income.plus(Money.of(config.innPlotDailyIncome()));
                case ARENA -> income.plus(Money.of(config.arenaPlotDailyIncome()));
                default -> income;
            };
        }
        return income;
    }

    public Money computeBankInterest(Town town) {
        int banks = 0;
        for (String key : town.getClaimBlockKeys()) {
            TownBlock block = core.getTownBlock(key);
            if (block != null && block.getPlotType() == PlotType.BANK) {
                banks++;
            }
        }
        if (banks == 0) {
            return Money.ZERO;
        }
        return town.getBalance().times(config.bankPlotInterestRate()).times(banks);
    }

    private long remainingBankruptcyDays(Town town) {
        long thresholdMs = config.bankruptcyDays() * MILLIS_PER_DAY;
        long elapsed = System.currentTimeMillis() - town.getBankruptcySince();
        long remaining = thresholdMs - elapsed;
        return remaining <= 0 ? 0 : remaining / MILLIS_PER_DAY;
    }

    private static Money min(Money a, Money b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    public record TaxResult(Money totalTax, int taxedResidents, Money totalUpkeep) {
    }

    public record BankruptcyNotice(String townUuid, String townName, String mayorUuid,
                                   long remainingDays, String message) {
    }

    public record SettlementReport(long day, boolean skipped, Money totalTax, int taxedResidents,
                                   Money totalUpkeep, int dissolvedTowns, List<BankruptcyNotice> notices) {
        public static SettlementReport skipped(long day) {
            return new SettlementReport(day, true, Money.ZERO, 0, Money.ZERO, 0, List.of());
        }

        public String summary() {
            if (skipped) {
                return "第 " + day + " 天无需结算";
            }
            return "第 " + day + " 天结算：税收 " + totalTax.format()
                    + "，缴税居民 " + taxedResidents
                    + "，维护费 " + totalUpkeep.format()
                    + "，解散城镇 " + dissolvedTowns;
        }
    }
}