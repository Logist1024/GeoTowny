package com.geotowny.p8;

import com.geotowny.config.PluginConfig;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.Nation;
import com.geotowny.domain.model.Town;
import com.geotowny.domain.model.TownBlock;
import com.geotowny.service.FestivalService;
import com.geotowny.service.InfluenceService;
import com.geotowny.storage.StorageService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class P8StressTest {

    private static final int TOWNS = 5000;
    private static final int CLAIMS = 10000;
    private static final long THRESHOLD_MS = 30_000L;

    @Test
    void dailySettlementForFiveThousandTownsCompletesWithinThreshold() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("P8_STRESS")),
                "设置 P8_STRESS=1 运行压力测试");
        Path temp = java.nio.file.Files.createTempDirectory("geotowny-p8-stress");
        StorageService storage = new StorageService(temp);
        try {
            storage.start();
            PluginConfig config = new PluginConfig(new YamlConfiguration());
            GeoTownyCore core = new GeoTownyCore(storage, config);
            core.load();

            Nation nation = new Nation();
            nation.setUuid(UUID.randomUUID().toString());
            nation.setName("压力测试国");
            core.registerNation(nation);

            int townIndex = 0;
            int claimIndex = 0;
            for (int i = 0; i < TOWNS; i++) {
                Town town = new Town();
                town.setUuid("t-" + i);
                town.setName("镇-" + i);
                town.setMayorUuid(UUID.randomUUID().toString());
                town.setNationUuid(nation.getUuid());
                town.setFounded(System.currentTimeMillis());
                town.setBalance(com.geotowny.domain.money.Money.ZERO);
                town.setTaxes(com.geotowny.domain.money.Money.ZERO);
                town.setPlotPrice(com.geotowny.domain.money.Money.ZERO);
                town.setColor(0xFFFFFF);
                town.getClaimBlockKeys().add(TownBlock.keyOf("minecraft:overworld", townIndex, 0));
                town.getClaimBlockKeys().add(TownBlock.keyOf("minecraft:overworld", townIndex + 1, 0));
                townIndex += 2;
                nation.getTownUuids().add(town.getUuid());
                core.registerTown(town);
                core.putTownBlock(new TownBlock("minecraft:overworld", townIndex - 2, 0, town.getUuid()));
                core.putTownBlock(new TownBlock("minecraft:overworld", townIndex - 1, 0, town.getUuid()));
                claimIndex += 2;
                if (claimIndex >= CLAIMS) {
                    break;
                }
            }
            core.saveNation(nation);
            core.saveTownBlocks();

            FestivalService festival = new FestivalService(config, () -> LocalDate.of(2026, 1, 6));
            InfluenceService influence = new InfluenceService(core, config, festival);
            long start = System.nanoTime();
            influence.applyDailyGrowth();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            System.out.println("P8_STRESS towns=" + TOWNS + " claims=" + CLAIMS
                    + " settlement_ms=" + elapsedMs);
            assertThat(elapsedMs).isLessThan(THRESHOLD_MS);
        } catch (Exception e) {
            throw new AssertionError(e);
        } finally {
            storage.close();
        }
    }
}