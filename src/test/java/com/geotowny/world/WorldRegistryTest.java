package com.geotowny.world;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorldRegistryTest {

    @Test
    void resolvesConfiguredWorldNames() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("world-mapping.minecraft:overworld", "world");
        config.set("world-mapping.minecraft:the_nether", "world_nether");

        WorldRegistry registry = new WorldRegistry(config);

        assertThat(registry.toBukkitName("minecraft:overworld")).isEqualTo("world");
        assertThat(registry.toBukkitName("minecraft:the_nether")).isEqualTo("world_nether");
    }

    @Test
    void stripsNamespaceWhenMappingIsMissing() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("world-mapping.minecraft:overworld", "world");

        WorldRegistry registry = new WorldRegistry(config);

        assertThat(registry.toBukkitName("minecraft:custom_world")).isEqualTo("custom_world");
        assertThat(registry.toBukkitName("custom")).isEqualTo("custom");
    }

    @Test
    void fallsBackToDefaultMappingWhenSectionIsEmpty() {
        WorldRegistry registry = new WorldRegistry(new YamlConfiguration());

        assertThat(registry.mapping())
                .containsEntry("minecraft:overworld", "world")
                .containsEntry("minecraft:the_nether", "world_nether")
                .containsEntry("minecraft:the_end", "world_the_end");
    }
}