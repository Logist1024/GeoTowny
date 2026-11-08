package com.geotowny.domain.model;

/**
 * 世界坐标点，用于城镇/国家出生点。
 */
public class WorldLocation {

    private String worldKey;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;

    public WorldLocation() {
    }

    public WorldLocation(String worldKey, double x, double y, double z, float yaw, float pitch) {
        this.worldKey = worldKey;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public String getWorldKey() {
        return worldKey;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }
}