package com.geotowny.domain.market;

import com.geotowny.service.MarketService;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ShopCatalogTest {

    @Test
    void catalogIsUniqueVanillaAndPriced() {
        List<MarketItem> items = ShopCatalog.defaults();
        assertThat(items).hasSizeGreaterThan(80);

        Set<String> ids = new HashSet<>();
        for (MarketItem item : items) {
            assertThat(item.getItemId()).startsWith("minecraft:");
            assertThat(ids.add(item.getItemId())).as(item.getItemId()).isTrue();
            assertThat(item.getDisplayName()).isNotBlank();
            assertThat(item.getBasePrice().isPositive()).as(item.getItemId()).isTrue();
        }
    }

    @Test
    void everyCatalogItemResolvesToMaterial() {
        for (MarketItem item : ShopCatalog.defaults()) {
            assertThat(MarketService.resolveMaterial(item.getItemId()))
                    .as(item.getItemId()).isNotNull();
        }
    }
}