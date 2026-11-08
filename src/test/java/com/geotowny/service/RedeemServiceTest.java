package com.geotowny.service;

import com.geotowny.config.PluginConfig;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.money.Money;
import com.geotowny.domain.redeem.RedeemCode;
import com.geotowny.storage.RedeemStore;
import com.geotowny.storage.StorageLayout;
import com.geotowny.util.Result;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedeemServiceTest {

    @TempDir
    Path tempDir;

    private RedeemService service;
    private RedeemStore store;
    private Resident resident;
    private Player player;

    @BeforeEach
    void setUp() {
        PluginConfig config = new PluginConfig(new YamlConfiguration());
        store = new RedeemStore(new StorageLayout(tempDir.resolve("plugin")));
        service = new RedeemService(config, store, value -> {
        });
        service.load();
        UUID uuid = UUID.randomUUID();
        resident = new Resident(uuid, "Alice", Money.ZERO);
        player = proxyPlayer(uuid);
    }

    @Test
    void createsAndRedeemsGeoCodeOnce() {
        RedeemCode code = service.createGeo("GEO1", Money.of(100.0), 1);
        assertThat(code).isNotNull();
        assertThat(service.createGeo("GEO1", Money.of(1.0), 1)).isNull();

        Result result = service.redeem(player, resident, "geo1");

        assertThat(result.isSuccess()).isTrue();
        assertThat(resident.getBalance()).isEqualTo(Money.of(100.0));
        assertThat(service.getCode("GEO1").getUsedCount()).isEqualTo(1);
        assertThat(service.getCode("GEO1").getUsedByPlayerUuids())
                .contains(resident.getUuidString());

        Result second = service.redeem(player, resident, "GEO1");
        assertThat(second.isSuccess()).isFalse();
        assertThat(second.getMessage()).contains("已经使用");
        assertThat(resident.getBalance()).isEqualTo(Money.of(100.0));
    }

    @Test
    void rejectsUnknownExpiredAndExhaustedCodes() {
        assertThat(service.redeem(player, resident, "MISSING").getMessage()).contains("不存在");

        RedeemCode expired = service.createGeo("EXPIRED", Money.of(10.0), 1);
        expired.setExpiryDate(System.currentTimeMillis() - 1000);
        service.save();
        assertThat(service.redeem(player, resident, "EXPIRED").getMessage()).contains("过期");

        RedeemCode exhausted = service.createGeo("EXHAUSTED", Money.of(10.0), 1);
        exhausted.setUsedCount(1);
        service.save();
        assertThat(service.redeem(player, resident, "EXHAUSTED").getMessage()).contains("上限");
    }

    @Test
    void generatesReadableCodes() {
        String code = service.generateRandomCode();
        assertThat(code).hasSize(8);
        assertThat(code).matches("[A-HJ-NP-Z2-9]+");
    }

    @Test
    void persistsCodesAndUsageAcrossReload() {
        service.createGeo("PERSIST", Money.of(25.0), 2);
        service.redeem(player, resident, "PERSIST");
        service.save();

        RedeemService reloaded = new RedeemService(
                new PluginConfig(new YamlConfiguration()), store, value -> {
        });
        reloaded.load();

        RedeemCode code = reloaded.getCode("PERSIST");
        assertThat(code).isNotNull();
        assertThat(code.getUsedCount()).isEqualTo(1);
        assertThat(code.getUsedByPlayerUuids()).contains(resident.getUuidString());
    }

    @Test
    void canSetMaxUses() {
        service.createGeo("MAX", Money.of(10.0), 1);
        assertThat(service.setMaxUses("MAX", 5)).isTrue();
        assertThat(service.getCode("MAX").getMaxUses()).isEqualTo(5);
        assertThat(service.setMaxUses("NONE", 5)).isFalse();
    }

    private static Player proxyPlayer(UUID uuid) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) {
                        return uuid;
                    }
                    if (method.getName().equals("getName")) {
                        return "Alice";
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType == boolean.class) {
                        return false;
                    }
                    if (returnType == int.class) {
                        return 0;
                    }
                    if (returnType == long.class) {
                        return 0L;
                    }
                    if (returnType == double.class) {
                        return 0.0d;
                    }
                    return null;
                });
    }
}