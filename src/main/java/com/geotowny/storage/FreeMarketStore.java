package com.geotowny.storage;

import com.geotowny.Log;
import com.geotowny.domain.market.FreeMarketSnapshot;
import com.geotowny.domain.money.Money;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.locks.ReentrantLock;

public final class FreeMarketStore {

    private final Path file;
    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .registerTypeAdapter(Money.class, new MoneyAdapter())
            .create();
    private final ReentrantLock writeLock = new ReentrantLock();

    public FreeMarketStore(StorageLayout layout) {
        this.file = layout.freeMarketFile();
    }

    public FreeMarketSnapshot load() {
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return gson.fromJson(Files.readString(file), FreeMarketSnapshot.class);
        } catch (IOException | RuntimeException e) {
            backupCorrupt();
            Log.error("读取自由市场数据失败: {}", e.getMessage());
            return null;
        }
    }

    public void save(FreeMarketSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        writeLock.lock();
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, gson.toJson(snapshot),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Log.error("保存自由市场数据失败: {}", e.getMessage());
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
            Log.warn("已备份损坏的自由市场文件到 {}", backup.getFileName());
        } catch (IOException e) {
            Log.error("备份损坏的自由市场文件失败: {}", e.getMessage());
        }
    }
}