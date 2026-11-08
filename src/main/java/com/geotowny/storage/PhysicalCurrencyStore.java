package com.geotowny.storage;

import com.geotowny.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public final class PhysicalCurrencyStore {

    private static final Type ISSUED_MAP = new TypeToken<Map<String, Long>>() {
    }.getType();

    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final ReentrantLock lock = new ReentrantLock();

    public PhysicalCurrencyStore(StorageLayout layout) {
        this.file = layout.currencyFile();
    }

    public Map<Long, Long> load() {
        Map<Long, Long> result = new HashMap<>();
        if (!Files.exists(file)) {
            return result;
        }
        try {
            Map<String, Long> raw = gson.fromJson(Files.readString(file), ISSUED_MAP);
            if (raw != null) {
                for (Map.Entry<String, Long> entry : raw.entrySet()) {
                    result.put(Long.parseLong(entry.getKey()), entry.getValue());
                }
            }
        } catch (IOException | RuntimeException e) {
            Log.error("读取实体货币发行量失败: {}", e.getMessage());
        }
        return result;
    }

    public void save(Map<Long, Long> issued) {
        if (issued == null) {
            return;
        }
        lock.lock();
        try {
            Files.createDirectories(file.getParent());
            Map<String, Long> raw = new LinkedHashMap<>();
            issued.forEach((key, value) -> raw.put(Long.toString(key), value));
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, gson.toJson(raw, ISSUED_MAP),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Log.error("保存实体货币发行量失败: {}", e.getMessage());
        } finally {
            try {
                Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".tmp"));
            } catch (IOException ignored) {
                // 临时文件清理失败不影响主流程。
            }
            lock.unlock();
        }
    }

    public Path file() {
        return file;
    }
}