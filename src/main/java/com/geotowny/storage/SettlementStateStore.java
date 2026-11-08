package com.geotowny.storage;

import com.geotowny.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * 每日结算状态持久化，保证重启后不重复结算。
 * <p>文件：{@code data/settlement.json}，内容：{@code {"lastSettledDay": 12345}}。
 */
public final class SettlementStateStore {

    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public SettlementStateStore(StorageLayout layout) {
        this.file = layout.data().resolve("settlement.json");
    }

    public long loadLastSettledDay() {
        if (!Files.exists(file)) {
            return 0L;
        }
        try {
            JsonObject object = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            return object.has("lastSettledDay") ? object.get("lastSettledDay").getAsLong() : 0L;
        } catch (IOException | RuntimeException e) {
            Log.warn("读取结算状态失败，按未结算处理: {}", e.getMessage());
            return 0L;
        }
    }

    public void saveLastSettledDay(long day) {
        try {
            Files.createDirectories(file.getParent());
            JsonObject object = new JsonObject();
            object.addProperty("lastSettledDay", day);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, gson.toJson(object),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Log.error("保存结算状态失败: {}", e.getMessage());
        }
    }
}