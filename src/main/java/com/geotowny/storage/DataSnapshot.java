package com.geotowny.storage;

import com.geotowny.domain.model.Alliance;
import com.geotowny.domain.model.Nation;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.Town;
import com.geotowny.domain.model.TownBlock;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一次性加载的数据快照。P2 阶段为只读集合，P3 由各 Service 接管写入。
 */
public record DataSnapshot(
        Map<String, Town> towns,
        Map<String, Nation> nations,
        Map<String, Resident> residents,
        Map<String, TownBlock> townBlocks,
        Map<String, Alliance> alliances
) {

    public DataSnapshot {
        towns = immutable(towns);
        nations = immutable(nations);
        residents = immutable(residents);
        townBlocks = immutable(townBlocks);
        alliances = immutable(alliances);
    }

    public static DataSnapshot empty() {
        return new DataSnapshot(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    private static <T> Map<String, T> immutable(Map<String, T> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}