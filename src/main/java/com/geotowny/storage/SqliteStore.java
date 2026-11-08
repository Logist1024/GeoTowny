package com.geotowny.storage;

import com.geotowny.Log;
import com.geotowny.domain.model.NationRank;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.TownRank;
import com.geotowny.domain.money.Money;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SQLite 存储：居民数据。
 * <p>旧表兼容：{@code town_uuids} 为 JSON 数组、{@code town_rank} 可能为 JSON Map、
 * {@code balance} 为 REAL。迁移后统一写单值 town_uuid 与 balance_cents。
 */
public final class SqliteStore implements AutoCloseable {

    public static final int SCHEMA_VERSION = 1;

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS residents (
                uuid TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                town_uuid TEXT,
                town_uuids TEXT,
                town_rank TEXT,
                nation_rank TEXT,
                balance REAL NOT NULL DEFAULT 0,
                balance_cents INTEGER,
                registered INTEGER NOT NULL,
                last_online INTEGER NOT NULL,
                password_salt TEXT,
                password_hash TEXT
            )
            """;

    private static final String UPSERT = """
            INSERT INTO residents(uuid, name, town_uuid, town_rank, nation_rank, balance, balance_cents, registered, last_online, password_hash)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                name=excluded.name,
                town_uuid=excluded.town_uuid,
                town_uuids=NULL,
                town_rank=excluded.town_rank,
                nation_rank=excluded.nation_rank,
                balance=excluded.balance,
                balance_cents=excluded.balance_cents,
                registered=excluded.registered,
                last_online=excluded.last_online,
                password_hash=excluded.password_hash
            """;

    private static final Type STRING_LIST = new TypeToken<List<String>>() {
    }.getType();
    private static final Type RANK_MAP = new TypeToken<Map<String, String>>() {
    }.getType();

    private final Path dbPath;
    private final Gson gson = new Gson();
    private final ReentrantLock writeLock = new ReentrantLock();

    private Connection connection;
    private volatile boolean closed;

    public SqliteStore(Path dbPath) {
        this.dbPath = dbPath;
    }

    public void init() throws SQLException, IOException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC 驱动缺失，请确认依赖已正确打包。", e);
        }
        this.closed = false;
        Files.createDirectories(dbPath.getParent());
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("PRAGMA journal_mode=WAL");
            statement.executeUpdate("PRAGMA busy_timeout=30000");
            statement.executeUpdate("PRAGMA synchronous=NORMAL");
            statement.executeUpdate(CREATE_TABLE);
        }
        ensureColumn("balance_cents", "ALTER TABLE residents ADD COLUMN balance_cents INTEGER");
        migrateBalance();
    }

    public ResidentLoadResult loadResidents() {
        Map<String, Resident> residents = new LinkedHashMap<>();
        List<FoldReport> folds = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM residents")) {
            while (rs.next()) {
                Resident resident = mapRow(rs, folds);
                residents.put(resident.getUuidString(), resident);
            }
        } catch (SQLException e) {
            Log.error("读取居民数据失败: {}", e.getMessage());
        }
        return new ResidentLoadResult(residents, folds);
    }

    public Resident findResident(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM residents WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs, null) : null;
            }
        } catch (SQLException e) {
            Log.error("查找居民失败 {}: {}", uuid, e.getMessage());
            return null;
        }
    }

    public Resident findResidentByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM residents WHERE LOWER(name) = LOWER(?)")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs, null) : null;
            }
        } catch (SQLException e) {
            Log.error("按名称查找居民失败 {}: {}", name, e.getMessage());
            return null;
        }
    }

    public void saveResident(Resident resident) {
        saveAll(List.of(resident));
    }

    public void saveAll(Collection<Resident> residents) {
        if (residents == null || residents.isEmpty() || closed) {
            return;
        }
        writeLock.lock();
        try {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(UPSERT)) {
                for (Resident resident : residents) {
                    if (resident == null || resident.getUuidString() == null) {
                        continue;
                    }
                    bind(ps, resident);
                    ps.addBatch();
                }
                ps.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            Log.error("保存居民数据失败: {}", e.getMessage());
        } finally {
            writeLock.unlock();
        }
    }

    private void bind(PreparedStatement ps, Resident resident) throws SQLException {
        ps.setString(1, resident.getUuidString());
        ps.setString(2, resident.getName());
        ps.setString(3, resident.getTownUuid());
        ps.setString(4, resident.getTownRank() == null ? null : resident.getTownRank().name());
        ps.setString(5, resident.getNationRank() == null ? null : resident.getNationRank().name());
        ps.setDouble(6, resident.getBalance().doubleValue());
        ps.setLong(7, resident.getBalance().cents());
        ps.setLong(8, resident.getRegistered());
        ps.setLong(9, resident.getLastOnline());
        ps.setString(10, resident.getWebPassword());
    }

    private Resident mapRow(ResultSet rs, List<FoldReport> folds) throws SQLException {
        Resident resident = new Resident();
        resident.setUuidString(rs.getString("uuid"));
        resident.setName(rs.getString("name"));

        String townUuid = rs.getString("town_uuid");
        List<String> townUuids = parseStringList(rs.getString("town_uuids"));
        if ((townUuid == null || townUuid.isBlank()) && !townUuids.isEmpty()) {
            townUuid = townUuids.get(0);
        }
        if (townUuid != null && !townUuid.isBlank()) {
            resident.setTownUuid(townUuid);
            if (folds != null) {
                String keptTownUuid = townUuid;
                List<String> dropped = townUuids.stream()
                        .filter(uuid -> !uuid.equals(keptTownUuid))
                        .toList();
                if (!dropped.isEmpty()) {
                    folds.add(new FoldReport(resident.getUuidString(), resident.getName(), townUuid, dropped));
                }
            }
        }

        resident.setTownRank(parseTownRank(rs.getString("town_rank"), townUuid));
        resident.setNationRank(parseNationRank(rs.getString("nation_rank")));
        resident.setBalance(readBalance(rs));
        resident.setRegistered(rs.getLong("registered"));
        resident.setLastOnline(rs.getLong("last_online"));
        try {
            resident.setWebPassword(rs.getString("password_hash"));
        } catch (SQLException ignored) {
            // 旧表可能没有密码列。
        }
        return resident;
    }

    private Money readBalance(ResultSet rs) {
        try {
            long cents = rs.getLong("balance_cents");
            if (!rs.wasNull()) {
                return Money.ofCents(cents);
            }
        } catch (SQLException ignored) {
            // 列不存在时回退。
        }
        try {
            return Money.of(rs.getDouble("balance"));
        } catch (SQLException e) {
            return Money.ZERO;
        }
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> list = gson.fromJson(json, STRING_LIST);
            if (list == null) {
                return List.of();
            }
            return list.stream().filter(value -> value != null && !value.isBlank()).toList();
        } catch (RuntimeException e) {
            Log.warn("town_uuids 解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    private TownRank parseTownRank(String raw, String townUuid) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.startsWith("{")) {
            try {
                Map<String, String> ranks = gson.fromJson(value, RANK_MAP);
                if (ranks == null || ranks.isEmpty()) {
                    return null;
                }
                String rank = townUuid == null ? null : ranks.get(townUuid);
                if (rank == null) {
                    rank = ranks.values().stream().filter(v -> v != null && !v.isBlank()).findFirst().orElse(null);
                }
                return rank == null ? null : safeTownRank(rank);
            } catch (RuntimeException e) {
                Log.warn("town_rank JSON 解析失败: {}", e.getMessage());
                return null;
            }
        }
        return safeTownRank(value);
    }

    private TownRank safeTownRank(String value) {
        try {
            return TownRank.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            Log.warn("未知城镇职位: {}", value);
            return null;
        }
    }

    private NationRank parseNationRank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return NationRank.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            Log.warn("未知国家职位: {}", value);
            return null;
        }
    }

    private void ensureColumn(String column, String ddl) throws SQLException {
        boolean exists = false;
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA table_info(residents)")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    exists = true;
                    break;
                }
            }
        }
        if (!exists) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(ddl);
                Log.info("数据库迁移：新增列 {}。", column);
            }
        }
    }

    private void migrateBalance() throws SQLException {
        if (userVersion() >= SCHEMA_VERSION) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE residents SET balance_cents = CAST(ROUND(balance * 100) AS INTEGER) WHERE balance_cents IS NULL");
            statement.executeUpdate("PRAGMA user_version = " + SCHEMA_VERSION);
        }
        Log.info("数据库已迁移到 schema v{}（金额改为分）。", SCHEMA_VERSION);
    }

    private int userVersion() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA user_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            Log.error("关闭数据库失败: {}", e.getMessage());
        }
    }

    public record ResidentLoadResult(Map<String, Resident> residents, List<FoldReport> folds) {
    }

    /**
     * 多城镇折叠报告：旧数据中一名居民属于多个城镇时，保留 town_uuid，丢弃其余。
     */
    public record FoldReport(String uuid, String name, String keptTownUuid, List<String> droppedTownUuids) {
    }
}