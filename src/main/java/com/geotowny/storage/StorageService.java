package com.geotowny.storage;

import com.geotowny.Log;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 存储门面：统一加载/保存 JSON 与 SQLite 数据。
 * <p>P2 阶段只提供只读快照与整体保存；P3 起由领域服务接管细粒度写入。
 */
public final class StorageService implements AutoCloseable {

    private final StorageLayout layout;
    private final JsonStore jsonStore;
    private final SqliteStore sqliteStore;

    private DataSnapshot snapshot = DataSnapshot.empty();
    private List<SqliteStore.FoldReport> folds = List.of();
    private StorageMigrator.NormalizationReport normalization = new StorageMigrator.NormalizationReport(0, 0, 0, 0, 0);

    public StorageService(Path pluginDataFolder) {
        this.layout = new StorageLayout(pluginDataFolder);
        this.jsonStore = new JsonStore(layout);
        this.sqliteStore = new SqliteStore(layout.residentsDb());
    }

    public void start() {
        try {
            layout.createDirectories();
            StorageMigrator.backupOnce(layout);
            sqliteStore.init();

            SqliteStore.ResidentLoadResult residentResult = sqliteStore.loadResidents();
            this.folds = List.copyOf(residentResult.folds());

            DataSnapshot loaded = new DataSnapshot(
                    jsonStore.loadTowns(),
                    jsonStore.loadNations(),
                    residentResult.residents(),
                    jsonStore.loadTownBlocks(),
                    jsonStore.loadAlliances());

            this.normalization = StorageMigrator.normalize(loaded);
            this.snapshot = loaded;

            persist();

            Log.info("数据加载完成：城镇={}, 国家={}, 居民={}, 领地={}, 联盟={}。",
                    snapshot.towns().size(), snapshot.nations().size(), snapshot.residents().size(),
                    snapshot.townBlocks().size(), snapshot.alliances().size());
            if (!folds.isEmpty()) {
                Log.warn("检测到 {} 名居民属于多个城镇，已按 D-01 折叠为活动城镇。", folds.size());
                for (SqliteStore.FoldReport fold : folds) {
                    Log.warn("  {} ({}) 保留 {}，丢弃 {}。", fold.name(), fold.uuid(), fold.keptTownUuid(), fold.droppedTownUuids());
                }
            }
            if (normalization.hasChanges()) {
                Log.warn("数据规范化完成：{}", normalization.summary());
            }
        } catch (Exception e) {
            throw new IllegalStateException("数据存储初始化失败: " + e.getMessage(), e);
        }
    }

    public void persist() {
        jsonStore.saveSnapshot(snapshot);
        sqliteStore.saveAll(snapshot.residents().values());
    }

    public void applySnapshot(DataSnapshot snapshot) {
        this.snapshot = snapshot;
        normalizeLoaded(snapshot);
        persist();
    }

    private void normalizeLoaded(DataSnapshot loaded) {
        this.normalization = StorageMigrator.normalize(loaded);
    }

    public DataSnapshot snapshot() {
        return snapshot;
    }

    public List<SqliteStore.FoldReport> folds() {
        return folds;
    }

    public StorageMigrator.NormalizationReport normalization() {
        return normalization;
    }

    public StorageLayout layout() {
        return layout;
    }

    public JsonStore jsonStore() {
        return jsonStore;
    }

    public SqliteStore sqliteStore() {
        return sqliteStore;
    }

    /**
     * 导入旧 Fabric 数据后重新加载。
     */
    public void reload() {
        sqliteStore.close();
        start();
    }

    @Override
    public void close() {
        // 数据由各领域服务在写入时持久化；此处只关闭数据库连接，避免用旧快照覆盖新数据。
        sqliteStore.close();
    }
}