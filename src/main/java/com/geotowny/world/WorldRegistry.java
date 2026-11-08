package com.geotowny.world;

import com.geotowny.Log;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内部世界键与 Bukkit 世界名之间的映射。
 * <p>旧数据使用 <code>minecraft:overworld</code> 这类注册表键；Bukkit 使用世界名。
 * 业务层统一使用内部键，平台层通过本类解析为 {@link World}。
 */
public final class WorldRegistry {

    private static final String MINECRAFT_PREFIX = "minecraft:";

    private final Map<String, String> internalToBukkit;
    private final Set<String> warnedUnknown = ConcurrentHashMap.newKeySet();

    public WorldRegistry(FileConfiguration config) {
        Map<String, String> mapping = new LinkedHashMap<>();
        var section = config.getConfigurationSection("world-mapping");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String value = section.getString(key);
                if (value != null && !value.isBlank()) {
                    mapping.put(key, value.trim());
                }
            }
        }
        if (mapping.isEmpty()) {
            mapping.put("minecraft:overworld", "world");
            mapping.put("minecraft:the_nether", "world_nether");
            mapping.put("minecraft:the_end", "world_the_end");
        }
        this.internalToBukkit = Collections.unmodifiableMap(mapping);
    }

    public Map<String, String> mapping() {
        return internalToBukkit;
    }

    /**
     * Bukkit 世界 → 内部键。未配置的世界会回退为 {@code minecraft:<name>} 并只告警一次。
     */
    public String toInternalKey(World world) {
        if (world == null) {
            return null;
        }
        String name = world.getName();
        for (Map.Entry<String, String> entry : internalToBukkit.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        if (warnedUnknown.add(name)) {
            Log.warn("世界 {} 未在 world-mapping 中配置，回退为 minecraft:{}。", name, name);
        }
        return MINECRAFT_PREFIX + name;
    }

    /**
     * 内部键 → Bukkit 世界名。未命中映射时去掉 {@code minecraft:} 前缀作为世界名。
     */
    public String toBukkitName(String internalKey) {
        if (internalKey == null) {
            return null;
        }
        String mapped = internalToBukkit.get(internalKey);
        if (mapped != null) {
            return mapped;
        }
        return internalKey.startsWith(MINECRAFT_PREFIX)
                ? internalKey.substring(MINECRAFT_PREFIX.length())
                : internalKey;
    }

    /**
     * 内部键 → 已加载的 Bukkit 世界，不存在返回 null。
     */
    public World toWorld(String internalKey) {
        String name = toBukkitName(internalKey);
        return name == null ? null : Bukkit.getWorld(name);
    }
}