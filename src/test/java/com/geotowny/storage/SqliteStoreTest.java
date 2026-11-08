package com.geotowny.storage;

import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.TownRank;
import com.geotowny.domain.money.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteStoreTest {

    @TempDir
    Path tempDir;

    private Path createLegacyDatabase() throws Exception {
        Path db = tempDir.resolve("residents.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE residents (
                        uuid TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        town_uuid TEXT,
                        town_uuids TEXT,
                        town_rank TEXT,
                        nation_rank TEXT,
                        balance REAL NOT NULL DEFAULT 0,
                        registered INTEGER NOT NULL,
                        last_online INTEGER NOT NULL,
                        password_salt TEXT,
                        password_hash TEXT
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO residents(uuid, name, town_uuid, town_uuids, town_rank, nation_rank, balance, registered, last_online)
                    VALUES ('u1', 'Alice', 't1', '["t1","t2"]', '{"t1":"MAYOR","t2":"RESIDENT"}', 'CITIZEN', 123.45, 1, 2)
                    """);
        }
        return db;
    }

    @Test
    void migratesLegacyMultiTownRowAndBalance() throws Exception {
        Path db = createLegacyDatabase();

        try (SqliteStore store = new SqliteStore(db)) {
            store.init();
            SqliteStore.ResidentLoadResult result = store.loadResidents();

            Resident resident = result.residents().get("u1");
            assertThat(resident).isNotNull();
            assertThat(resident.getTownUuid()).isEqualTo("t1");
            assertThat(resident.getTownRank()).isEqualTo(TownRank.MAYOR);
            assertThat(resident.getBalance()).isEqualTo(Money.of(123.45));
            assertThat(result.folds()).hasSize(1);
            assertThat(result.folds().get(0).droppedTownUuids()).containsExactly("t2");

            store.saveAll(result.residents().values());
        }

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT town_uuid, town_uuids, town_rank, balance_cents FROM residents WHERE uuid = 'u1'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("town_uuid")).isEqualTo("t1");
            assertThat(rs.getString("town_uuids")).isNull();
            assertThat(rs.getString("town_rank")).isEqualTo("MAYOR");
            assertThat(rs.getLong("balance_cents")).isEqualTo(12_345L);
        }
    }

    @Test
    void storesNewResidentsInSingleTownFormat() throws Exception {
        Path db = tempDir.resolve("fresh.db");
        Resident resident = new Resident(java.util.UUID.randomUUID(), "Bob", Money.of(10.0));
        resident.setTownUuid("t9");
        resident.setTownRank(TownRank.RESIDENT);

        try (SqliteStore store = new SqliteStore(db)) {
            store.init();
            store.saveResident(resident);
            Resident loaded = store.findResident(resident.getUuid());

            assertThat(loaded).isNotNull();
            assertThat(loaded.getTownUuid()).isEqualTo("t9");
            assertThat(loaded.getBalance()).isEqualTo(Money.of(10.0));
        }
    }
}