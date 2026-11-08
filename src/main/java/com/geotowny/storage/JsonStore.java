package com.geotowny.storage;

import com.geotowny.Log;
import com.geotowny.domain.money.Money;
import com.geotowny.domain.model.Alliance;
import com.geotowny.domain.model.Nation;
import com.geotowny.domain.model.Town;
import com.geotowny.domain.model.TownBlock;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * JSON 存储：城镇、国家、领地、联盟。
 * <p>兼容旧 Fabric 数据格式：金额由 {@link MoneyAdapter} 读取 double，枚举忽略未知值。
 * 写入使用临时文件 + 原子移动，单实体文件附加 {@code schemaVersion}。
 */
public final class JsonStore {

    private static final Type TOWN_BLOCK_MAP = new TypeToken<Map<String, TownBlock>>() {
    }.getType();
    private static final Type ALLIANCE_MAP = new TypeToken<Map<String, Alliance>>() {
    }.getType();
    private static final Type ALLIANCE_LIST = new TypeToken<List<Alliance>>() {
    }.getType();

    private final StorageLayout layout;
    private final Gson gson;
    private final ReentrantLock writeLock = new ReentrantLock();

    public JsonStore(StorageLayout layout) {
        this.layout = layout;
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .registerTypeAdapter(Money.class, new MoneyAdapter())
                .create();
    }

    public Map<String, Town> loadTowns() {
        return loadDirectory(layout.townsDir(), Town.class, Town::getUuid);
    }

    public Map<String, Nation> loadNations() {
        return loadDirectory(layout.nationsDir(), Nation.class, Nation::getUuid);
    }

    public Map<String, TownBlock> loadTownBlocks() {
        return loadMapFile(layout.townBlocksFile(), TOWN_BLOCK_MAP);
    }

    public Map<String, Alliance> loadAlliances() {
        Path file = layout.alliancesFile();
        if (!Files.exists(file)) {
            return Map.of();
        }
        try {
            String text = Files.readString(file);
            try {
                Map<String, Alliance> map = gson.fromJson(text, ALLIANCE_MAP);
                if (map != null) {
                    return new LinkedHashMap<>(map);
                }
            } catch (RuntimeException ignored) {
                // 旧版本可能以数组保存联盟，回退解析。
            }
            List<Alliance> list = gson.fromJson(text, ALLIANCE_LIST);
            Map<String, Alliance> result = new LinkedHashMap<>();
            if (list != null) {
                for (Alliance alliance : list) {
                    if (alliance != null && alliance.getUuid() != null) {
                        result.put(alliance.getUuid(), alliance);
                    }
                }
            }
            return result;
        } catch (IOException | RuntimeException e) {
            backupCorrupt(file);
            Log.error("读取联盟文件失败: {}", e.getMessage());
            return Map.of();
        }
    }

    public void saveTown(Town town) {
        if (town == null || town.getUuid() == null) {
            Log.warn("城镇 UUID 为空，跳过保存。");
            return;
        }
        writeEntity(layout.townsDir().resolve(town.getUuid() + ".json"), town);
    }

    public void saveNation(Nation nation) {
        if (nation == null || nation.getUuid() == null) {
            Log.warn("国家 UUID 为空，跳过保存。");
            return;
        }
        writeEntity(layout.nationsDir().resolve(nation.getUuid() + ".json"), nation);
    }

    public void saveTownBlocks(Map<String, TownBlock> blocks) {
        writeJson(layout.townBlocksFile(), blocks);
    }

    public void saveAlliances(Map<String, Alliance> alliances) {
        writeJson(layout.alliancesFile(), alliances);
    }

    public void deleteTown(String uuid) {
        delete(layout.townsDir().resolve(uuid + ".json"));
    }

    public void deleteNation(String uuid) {
        delete(layout.nationsDir().resolve(uuid + ".json"));
    }

    public void saveSnapshot(DataSnapshot snapshot) {
        snapshot.towns().values().forEach(this::saveTown);
        snapshot.nations().values().forEach(this::saveNation);
        saveTownBlocks(snapshot.townBlocks());
        saveAlliances(snapshot.alliances());
    }

    private <T> Map<String, T> loadDirectory(Path dir, Class<T> type, Function<T, String> idExtractor) {
        if (!Files.exists(dir)) {
            return Map.of();
        }
        Map<String, T> result = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(path -> path.toString().endsWith(".json")).forEach(path -> {
                try {
                    T value = gson.fromJson(Files.readString(path), type);
                    if (value == null) {
                        return;
                    }
                    String id = idExtractor.apply(value);
                    if (id != null) {
                        result.put(id, value);
                    }
                } catch (IOException | RuntimeException e) {
                    backupCorrupt(path);
                    Log.error("读取文件失败 {}: {}", path.getFileName(), e.getMessage());
                }
            });
        } catch (IOException e) {
            Log.error("列举目录失败 {}: {}", dir, e.getMessage());
        }
        return result;
    }

    private <T> Map<String, T> loadMapFile(Path file, Type type) {
        if (!Files.exists(file)) {
            return Map.of();
        }
        try {
            Map<String, T> map = gson.fromJson(Files.readString(file), type);
            return map == null ? Map.of() : new LinkedHashMap<>(map);
        } catch (IOException | RuntimeException e) {
            backupCorrupt(file);
            Log.error("读取数据文件失败 {}: {}", file.getFileName(), e.getMessage());
            return Map.of();
        }
    }

    private void writeEntity(Path target, Object value) {
        JsonElement tree = gson.toJsonTree(value);
        if (tree.isJsonObject()) {
            JsonObject object = tree.getAsJsonObject();
            object.addProperty("schemaVersion", StorageMigrator.CURRENT_SCHEMA);
        }
        writeJson(target, tree);
    }

    private void writeJson(Path target, Object value) {
        writeJson(target, gson.toJsonTree(value));
    }

    private void writeJson(Path target, JsonElement tree) {
        writeLock.lock();
        try {
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(tmp, gson.toJson(tree),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Log.error("保存文件失败 {}: {}", target.getFileName(), e.getMessage());
        } finally {
            try {
                Files.deleteIfExists(target.resolveSibling(target.getFileName() + ".tmp"));
            } catch (IOException ignored) {
                // 临时文件清理失败不影响主流程。
            }
            writeLock.unlock();
        }
    }

    private void delete(Path target) {
        writeLock.lock();
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            Log.error("删除文件失败 {}: {}", target.getFileName(), e.getMessage());
        } finally {
            writeLock.unlock();
        }
    }

    private void backupCorrupt(Path file) {
        if (file == null || !Files.exists(file)) {
            return;
        }
        Path backup = file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis());
        try {
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            Log.warn("已备份损坏文件到 {}", backup.getFileName());
        } catch (IOException e) {
            Log.error("备份损坏文件失败: {}", e.getMessage());
        }
    }

    public Gson gson() {
        return gson;
    }

    @Override
    public String toString() {
        return "JsonStore{" + Objects.requireNonNullElse(layout.root(), "?") + "}";
    }
}