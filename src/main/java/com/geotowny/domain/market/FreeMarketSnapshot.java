package com.geotowny.domain.market;

import java.util.ArrayList;
import java.util.List;

public final class FreeMarketSnapshot {

    private long nextId = 1L;
    private List<Listing> listings = new ArrayList<>();

    public long getNextId() {
        return nextId < 1L ? 1L : nextId;
    }

    public void setNextId(long nextId) {
        this.nextId = Math.max(1L, nextId);
    }

    public List<Listing> getListings() {
        if (listings == null) {
            listings = new ArrayList<>();
        }
        return listings;
    }

    public void setListings(List<Listing> listings) {
        this.listings = listings == null ? new ArrayList<>() : listings;
    }
}