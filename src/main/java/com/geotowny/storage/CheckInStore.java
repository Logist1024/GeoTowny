package com.geotowny.storage;

import com.geotowny.Log;
import com.geotowny.domain.checkin.CheckInRecord;
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
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public final class CheckInStore {

    private static final Type RECORD_MAP = new TypeToken<Map<String, CheckInRecord>>() {
    }.getType();

    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final ReentrantLock writeLock = new ReentrantLock();

    public CheckInStore(StorageLayout layout) {
        this.file = layout.checkInsFile();
    }

    public Map<String, CheckInRecord> load() {
        if (!Files.exists(file)) {
            return new HashMap<>();
        }
        try {
            Map<String, CheckInRecord> loaded = gson.fromJson(Files.readString(file), RECORD_MAP);
            return loaded == null ? new HashMap<>() : loaded;
        } catch (IOException | RuntimeException e) {
            backupCorrupt();
            Log.error("读取签到数据失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    public void save(Map<String, CheckInRecord> records) {
        if (records == null) {
            return;
        }
        writeLock.lock();
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, gson.toJson(records, RECORD_MAP),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Log.error("保存签到数据失败: {}", e.getMessage());
        } finally {
            try {
                Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".tmp"));
            } catch (IOException ignored) {
                // 临时文件清理失败不影响主流程。
            }
            writeLock.unlock();
        }
    }

    public Path file() {
        return file;
    }

    private void backupCorrupt() {
        if (!Files.exists(file)) {
            return;
        }
        Path backup = file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis());
        try {
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            Log.warn("已备份损坏的签到文件到 {}", backup.getFileName());
        } catch (IOException e) {
            Log.error("备份损坏签到文件失败: {}", e.getMessage());
        }
    }
}