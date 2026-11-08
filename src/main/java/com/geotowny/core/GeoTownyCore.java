package com.geotowny.core;

import com.geotowny.config.PluginConfig;
import com.geotowny.domain.model.Alliance;
import com.geotowny.domain.model.Nation;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.Town;
import com.geotowny.domain.model.TownBlock;
import com.geotowny.domain.money.Money;
import com.geotowny.storage.DataSnapshot;
import com.geotowny.storage.StorageService;
import com.geotowny.util.PinyinUtil;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 中央协调器：内存索引、查询与持久化入口。
 * <p>P3 起由领域服务写入；命令层与监听器只通过服务访问。
 */
public final class GeoTownyCore {

    private final StorageService storage;
    private final PluginConfig config;

    private final Map<String, Town> towns = new ConcurrentHashMap<>();
    private final Map<String, String> townsByName = new ConcurrentHashMap<>();
    private final Map<String, String> townsByCode = new ConcurrentHashMap<>();

    private final Map<String, Nation> nations = new ConcurrentHashMap<>();
    private final Map<String, String> nationsByName = new ConcurrentHashMap<>();
    private final Map<String, String> nationsByCode = new ConcurrentHashMap<>();

    private final Map<String, Resident> residents = new ConcurrentHashMap<>();
    private final Map<String, String> residentsByName = new ConcurrentHashMap<>();

    private final Map<String, TownBlock> townBlocks = new ConcurrentHashMap<>();
    private final Map<String, Alliance> alliances = new ConcurrentHashMap<>();

    public GeoTownyCore(StorageService storage, PluginConfig config) {
        this.storage = storage;
        this.config = config;
    }

    public void load() {
        DataSnapshot snapshot = storage.snapshot();
        towns.clear();
        townsByName.clear();
        townsByCode.clear();
        nations.clear();
        nationsByName.clear();
        nationsByCode.clear();
        residents.clear();
        residentsByName.clear();
        townBlocks.clear();
        alliances.clear();

        for (Resident resident : snapshot.residents().values()) {
            residents.put(resident.getUuidString(), resident);
            if (resident.getName() != null) {
                residentsByName.put(resident.getName().toLowerCase(Locale.ROOT), resident.getUuidString());
            }
        }
        for (Town town : snapshot.towns().values()) {
            towns.put(town.getUuid(), town);
            if (town.getName() != null) {
                townsByName.put(town.getName().toLowerCase(Locale.ROOT), town.getUuid());
            }
            if (town.getCode() != null && !town.getCode().isBlank()) {
                townsByCode.put(town.getCode().toUpperCase(Locale.ROOT), town.getUuid());
            }
        }
        for (Nation nation : snapshot.nations().values()) {
            nations.put(nation.getUuid(), nation);
            if (nation.getName() != null) {
                nationsByName.put(nation.getName().toLowerCase(Locale.ROOT), nation.getUuid());
            }
            if (nation.getCode() != null && !nation.getCode().isBlank()) {
                nationsByCode.put(nation.getCode().toUpperCase(Locale.ROOT), nation.getUuid());
            }
        }
        townBlocks.putAll(snapshot.townBlocks());
        alliances.putAll(snapshot.alliances());
    }

    // ===== 居民 =====

    public Resident getResident(UUID uuid) {
        return uuid == null ? null : residents.get(uuid.toString());
    }

    public Resident getResident(String uuid) {
        return uuid == null ? null : residents.get(uuid);
    }

    public Resident getResidentByName(String name) {
        if (name == null) {
            return null;
        }
        String uuid = residentsByName.get(name.toLowerCase(Locale.ROOT));
        return uuid == null ? null : residents.get(uuid);
    }

    /**
     * 获取或创建居民。离线玩家从数据库恢复；新玩家按配置初始余额创建。
     */
    public Resident getOrCreateResident(UUID uuid, String name) {
        Resident resident = residents.get(uuid.toString());
        if (resident == null) {
            resident = storage.sqliteStore().findResident(uuid);
            if (resident == null) {
                resident = new Resident(uuid, name, Money.of(config.startingBalance()));
                residents.put(resident.getUuidString(), resident);
                indexResidentName(resident);
                storage.sqliteStore().saveResident(resident);
            } else {
                residents.put(resident.getUuidString(), resident);
            }
        }
        if (name != null && !name.equals(resident.getName())) {
            String oldName = resident.getName();
            resident.setName(name);
            if (oldName != null) {
                residentsByName.remove(oldName.toLowerCase(Locale.ROOT));
            }
            indexResidentName(resident);
            storage.sqliteStore().saveResident(resident);
        }
        return resident;
    }

