package com.geotowny.service;

import com.geotowny.domain.model.TownLog;
import com.geotowny.storage.TownLogStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 城镇日志服务：写入、分页查询、清理。
 * <p>P3 只提供查询与存储；事件监听写入在 P4 接入。
 */
public final class TownLogService {

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_LOGS_PER_TOWN = 2000;

    private final TownLogStore store;

    public TownLogService(TownLogStore store) {
        this.store = store;
    }

    public void add(TownLog log) {
        if (log == null || log.getTownUuid() == null) {
            return;
        }
        List<TownLog> logs = store.load(log.getTownUuid());
        logs.add(log);
        logs.sort(Comparator.comparingLong(TownLog::getTimestamp));
        if (logs.size() > MAX_LOGS_PER_TOWN) {
            logs = new ArrayList<>(logs.subList(logs.size() - MAX_LOGS_PER_TOWN, logs.size()));
        }
        store.save(log.getTownUuid(), logs);
    }

    public List<TownLog> query(String townUuid, int page, String playerFilter,
                               String actionFilter, long since, int pageSize) {
        List<TownLog> filtered = filter(townUuid, playerFilter, actionFilter, since);
        int size = pageSize <= 0 ? DEFAULT_PAGE_SIZE : pageSize;
        int from = Math.max(0, (Math.max(1, page) - 1) * size);
        if (from >= filtered.size()) {
            return List.of();
        }
        int to = Math.min(filtered.size(), from + size);
        return List.copyOf(filtered.subList(from, to));
    }

    public int count(String townUuid, String playerFilter, String actionFilter, long since) {
        return filter(townUuid, playerFilter, actionFilter, since).size();
    }

    public void clear(String townUuid) {
        store.delete(townUuid);
    }

    public int cleanup(int retentionDays) {
        if (retentionDays <= 0) {
            return 0;
        }
        long threshold = System.currentTimeMillis() - retentionDays * 86_400_000L;
        int removed = 0;
        for (String townUuid : store.listTownUuids()) {
            List<TownLog> logs = store.load(townUuid);
            int before = logs.size();
            logs.removeIf(log -> log.getTimestamp() < threshold);
            removed += before - logs.size();
            if (logs.isEmpty()) {
                store.delete(townUuid);
            } else if (logs.size() != before) {
                store.save(townUuid, logs);
            }
        }
        return removed;
    }

    private List<TownLog> filter(String townUuid, String playerFilter, String actionFilter, long since) {
        String player = playerFilter == null ? null : playerFilter.toLowerCase(Locale.ROOT);
        String action = actionFilter == null ? null : actionFilter.toUpperCase(Locale.ROOT);
        return store.load(townUuid).stream()
                .filter(log -> since <= 0 || log.getTimestamp() >= since)
                .filter(log -> player == null || (log.getPlayerName() != null
                        && log.getPlayerName().toLowerCase(Locale.ROOT).contains(player)))
                .filter(log -> action == null || (log.getAction() != null
                        && log.getAction().name().equals(action)))
                .sorted(Comparator.comparingLong(TownLog::getTimestamp).reversed())
                .toList();
    }
}