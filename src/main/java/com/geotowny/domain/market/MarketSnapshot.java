package com.geotowny.domain.market;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MarketSnapshot {

    private List<MarketItem> items = new ArrayList<>();
    private ServerQuota server = new ServerQuota();
    private Map<String, PlayerQuota> players = new HashMap<>();
    private long lastResetDay;

    public List<MarketItem> getItems() {
        if (items == null) {
            items = new ArrayList<>();
        }
        return items;
    }

    public void setItems(List<MarketItem> items) {
        this.items = items == null ? new ArrayList<>() : items;
    }

    public ServerQuota getServer() {
        if (server == null) {
            server = new ServerQuota();
        }
        return server;
    }

    public void setServer(ServerQuota server) {
        this.server = server == null ? new ServerQuota() : server;
    }

    public Map<String, PlayerQuota> getPlayers() {
        if (players == null) {
            players = new HashMap<>();
        }
        return players;
    }

    public void setPlayers(Map<String, PlayerQuota> players) {
        this.players = players == null ? new HashMap<>() : players;
    }

    public long getLastResetDay() {
        return lastResetDay;
    }

    public void setLastResetDay(long lastResetDay) {
        this.lastResetDay = lastResetDay;
    }

    public static final class ServerQuota {

        private long dailyBought;
        private long dailySold;

        public long getDailyBought() {
            return dailyBought;
        }

        public void setDailyBought(long dailyBought) {
            this.dailyBought = Math.max(0, dailyBought);
        }

        public long getDailySold() {
            return dailySold;
        }

        public void setDailySold(long dailySold) {
            this.dailySold = Math.max(0, dailySold);
        }
    }

    public static final class PlayerQuota {

        private long dailyBought;
        private long dailySold;

        public long getDailyBought() {
            return dailyBought;
        }

        public void setDailyBought(long dailyBought) {
            this.dailyBought = Math.max(0, dailyBought);
        }

        public long getDailySold() {
            return dailySold;
        }

        public void setDailySold(long dailySold) {
            this.dailySold = Math.max(0, dailySold);
        }
    }
}