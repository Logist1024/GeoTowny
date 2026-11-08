package com.geotowny.storage;

import com.geotowny.Log;
import com.geotowny.domain.model.TownLog;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * 城镇日志存储：每个城镇一个 JSON 文件 {@code data/logs/<townUuid>.json}。
 */
public final class TownLogStore {

    private static final Type LOG_LIST = new TypeToken<List<TownLog>>() {
    }.getType();

    private final StorageLayout layout;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final ReentrantLock lock = new ReentrantLock();

    public TownLogStore(StorageLayout layout) {
        this.layout = layout;
    }

    public List<TownLog> load(String townUuid) {
        Path file = fileOf(townUuid);
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try {
            List<TownLog> logs = gson.fromJson(Files.readString(file), LOG_LIST);
            return logs == null ? new ArrayList<>() : new ArrayList<>(logs);
        } catch (IOException | RuntimeException e) {
            Log.warn("读取城镇日志失败 {}: {}", townUuid, e.getMessage());
            return new ArrayList<>();
        }
    }

    public void save(String townUuid, List<TownLog> logs) {
        lock.lock();
        try {
            Files.createDirectories(layout.townLogsDir());
            Path file = fileOf(townUuid);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, gson.toJson(logs),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Log.error("保存城镇日志失败 {}: {}", townUuid, e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    public void delete(String townUuid) {
        try {
            Files.deleteIfExists(fileOf(townUuid));
        } catch (IOException e) {
            Log.error("删除城镇日志失败 {}: {}", townUuid, e.getMessage());
        }
    }

    public List<String> listTownUuids() {
        if (!Files.isDirectory(layout.townLogsDir())) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(layout.townLogsDir())) {
            return stream.filter(path -> path.toString().endsWith(".json"))
                    .map(path -> path.getFileName().toString().replace(".json", ""))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private Path fileOf(String townUuid) {
        return layout.townLogsDir().resolve(townUuid + ".json");
    }
}