    public Collection<Resident> allResidents() {
        return residents.values();
    }

    public void saveResident(Resident resident) {
        storage.sqliteStore().saveResident(resident);
    }

    public void saveResidents(Collection<Resident> residents) {
        if (residents == null || residents.isEmpty()) {
            return;
        }
        storage.sqliteStore().saveAll(residents);
    }

    private void indexResidentName(Resident resident) {
        if (resident.getName() != null) {
            residentsByName.put(resident.getName().toLowerCase(Locale.ROOT), resident.getUuidString());
        }
    }

    // ===== 城镇 =====

    public Town getTown(String uuid) {
        return uuid == null ? null : towns.get(uuid);
    }

    public Town getTownByName(String name) {
        if (name == null) {
            return null;
        }
        String uuid = townsByName.get(name.toLowerCase(Locale.ROOT));
        return uuid == null ? null : towns.get(uuid);
    }

    public Town getTownByCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String uuid = townsByCode.get(code.toUpperCase(Locale.ROOT));
        return uuid == null ? null : towns.get(uuid);
    }

    public Collection<Town> allTowns() {
        return towns.values();
    }

    public void registerTown(Town town) {
        towns.put(town.getUuid(), town);
        if (town.getName() != null) {
            townsByName.put(town.getName().toLowerCase(Locale.ROOT), town.getUuid());
        }
        if (town.getCode() != null && !town.getCode().isBlank()) {
            townsByCode.put(town.getCode().toUpperCase(Locale.ROOT), town.getUuid());
        }
        storage.jsonStore().saveTown(town);
    }

    public void unregisterTown(Town town) {
        towns.remove(town.getUuid());
        if (town.getName() != null) {
            townsByName.remove(town.getName().toLowerCase(Locale.ROOT));
        }
        if (town.getCode() != null && !town.getCode().isBlank()) {
            townsByCode.remove(town.getCode().toUpperCase(Locale.ROOT));
        }
        storage.jsonStore().deleteTown(town.getUuid());
    }

    public void updateTownNameIndex(String oldName, Town town) {
        if (oldName != null) {
            townsByName.remove(oldName.toLowerCase(Locale.ROOT));
        }
        if (town.getName() != null) {
            townsByName.put(town.getName().toLowerCase(Locale.ROOT), town.getUuid());
        }
    }

    public void updateTownCodeIndex(String oldCode, Town town) {
        if (oldCode != null) {
            townsByCode.remove(oldCode.toUpperCase(Locale.ROOT));
        }
        if (town.getCode() != null && !town.getCode().isBlank()) {
            townsByCode.put(town.getCode().toUpperCase(Locale.ROOT), town.getUuid());
        }
    }

    public void saveTown(Town town) {
        storage.jsonStore().saveTown(town);
    }

    /**
     * 生成唯一三字代码：中文取拼音首字母，英文取前三位，冲突推位。
     */
    public String generateUniqueTownCode(String townName) {
        String base = buildCodeBase(townName, 3);
        if (getTownByCode(base) == null) {
            return base;
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            String candidate = base.substring(0, 2) + c;
            if (getTownByCode(candidate) == null) {
                return candidate;
            }
        }
        return UUID.randomUUID().toString().substring(0, 3).toUpperCase(Locale.ROOT);
    }

    private static String buildCodeBase(String name, int length) {
        String initials = PinyinUtil.getFirstThreeInitials(name);
        StringBuilder letters = new StringBuilder();
        for (int i = 0; i < name.length() && letters.length() < length; i++) {
            char ch = Character.toUpperCase(name.charAt(i));
            if (Character.isLetterOrDigit(ch)) {
                letters.append(ch);
            }
        }
        if (!initials.isBlank()) {
            letters.insert(0, initials.toUpperCase(Locale.ROOT));
        }
        String base = letters.toString().replaceAll("[^A-Z0-9]", "");
        if (base.isBlank()) {
            base = "TOW";
        }
        while (base.length() < length) {
            base += "X";
        }
        return base.substring(0, length);
    }

    // ===== 国家 =====

    public Nation getNation(String uuid) {
        return uuid == null ? null : nations.get(uuid);
    }

    public Nation getNationByName(String name) {
        if (name == null) {
            return null;
        }
        String uuid = nationsByName.get(name.toLowerCase(Locale.ROOT));
        return uuid == null ? null : nations.get(uuid);
    }

    public Nation getNationByCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String uuid = nationsByCode.get(code.toUpperCase(Locale.ROOT));
        return uuid == null ? null : nations.get(uuid);
    }

    public Collection<Nation> allNations() {
        return nations.values();
    }

    public void registerNation(Nation nation) {
        nations.put(nation.getUuid(), nation);
        if (nation.getName() != null) {
            nationsByName.put(nation.getName().toLowerCase(Locale.ROOT), nation.getUuid());
        }
        if (nation.getCode() != null && !nation.getCode().isBlank()) {
            nationsByCode.put(nation.getCode().toUpperCase(Locale.ROOT), nation.getUuid());
        }
        storage.jsonStore().saveNation(nation);
    }

    public void unregisterNation(Nation nation) {
        nations.remove(nation.getUuid());
        if (nation.getName() != null) {
            nationsByName.remove(nation.getName().toLowerCase(Locale.ROOT));
        }
        if (nation.getCode() != null && !nation.getCode().isBlank()) {
            nationsByCode.remove(nation.getCode().toUpperCase(Locale.ROOT));
        }
        storage.jsonStore().deleteNation(nation.getUuid());
    }

    public void saveNation(Nation nation) {
        storage.jsonStore().saveNation(nation);
    }

    public void updateNationNameIndex(String oldName, Nation nation) {
        if (oldName != null) {
            nationsByName.remove(oldName.toLowerCase(Locale.ROOT));
        }
        if (nation.getName() != null) {
            nationsByName.put(nation.getName().toLowerCase(Locale.ROOT), nation.getUuid());
        }
    }

    public void updateNationCodeIndex(String oldCode, Nation nation) {
        if (oldCode != null) {
            nationsByCode.remove(oldCode.toUpperCase(Locale.ROOT));
        }
        if (nation.getCode() != null && !nation.getCode().isBlank()) {
            nationsByCode.put(nation.getCode().toUpperCase(Locale.ROOT), nation.getUuid());
        }
    }

    /**
     * 生成唯一四位国家代码：中文取拼音首字母，英文取前四位，冲突推位。
     */
    public String generateUniqueNationCode(String nationName) {
        String initials = PinyinUtil.getInitials(nationName);
        StringBuilder letters = new StringBuilder();
        for (int i = 0; i < initials.length() && letters.length() < 4; i++) {
            char ch = Character.toUpperCase(initials.charAt(i));
            if (Character.isLetter(ch)) {
                letters.append(ch);
            }
        }
        for (int i = 0; i < nationName.length() && letters.length() < 4; i++) {
            char ch = Character.toUpperCase(nationName.charAt(i));
            if (Character.isLetter(ch)) {
                letters.append(ch);
            }
        }
        String base = letters.toString();
        while (base.length() < 4) {
            base += "X";
        }
        base = base.substring(0, 4);
        if (getNationByCode(base) == null) {
            return base;
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            String candidate = base.substring(0, 3) + c;
            if (getNationByCode(candidate) == null) {
                return candidate;
            }
        }
        return UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
    }

    // ===== 领地 =====

    public TownBlock getTownBlock(String key) {
        return key == null ? null : townBlocks.get(key);
    }

    public TownBlock getTownBlockAt(String worldKey, int chunkX, int chunkZ) {
        return townBlocks.get(TownBlock.keyOf(worldKey, chunkX, chunkZ));
    }

    public void putTownBlock(TownBlock block) {
        townBlocks.put(block.key(), block);
    }

    public void removeTownBlock(String key) {
        townBlocks.remove(key);
    }

    public Collection<TownBlock> allTownBlocks() {
        return townBlocks.values();
    }

    public Map<String, TownBlock> townBlockMap() {
        return townBlocks;
    }

    public void saveTownBlocks() {
        storage.jsonStore().saveTownBlocks(townBlocks);
    }

    // ===== 联盟 =====

    public Collection<Alliance> allAlliances() {
        return alliances.values();
    }

    public Alliance getAlliance(String uuid) {
        return uuid == null ? null : alliances.get(uuid);
    }

    public void putAlliance(Alliance alliance) {
        alliances.put(alliance.getUuid(), alliance);
    }

    public void removeAlliance(String uuid) {
        alliances.remove(uuid);
    }

    public void saveAlliances() {
        storage.jsonStore().saveAlliances(alliances);
    }

    /**
     * 全量保存：城镇、国家、居民、领地、联盟。
     */
    public void saveAll() {
        towns.values().forEach(storage.jsonStore()::saveTown);
        nations.values().forEach(storage.jsonStore()::saveNation);
        storage.sqliteStore().saveAll(residents.values());
        storage.jsonStore().saveTownBlocks(townBlocks);
        storage.jsonStore().saveAlliances(alliances);
    }

    public PluginConfig config() {
        return config;
    }
}