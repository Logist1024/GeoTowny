package com.geotowny.service;

import com.geotowny.config.PluginConfig;
import com.geotowny.domain.market.MarketItem;
import com.geotowny.domain.market.ShopCatalog;
import com.geotowny.domain.money.Money;
import com.geotowny.storage.MarketStore;
import com.geotowny.storage.StorageLayout;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MarketServiceTest {

    @TempDir
    Path tempDir;

    private MarketService service;

    @BeforeEach
    void setUp() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("market.shop.daily-random-range", 0.0);
        PluginConfig config = new PluginConfig(yaml);
        MarketStore store = new MarketStore(new StorageLayout(tempDir.resolve("plugin")));
        service = new MarketService(null, config, store);
        service.load();
        service.initializeDay(100L);
    }

    @Test
    void loadsCatalogAndBasePrices() {
        assertThat(service.items()).hasSize(ShopCatalog.defaults().size());
        MarketItem wheat = service.find("minecraft:wheat");
        assertThat(wheat).isNotNull();
        assertThat(wheat.getBasePrice()).isEqualTo(Money.of(8.0));
        assertThat(service.currentBuyPrice(wheat)).isEqualTo(Money.of(8.0));
        assertThat(service.currentSellPrice(wheat)).isEqualTo(Money.of(6.8));
    }

    @Test
    void netBuyingRaisesPriceAndNetSellingLowersIt() {
        MarketItem wheat = service.find("minecraft:wheat");
        wheat.setTotalBought(100);
        assertThat(service.currentBuyPrice(wheat)).isEqualTo(Money.of(24.0));

        wheat.setTotalBought(0);
        wheat.setTotalSold(100);
        assertThat(service.currentBuyPrice(wheat)).isEqualTo(Money.of(0.8));
        assertThat(service.currentSellPrice(wheat)).isEqualTo(Money.of(0.68));
    }

    @Test
    void dailyResetDecaysVolumeAndClearsDailyCounters() {
        MarketItem wheat = service.find("minecraft:wheat");
        wheat.setTotalBought(1000);
        wheat.setTotalSold(500);
        wheat.setDailyBought(10);
        wheat.setDailySold(5);
        service.save();

        assertThat(service.resetDaily(101L)).isTrue();

        assertThat(wheat.getTotalBought()).isEqualTo(980);
        assertThat(wheat.getTotalSold()).isEqualTo(490);
        assertThat(wheat.getDailyBought()).isZero();
        assertThat(wheat.getDailySold()).isZero();
        assertThat(service.lastResetDay()).isEqualTo(101L);
        assertThat(service.resetDaily(101L)).isFalse();
    }

    @Test
    void persistsStateAcrossReload() {
        MarketItem wheat = service.find("minecraft:wheat");
        wheat.setTotalBought(123);
        wheat.setTotalSold(45);
        service.save();

        MarketStore store = new MarketStore(new StorageLayout(tempDir.resolve("plugin")));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("market.shop.daily-random-range", 0.0);
        MarketService reloaded = new MarketService(null, new PluginConfig(yaml), store);
        reloaded.load();

        MarketItem restored = reloaded.find("minecraft:wheat");
        assertThat(restored.getTotalBought()).isEqualTo(123);
        assertThat(restored.getTotalSold()).isEqualTo(45);
        assertThat(restored.getBasePrice()).isEqualTo(Money.of(8.0));
        assertThat(reloaded.lastResetDay()).isEqualTo(100L);
    }

    @Test
    void adminCanAdjustAndResetPrice() {
        assertThat(service.adjustPrice("minecraft:wheat", 1.5).isSuccess()).isTrue();
        assertThat(service.currentBuyPrice(service.find("minecraft:wheat"))).isEqualTo(Money.of(12.0));

        assertThat(service.resetPrice("minecraft:wheat").isSuccess()).isTrue();
        assertThat(service.currentBuyPrice(service.find("minecraft:wheat"))).isEqualTo(Money.of(8.0));

        MarketItem wheat = service.find("minecraft:wheat");
        wheat.setTotalBought(100);
        assertThat(service.resetVolume("minecraft:wheat").isSuccess()).isTrue();
        assertThat(wheat.getTotalBought()).isZero();
        assertThat(service.currentBuyPrice(wheat)).isEqualTo(Money.of(8.0));
    }

    @Test
    void quotasUseConfiguredLimits() {
        assertThat(service.playerRemainingBuy("player")).isEqualTo(512);
        assertThat(service.playerRemainingSell("player")).isEqualTo(512);
        assertThat(service.serverRemainingBuy()).isEqualTo(8192);
        assertThat(service.serverRemainingSell()).isEqualTo(8192);
    }

    @Test
    void priceNeverDropsBelowOneCent() {
        MarketItem wheat = service.find("minecraft:wheat");
        wheat.setBasePrice(Money.ofCents(1));
        wheat.setTotalSold(1000);
        assertThat(service.currentBuyPrice(wheat)).isEqualTo(Money.ofCents(1));
        assertThat(service.currentSellPrice(wheat)).isEqualTo(Money.ofCents(1));
    }

    @Test
    void resolvesVanillaMaterials() {
        assertThat(MarketService.resolveMaterial("minecraft:wheat")).isEqualTo(Material.WHEAT);
        assertThat(MarketService.resolveMaterial("wheat")).isEqualTo(Material.WHEAT);
        assertThat(MarketService.resolveMaterial("minecraft:not_a_real_item")).isNull();
    }
}