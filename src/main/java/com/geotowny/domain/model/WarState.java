package com.geotowny.domain.model;

/**
 * 战争状态。具体战役进度由 P6 的 {@code Campaign} 管理。
 */
public enum WarState {
    NONE,
    ACTIVE,
    SIEGE,
    CEASEFIRE,
    ENDED
}