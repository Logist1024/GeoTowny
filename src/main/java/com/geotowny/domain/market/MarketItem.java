package com.geotowny.domain.market;

import com.geotowny.domain.money.Money;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MarketItem {

    private static final int MAX_HISTORY_SIZE = 100;

    private String itemId;
    private String displayName;
    private String category;
    private Money basePrice = Money.ZERO;
    private double priceAdjust = 1.0d;
    private long totalBought;
    private long totalSold;
    private long dailyBought;
    private long dailySold;
    private double dailyRandomMultiplier = 1.0d;
    private List<PriceSnapshot> priceHistory = new ArrayList<>();

    public MarketItem() {
    }

    public MarketItem(String itemId, String displayName, String category, Money basePrice) {
        this.itemId = itemId;
        this.displayName = displayName;
        this.category = category;
        this.basePrice = basePrice == null ? Money.ZERO : basePrice;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Money getBasePrice() {
        return basePrice == null ? Money.ZERO : basePrice;
    }

    public void setBasePrice(Money basePrice) {
        this.basePrice = basePrice == null ? Money.ZERO : basePrice;
    }

    public double getPriceAdjust() {
        return priceAdjust;
    }

    public void setPriceAdjust(double priceAdjust) {
        this.priceAdjust = Double.isFinite(priceAdjust) && priceAdjust > 0 ? priceAdjust : 1.0d;
    }

    public long getTotalBought() {
        return totalBought;
    }

    public void setTotalBought(long totalBought) {
        this.totalBought = Math.max(0, totalBought);
    }

    public long getTotalSold() {
        return totalSold;
    }

    public void setTotalSold(long totalSold) {
        this.totalSold = Math.max(0, totalSold);
    }

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

    public double getDailyRandomMultiplier() {
        return dailyRandomMultiplier;
    }

    public void setDailyRandomMultiplier(double dailyRandomMultiplier) {
        this.dailyRandomMultiplier = Double.isFinite(dailyRandomMultiplier) && dailyRandomMultiplier > 0
                ? dailyRandomMultiplier : 1.0d;
    }

    public long getNetBought() {
        return totalBought - totalSold;
    }

    public List<PriceSnapshot> getPriceHistory() {
        if (priceHistory == null) {
            priceHistory = new ArrayList<>();
        }
        return Collections.unmodifiableList(priceHistory);
    }

    public void setPriceHistory(List<PriceSnapshot> priceHistory) {
        this.priceHistory = priceHistory == null ? new ArrayList<>() : new ArrayList<>(priceHistory);
    }

    public void recordPriceSnapshot(Money buyPrice, Money sellPrice) {
        if (buyPrice == null || sellPrice == null || !buyPrice.isPositive() || !sellPrice.isPositive()) {
            return;
        }
        if (priceHistory == null) {
            priceHistory = new ArrayList<>();
        }
        priceHistory.add(new PriceSnapshot(System.currentTimeMillis(), buyPrice.cents(), sellPrice.cents()));
        if (priceHistory.size() > MAX_HISTORY_SIZE) {
            priceHistory.subList(0, priceHistory.size() - MAX_HISTORY_SIZE).clear();
        }
    }

    public void clearPriceHistory() {
        priceHistory.clear();
    }

    public int getPriceTrend() {
        if (priceHistory.size() < 2) {
            return 0;
        }
        PriceSnapshot latest = priceHistory.get(priceHistory.size() - 1);
        PriceSnapshot previous = priceHistory.get(priceHistory.size() - 2);
        long diff = latest.getBuyCents() - previous.getBuyCents();
        if (diff == 0) {
            return 0;
        }
        return diff > 0 ? 1 : -1;
    }

    public double getPriceChangePercent() {
        if (priceHistory.size() < 2) {
            return 0;
        }
        PriceSnapshot first = priceHistory.get(0);
        PriceSnapshot latest = priceHistory.get(priceHistory.size() - 1);
        if (first.getBuyCents() <= 0) {
            return 0;
        }
        return ((double) (latest.getBuyCents() - first.getBuyCents()) / first.getBuyCents()) * 100.0d;
    }

    public static final class PriceSnapshot {

        private long timestamp;
        private long buyCents;
        private long sellCents;

        public PriceSnapshot() {
        }

        public PriceSnapshot(long timestamp, long buyCents, long sellCents) {
            this.timestamp = timestamp;
            this.buyCents = buyCents;
            this.sellCents = sellCents;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public long getBuyCents() {
            return buyCents;
        }

        public long getSellCents() {
            return sellCents;
        }
    }
}