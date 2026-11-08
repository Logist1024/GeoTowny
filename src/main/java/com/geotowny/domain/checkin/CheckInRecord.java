package com.geotowny.domain.checkin;

public final class CheckInRecord {

    private String uuid;
    private String lastCheckInDate = "";
    private int currentStreak;
    private int totalCheckIns;

    public CheckInRecord() {
    }

    public CheckInRecord(String uuid) {
        this.uuid = uuid;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getLastCheckInDate() {
        return lastCheckInDate == null ? "" : lastCheckInDate;
    }

    public void setLastCheckInDate(String lastCheckInDate) {
        this.lastCheckInDate = lastCheckInDate == null ? "" : lastCheckInDate;
    }

    public int getCurrentStreak() {
        return currentStreak;
    }

    public void setCurrentStreak(int currentStreak) {
        this.currentStreak = Math.max(0, currentStreak);
    }

    public int getTotalCheckIns() {
        return totalCheckIns;
    }

    public void setTotalCheckIns(int totalCheckIns) {
        this.totalCheckIns = Math.max(0, totalCheckIns);
    }
}