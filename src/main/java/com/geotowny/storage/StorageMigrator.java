package com.geotowny.storage;

import com.geotowny.Log;
import com.geotowny.domain.model.Nation;
import com.geotowny.domain.model.PlotType;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.Town;
import com.geotowny.domain.model.TownBlock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 数据迁移：备份、规范化与折叠报告。
 * <p>读取阶段由 {@link MoneyAdapter} 与 {@link SqliteStore} 兼容旧格式；
 * 本类负责把加载后的数据规范化为 P2 目标模型，并在首次迁移前备份。
 */
public final class StorageMigrator {

    public static final int CURRENT_SCHEMA = 1;

    private StorageMigrator() {
    }

    /**
     * 首次启动且备份目录为空时备份现有数据，避免迁移不可逆。
     */
    public static void backupOnce(StorageLayout layout) {
        if (hasExistingBackup(layout.backupDir())) {
            return;
        }
        Path backupRoot = layout.backupDir().resolve("initial-" + Instant.now().toEpochMilli());
        if (hasAnyData(layout)) {
            try {
                Files.createDirectories(backupRoot);
                copyIfExists(layout.data(), backupRoot.resolve("data"));
                copyIfExists(layout.alliancesFile(), backupRoot.resolve("alliances.json"));
                copyIfExists(layout.residentsDb(), backupRoot.resolve("residents.db"));
                Log.info("迁移前备份已创建: {}", backupRoot);
            } catch (IOException e) {
                Log.error("创建迁移备份失败: {}", e.getMessage());
            }
        }
    }

    public static NormalizationReport normalize(DataSnapshot snapshot) {
        int plotTypes = 0;
        for (Map.Entry<String, TownBlock> entry : snapshot.townBlocks().entrySet()) {
            TownBlock block = entry.getValue();
            PlotType type = block.getPlotType();
            if (type == PlotType.JAIL || type == PlotType.WILDS || type == PlotType.CAPITAL_CORE) {
                block.setPlotType(PlotType.DEFAULT);
                plotTypes++;
            }
        }

        int orphanTowns = 0;
        for (Resident resident : snapshot.residents().values()) {
            if (resident.getTownUuid() != null && !snapshot.towns().containsKey(resident.getTownUuid())) {
                resident.leaveTown();
                orphanTowns++;
            }
        }

        int orphanBlocks = 0;
        for (Map.Entry<String, TownBlock> entry : snapshot.townBlocks().entrySet()) {
            TownBlock block = entry.getValue();
            if (block.getTownUuid() == null || !snapshot.towns().containsKey(block.getTownUuid())) {
                orphanBlocks++;
            }
        }

        int capitalIssues = 0;
        for (Nation nation : snapshot.nations().values()) {
            String capital = nation.getCapitalTownUuid();
            if (capital != null && !snapshot.towns().containsKey(capital)) {
                nation.setCapitalTownUuid(null);
                capitalIssues++;
            }
        }

        int legacies = 0;
        for (Town town : snapshot.towns().values()) {
            if (town.getNationUuid() != null && !snapshot.nations().containsKey(town.getNationUuid())) {
                town.setNationUuid(null);
                town.setCapital(false);
                legacies++;
            }
        }

        return new NormalizationReport(plotTypes, orphanTowns, orphanBlocks, capitalIssues, legacies);
    }

    public record NormalizationReport(
            int plotTypesRemapped,
            int residentsDetachedFromMissingTowns,
            int orphanBlocks,
            int capitalsCleared,
            int townsDetachedFromMissingNations
    ) {
        public boolean hasChanges() {
            return plotTypesRemapped > 0 || residentsDetachedFromMissingTowns > 0
                    || orphanBlocks > 0 || capitalsCleared > 0 || townsDetachedFromMissingNations > 0;
        }

        public String summary() {
            return "plotType 重映射=" + plotTypesRemapped
                    + ", 居民脱离不存在城镇=" + residentsDetachedFromMissingTowns
                    + ", 孤儿领地=" + orphanBlocks
                    + ", 失效首都=" + capitalsCleared
                    + ", 城镇脱离不存在国家=" + townsDetachedFromMissingNations;
        }
    }

    private static boolean hasAnyData(StorageLayout layout) {
        return Files.exists(layout.data()) || Files.exists(layout.alliancesFile()) || Files.exists(layout.residentsDb());
    }

    private static boolean hasExistingBackup(Path backupDir) {
        if (!Files.isDirectory(backupDir)) {
            return false;
        }
        try (Stream<Path> stream = Files.list(backupDir)) {
            return stream.findAny().isPresent();
        } catch (IOException e) {
            return false;
        }
    }

    private static void copyIfExists(Path source, Path target) throws IOException {
        if (!Files.exists(source)) {
            return;
        }
        if (Files.isDirectory(source)) {
            Files.walk(source).forEach(path -> {
                try {
                    Path relative = source.relativize(path);
                    Path destination = target.resolve(relative.toString());
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.createDirectories(destination.getParent());
                        Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    Log.error("备份文件失败 {}: {}", path, e.getMessage());
                }
            });
            return;
        }
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
}