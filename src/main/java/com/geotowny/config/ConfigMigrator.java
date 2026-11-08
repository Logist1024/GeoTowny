package com.geotowny.config;

import com.geotowny.Log;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 配置版本迁移。
 * <p>当前迁移链：0 → 1。旧 Fabric 的 JSON 配置由 P2 的 <code>LegacyConfigImporter</code> 负责转换，
 * 本类只处理 Spigot config.yml 自身的版本演进。
 */
public final class ConfigMigrator {

    public static final int CURRENT_VERSION = 1;

    private ConfigMigrator() {
    }

    public static void migrate(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        int version = config.getInt("config-version", 0);

        if (version == 0) {
            backup(plugin, 0);
            config.set("config-version", CURRENT_VERSION);
            plugin.saveConfig();
            Log.info("配置已从 v0 迁移到 v{}。", CURRENT_VERSION);
            return;
        }

        if (version > CURRENT_VERSION) {
            Log.warn("配置版本 {} 高于插件支持的 {}，部分配置可能被忽略。", version, CURRENT_VERSION);
        }
    }

    private static void backup(JavaPlugin plugin, int version) {
        Path source = plugin.getDataFolder().toPath().resolve("config.yml");
        if (!Files.exists(source)) {
            return;
        }
        Path target = source.resolveSibling("config.yml.v" + version + ".bak");
        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            Log.info("已备份旧配置到 {}。", target.getFileName());
        } catch (IOException e) {
            Log.error("备份配置文件失败: {}", e.getMessage());
        }
    }
}