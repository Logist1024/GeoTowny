package com.geotowny.storage;

import com.geotowny.Log;
import com.geotowny.domain.money.Money;
import com.geotowny.domain.redeem.RedeemCode;
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

public final class RedeemStore {

    private static final Type CODE_MAP = new TypeToken<Map<String, RedeemCode>>() {
    }.getType();

    private final Path file;
    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .registerTypeAdapter(Money.class, new MoneyAdapter())
            .create();
    private final ReentrantLock writeLock = new ReentrantLock();

    public RedeemStore(StorageLayout layout) {
        this.file = layout.redeemFile();
    }

    public Map<String, RedeemCode> load() {
        if (!Files.exists(file)) {
            return new HashMap<>();
        }
        try {
            Map<String, RedeemCode> loaded = gson.fromJson(Files.readString(file), CODE_MAP);
            return loaded == null ? new HashMap<>() : loaded;
        } catch (IOException | RuntimeException e) {
            backupCorrupt();
            Log.error("读取兑换码数据失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    public void save(Map<String, RedeemCode> codes) {
        if (codes == null) {
            return;
        }
        writeLock.lock();
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, gson.toJson(codes, CODE_MAP),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Log.error("保存兑换码数据失败: {}", e.getMessage());
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
            Log.warn("已备份损坏的兑换码文件到 {}", backup.getFileName());
        } catch (IOException e) {
            Log.error("备份损坏兑换码文件失败: {}", e.getMessage());
        }
    }
}