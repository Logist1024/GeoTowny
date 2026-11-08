package com.geotowny.storage;

import com.geotowny.domain.model.Town;
import com.geotowny.domain.money.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonStoreTest {

    @TempDir
    Path tempDir;

    private JsonStore store() {
        return new JsonStore(new StorageLayout(tempDir));
    }

    @Test
    void roundTripsTownAndWritesSchemaVersion() throws IOException {
        StorageLayout layout = new StorageLayout(tempDir);
        layout.createDirectories();
        JsonStore store = new JsonStore(layout);

        Town town = new Town();
        town.setUuid("town-1");
        town.setName("柏林");
        town.setBalance(Money.of(123.45));

        store.saveTown(town);

        Map<String, Town> loaded = store.loadTowns();
        assertThat(loaded).containsKey("town-1");
        assertThat(loaded.get("town-1").getBalance()).isEqualTo(Money.of(123.45));
        assertThat(Files.readString(layout.townsDir().resolve("town-1.json")))
                .contains("\"schemaVersion\": 1")
                .contains("\"cents\": 12345");
    }

    @Test
    void readsLegacyDoubleAmount() throws IOException {
        StorageLayout layout = new StorageLayout(tempDir);
        Files.createDirectories(layout.townsDir());
        Files.writeString(layout.townsDir().resolve("old.json"),
                "{\"uuid\":\"old\",\"name\":\"旧城\",\"balance\":100.0,\"taxes\":5.5}");

        Town town = store().loadTowns().get("old");

        assertThat(town).isNotNull();
        assertThat(town.getBalance()).isEqualTo(Money.of(100.0));
        assertThat(town.getTaxes()).isEqualTo(Money.of(5.5));
    }

    @Test
    void backsUpCorruptFileAndContinues() throws IOException {
        StorageLayout layout = new StorageLayout(tempDir);
        Files.createDirectories(layout.townsDir());
        Files.writeString(layout.townsDir().resolve("broken.json"), "{not-json");
        Files.writeString(layout.townsDir().resolve("ok.json"), "{\"uuid\":\"ok\",\"name\":\"好城\"}");

        Map<String, Town> loaded = store().loadTowns();

        assertThat(loaded).containsKey("ok");
        try (var stream = Files.list(layout.townsDir())) {
            assertThat(stream.anyMatch(path -> path.getFileName().toString().contains(".corrupt-"))).isTrue();
        }
    }
}