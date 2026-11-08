package com.geotowny.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 国家联盟（超国家组织）。
 */
public class Alliance {

    private String uuid;
    private String name;
    private String leaderNationUuid;
    private List<String> memberNationUuids = new ArrayList<>();
    private long founded;
    private boolean defensivePact;
    private boolean tradePact;
    private String board;

    public Alliance() {
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLeaderNationUuid() {
        return leaderNationUuid;
    }

    public void setLeaderNationUuid(String leaderNationUuid) {
        this.leaderNationUuid = leaderNationUuid;
    }

    public List<String> getMemberNationUuids() {
        if (memberNationUuids == null) {
            memberNationUuids = new ArrayList<>();
        }
        return memberNationUuids;
    }

    public void setMemberNationUuids(List<String> memberNationUuids) {
        this.memberNationUuids = memberNationUuids;
    }

    public long getFounded() {
        return founded;
    }

    public void setFounded(long founded) {
        this.founded = founded;
    }

    public boolean isDefensivePact() {
        return defensivePact;
    }

    public void setDefensivePact(boolean defensivePact) {
        this.defensivePact = defensivePact;
    }

    public boolean isTradePact() {
        return tradePact;
    }

    public void setTradePact(boolean tradePact) {
        this.tradePact = tradePact;
    }

    public String getBoard() {
        return board;
    }

    public void setBoard(String board) {
        this.board = board;
    }

    public boolean hasMember(String nationUuid) {
        return memberNationUuids != null && memberNationUuids.contains(nationUuid);
    }

    public int getMemberCount() {
        return memberNationUuids == null ? 0 : memberNationUuids.size();
    }
}