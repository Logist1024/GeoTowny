package com.geotowny.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 存储路径布局。所有业务数据位于插件数据目录下。
 */
public final class StorageLayout {

    private final Path root;

    public StorageLayout(Path root) {
        this.root = root;
    }

    public Path root() {
        return root;
    }

    public Path data() {
        return root.resolve("data");
    }

    public Path townsDir() {
        return data().resolve("towns");
    }

    public Path nationsDir() {
        return data().resolve("nations");
    }

    public Path townBlocksFile() {
        return data().resolve("townblocks.json");
    }

    public Path townLogsDir() {
        return data().resolve("logs");
    }

    public Path marketFile() {
        return data().resolve("market.json");
    }

    public Path freeMarketFile() {
        return data().resolve("freemarket.json");
    }

    public Path checkInsFile() {
        return data().resolve("checkins.json");
    }

    public Path redeemFile() {
        return data().resolve("redeem_codes.json");
    }

    public Path currencyFile() {
        return data().resolve("physical_currency.json");
    }

    public Path alliancesFile() {
        return root.resolve("alliances.json");
    }

    public Path residentsDb() {
        return root.resolve("residents.db");
    }

    public Path backupDir() {
        return root.resolve("backup");
    }

    public void createDirectories() throws IOException {
        Files.createDirectories(data());
        Files.createDirectories(townsDir());
        Files.createDirectories(nationsDir());
        Files.createDirectories(townLogsDir());
        Files.createDirectories(backupDir());
    }
}