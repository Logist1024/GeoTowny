package com.geotowny.storage;

import com.geotowny.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 旧 Fabric 数据导入器。
 * <p>旧布局（Fabric 配置目录）：
 * <pre>
 * geotowny/
 * ├─ data/towns/*.json
 * ├─ data/nations/*.json
 * ├─ data/townblocks.json
 * ├─ alliances.json
 * └─ residents.db
 * </pre>
 * 新布局由 {@link StorageLayout} 定义。
 */
public final class LegacyDataImporter {

    private final StorageLayout target;

    public LegacyDataImporter(StorageLayout target) {
        this.target = target;
    }

    public ImportReport importFrom(Path legacyRoot, boolean dryRun) throws IOException {
        if (legacyRoot == null || !Files.isDirectory(legacyRoot)) {
            throw new IOException("旧数据目录不存在: " + legacyRoot);
        }

        Path legacyData = legacyRoot.resolve("data");
        Path legacyTowns = legacyData.resolve("towns");
        Path legacyNations = legacyData.resolve("nations");
        Path legacyBlocks = legacyData.resolve("townblocks.json");
        Path legacyDb = legacyRoot.resolve("residents.db");
        Path legacyAlliances = legacyRoot.resolve("alliances.json");

        int townCount = countJson(legacyTowns);
        int nationCount = countJson(legacyNations);
        boolean hasBlocks = Files.isRegularFile(legacyBlocks);
        boolean hasDb = Files.isRegularFile(legacyDb);
        boolean hasAlliances = Files.isRegularFile(legacyAlliances);

        List<String> warnings = new ArrayList<>();
        if (townCount == 0) {
            warnings.add("旧数据中没有找到城镇文件。");
        }
        if (!hasDb) {
            warnings.add("旧数据中没有找到 residents.db。");
        }
        if (!hasBlocks) {
            warnings.add("旧数据中没有找到 townblocks.json。");
        }

        ImportReport report = new ImportReport(dryRun, townCount, nationCount, hasBlocks, hasDb, hasAlliances, warnings);
        if (dryRun) {
            Log.info("旧数据导入 dry-run: {}", report.summary());
            return report;
        }

        if (isTargetNotEmpty()) {
            throw new IOException("目标数据目录非空，请先备份并清空 plugins/GeoTowny/data 后再导入。");
        }

        target.createDirectories();
        copyJsonDirectory(legacyTowns, target.townsDir());
        copyJsonDirectory(legacyNations, target.nationsDir());
        if (hasBlocks) {
            Files.copy(legacyBlocks, target.townBlocksFile(), StandardCopyOption.REPLACE_EXISTING);
        }
        if (hasAlliances) {
            Files.copy(legacyAlliances, target.alliancesFile(), StandardCopyOption.REPLACE_EXISTING);
        }
        if (hasDb) {
            Files.copy(legacyDb, target.residentsDb(), StandardCopyOption.REPLACE_EXISTING);
        }

        Log.info("旧数据导入完成: {}", report.summary());
        return report;
    }

    private boolean isTargetNotEmpty() throws IOException {
        return countJson(target.townsDir()) > 0
                || countJson(target.nationsDir()) > 0
                || Files.exists(target.townBlocksFile())
                || Files.exists(target.residentsDb());
    }

    private static int countJson(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return (int) stream.filter(path -> path.toString().endsWith(".json")).count();
        }
    }

    private static void copyJsonDirectory(Path source, Path targetDir) throws IOException {
        if (!Files.isDirectory(source)) {
            return;
        }
        Files.createDirectories(targetDir);
        try (Stream<Path> stream = Files.list(source)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
                Files.copy(path, targetDir.resolve(path.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public record ImportReport(
            boolean dryRun,
            int towns,
            int nations,
            boolean townBlocks,
            boolean residentsDb,
            boolean alliances,
            List<String> warnings
    ) {
        public String summary() {
            return "城镇=" + towns + ", 国家=" + nations
                    + ", townblocks=" + townBlocks
                    + ", residents.db=" + residentsDb
                    + ", alliances=" + alliances
                    + (warnings.isEmpty() ? "" : ", 警告=" + warnings);
        }
    }
}