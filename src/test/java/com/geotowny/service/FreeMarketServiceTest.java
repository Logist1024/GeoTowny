package com.geotowny.service;

import com.geotowny.config.PluginConfig;
import com.geotowny.domain.market.FreeMarketSnapshot;
import com.geotowny.domain.market.Listing;
import com.geotowny.domain.money.Money;
import com.geotowny.storage.FreeMarketStore;
import com.geotowny.storage.StorageLayout;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FreeMarketServiceTest {

    @TempDir
    Path tempDir;

    private FreeMarketStore store;
    private FreeMarketService service;

    @BeforeEach
    void setUp() {
        PluginConfig config = new PluginConfig(new YamlConfiguration());
        store = new FreeMarketStore(new StorageLayout(tempDir.resolve("plugin")));
        service = new FreeMarketService(null, config, store);
        service.load();
    }

    @Test
    void listingTotalUsesCents() {
        Listing listing = new Listing(1, "seller", "Seller", "minecraft:stone", "石头",
                "encoded-item", 3, Money.of(2.5), 100L);
        assertThat(listing.totalPrice()).isEqualTo(Money.of(7.5));
    }

    @Test
    void loadsValidListingAndIndexesBySeller() {
        saveSnapshot(validListing(7));

        service.load();

        assertThat(service.allListings()).hasSize(1);
        Listing listing = service.find(7);
        assertThat(listing).isNotNull();
        assertThat(listing.getUnitPrice()).isEqualTo(Money.of(2.5));
        assertThat(listing.totalPrice()).isEqualTo(Money.of(7.5));
        assertThat(service.countBySeller("seller")).isEqualTo(1);
        assertThat(service.listingsBySeller("seller")).hasSize(1);
        assertThat(service.find(99)).isNull();
    }

    @Test
    void safelyDiscardsLegacyNbtListing() {
        Listing legacy = validListing(8);
        legacy.setItemData(null);
        saveSnapshot(legacy);

        service.load();

        assertThat(service.allListings()).isEmpty();
    }

    @Test
    void persistsListingsAcrossReload() {
        saveSnapshot(validListing(9));

        service.load();

        FreeMarketService reloaded = new FreeMarketService(null,
                new PluginConfig(new YamlConfiguration()), store);
        reloaded.load();

        assertThat(reloaded.find(9)).isNotNull();
        assertThat(reloaded.allListings()).hasSize(1);
    }

    private void saveSnapshot(Listing listing) {
        FreeMarketSnapshot snapshot = new FreeMarketSnapshot();
        snapshot.setListings(new ArrayList<>(List.of(listing)));
        snapshot.setNextId(listing.getId() + 1);
        store.save(snapshot);
    }

    private static Listing validListing(long id) {
        return new Listing(id, "seller", "Seller", "minecraft:stone", "石头",
                "encoded-item", 3, Money.of(2.5), 100L);
    }
}