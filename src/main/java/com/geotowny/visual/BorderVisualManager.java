package com.geotowny.visual;

import com.geotowny.GeoTownyPlugin;
import com.geotowny.core.GeoTownyCore;
import com.geotowny.domain.model.Town;
import com.geotowny.domain.model.TownBlock;
import com.geotowny.world.WorldRegistry;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * 城镇边界粒子渲染。
 * <p>P4 简化版：每 5 秒渲染一次外边界，固定 Y=64+borderHeight；每城镇最多渲染 256 个区块。
 * 后续可加入玩家距离筛选与动态高度。
 */
public final class BorderVisualManager {

    private static final int RENDER_INTERVAL_TICKS = 5;
    private static final int MAX_CLAIMS_PER_TOWN = 256;
    private static final double SEGMENT_STEP = 4.0;

    private final GeoTownyPlugin plugin;
    private final GeoTownyCore core;
    private final WorldRegistry worlds;

    private BukkitTask task;
    private int tickCounter;

    public BorderVisualManager(GeoTownyPlugin plugin, GeoTownyCore core, WorldRegistry worlds) {
        this.plugin = plugin;
        this.core = core;
        this.worlds = worlds;
    }

    public void start() {
        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        tickCounter++;
        if (tickCounter % RENDER_INTERVAL_TICKS != 0) {
            return;
        }
        for (Town town : core.allTowns()) {
            if (!town.isBorderEnabled() || town.getClaimCount() == 0) {
                continue;
            }
            renderTown(town);
        }
    }

    private void renderTown(Town town) {
        int rendered = 0;
        for (String key : town.getClaimBlockKeys()) {
            if (rendered >= MAX_CLAIMS_PER_TOWN) {
                break;
            }
            TownBlock block = core.getTownBlock(key);
            if (block == null) {
                continue;
            }
            World world = worlds.toWorld(block.getWorldKey());
            if (world == null) {
                continue;
            }
            drawEdges(town, world, block);
            rendered++;
        }
    }

    private void drawEdges(Town town, World world, TownBlock block) {
        int baseX = block.getChunkX() << 4;
        int baseZ = block.getChunkZ() << 4;
        double y = 64 + town.getBorderHeight();
        Particle.DustOptions dust = new Particle.DustOptions(
                Color.fromRGB(town.getEffectiveBorderColor() & 0xFFFFFF), 1.0f);

        if (!owns(town, block.getWorldKey(), block.getChunkX(), block.getChunkZ() - 1)) {
            lineX(world, dust, baseX, baseZ, y);
        }
        if (!owns(town, block.getWorldKey(), block.getChunkX(), block.getChunkZ() + 1)) {
            lineX(world, dust, baseX, baseZ + 16, y);
        }
        if (!owns(town, block.getWorldKey(), block.getChunkX() - 1, block.getChunkZ())) {
            lineZ(world, dust, baseX, baseZ, y);
        }
        if (!owns(town, block.getWorldKey(), block.getChunkX() + 1, block.getChunkZ())) {
            lineZ(world, dust, baseX + 16, baseZ, y);
        }
    }

    private boolean owns(Town town, String worldKey, int chunkX, int chunkZ) {
        TownBlock neighbor = core.getTownBlockAt(worldKey, chunkX, chunkZ);
        return neighbor != null && town.getUuid().equals(neighbor.getTownUuid());
    }

    private void lineX(World world, Particle.DustOptions dust, double x, double z, double y) {
        for (double offset = 0; offset <= 16; offset += SEGMENT_STEP) {
            world.spawnParticle(Particle.DUST, x + offset, y, z, 1, 0, 0, 0, 0, dust);
        }
    }

    private void lineZ(World world, Particle.DustOptions dust, double x, double z, double y) {
        for (double offset = 0; offset <= 16; offset += SEGMENT_STEP) {
            world.spawnParticle(Particle.DUST, x, y, z + offset, 1, 0, 0, 0, 0, dust);
        }
    }
}