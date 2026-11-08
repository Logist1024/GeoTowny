package com.geotowny.domain.model;

/**
 * 国家间外交关系。
 * <p>VASSAL / OVERLORD 保留关系语义，但决策 D-05 不实现附庸义务。
 */
public enum RelationType {
    ALLY,
    FRIENDLY,
    NEUTRAL,
    HOSTILE,
    AT_WAR,
    VASSAL,
    OVERLORD,
    TRADE_PACT,
    NON_AGGRESSION
}