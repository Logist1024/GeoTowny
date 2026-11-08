package com.geotowny.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyDataImporterTest {

    @TempDir
    Path tempDir;

    private Path legacyRoot() throws IOException {
        Path legacy = tempDir.resolve("legacy");
        Files.createDirectories(legacy.resolve("data/towns"));
        Files.createDirectories(legacy.resolve("data/nations"));
        Files.writeString(legacy.resolve("data/towns/t1.json"), "{\"uuid\":\"t1\",\"name\":\"旧城\"}");
        Files.writeString(legacy.resolve("data/nations/n1.json"), "{\"uuid\":\"n1\",\"name\":\"旧国\"}");
        Files.writeString(legacy.resolve("data/townblocks.json"), "{\"w#0#0\":{\"worldKey\":\"w\",\"chunkX\":0,\"chunkZ\":0,\"townUuid\":\"t1\"}}");
        Files.writeString(legacy.resolve("alliances.json"), "{}");
        return legacy;
    }

    @Test
    void dryRunReportsWithoutCopying() throws IOException {
        Path legacy = legacyRoot();
        StorageLayout target = new StorageLayout(tempDir.resolve("target"));

        LegacyDataImporter.ImportReport report = new LegacyDataImporter(target).importFrom(legacy, true);

        assertThat(report.dryRun()).isTrue();
        assertThat(report.towns()).isEqualTo(1);
        assertThat(report.nations()).isEqualTo(1);
        assertThat(report.townBlocks()).isTrue();
        assertThat(report.alliances()).isTrue();
        assertThat(report.residentsDb()).isFalse();
        assertThat(Files.exists(target.townsDir())).isFalse();
    }

    @Test
    void importsIntoEmptyTarget() throws IOException {
        Path legacy = legacyRoot();
        StorageLayout target = new StorageLayout(tempDir.resolve("target"));

        new LegacyDataImporter(target).importFrom(legacy, false);

        assertThat(Files.exists(target.townsDir().resolve("t1.json"))).isTrue();
        assertThat(Files.exists(target.nationsDir().resolve("n1.json"))).isTrue();
        assertThat(Files.exists(target.townBlocksFile())).isTrue();
        assertThat(Files.exists(target.alliancesFile())).isTrue();
    }

    @Test
    void refusesToOverwriteNonEmptyTarget() throws IOException {
        Path legacy = legacyRoot();
        StorageLayout target = new StorageLayout(tempDir.resolve("target"));
        LegacyDataImporter importer = new LegacyDataImporter(target);
        importer.importFrom(legacy, false);

        assertThatThrownBy(() -> importer.importFrom(legacy, false))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("目标数据目录非空");
    }
}