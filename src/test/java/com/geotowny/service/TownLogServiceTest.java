package com.geotowny.service;

import com.geotowny.domain.model.TownLog;
import com.geotowny.storage.StorageLayout;
import com.geotowny.storage.TownLogStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TownLogServiceTest {

    @TempDir
    Path tempDir;

    private TownLogService service;

    @BeforeEach
    void setUp() {
        service = new TownLogService(new TownLogStore(new StorageLayout(tempDir)));
    }

    private TownLog log(long timestamp, String townUuid, String player, TownLog.ActionType action) {
        return new TownLog(timestamp, townUuid, "uuid-" + player, player, "柏林", "德意志",
                action, "minecraft:overworld", 0, 64, 0, "detail");
    }

    @Test
    void addsAndQueriesWithPaginationAndFilters() {
        service.add(log(1000, "t1", "Alice", TownLog.ActionType.BREAK));
        service.add(log(2000, "t1", "Bob", TownLog.ActionType.PLACE));
        service.add(log(3000, "t1", "Alice", TownLog.ActionType.BREAK));

        List<TownLog> page1 = service.query("t1", 1, null, null, 0, 2);
        List<TownLog> page2 = service.query("t1", 2, null, null, 0, 2);

        assertThat(page1).hasSize(2);
        assertThat(page1.get(0).getTimestamp()).isEqualTo(3000);
        assertThat(page1.get(1).getTimestamp()).isEqualTo(2000);
        assertThat(page2).hasSize(1);
        assertThat(page2.get(0).getTimestamp()).isEqualTo(1000);

        assertThat(service.count("t1", "Alice", null, 0)).isEqualTo(2);
        assertThat(service.count("t1", null, "BREAK", 0)).isEqualTo(2);
        assertThat(service.query("t1", 1, "Bob", null, 0, 10)).hasSize(1);
    }

    @Test
    void clearsAndCleansUpOldLogs() {
        long now = System.currentTimeMillis();
        service.add(log(now - 10L * 86_400_000L, "t1", "Alice", TownLog.ActionType.BREAK));
        service.add(log(now, "t1", "Bob", TownLog.ActionType.PLACE));

        int removed = service.cleanup(7);

        assertThat(removed).isEqualTo(1);
        assertThat(service.count("t1", null, null, 0)).isEqualTo(1);

        service.clear("t1");
        assertThat(service.count("t1", null, null, 0)).isZero();
    }
}