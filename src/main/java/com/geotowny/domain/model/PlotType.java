package com.geotowny.domain.model;

/**
 * 地块类型。
 * <p>JAIL / WILDS / CAPITAL_CORE 仅为旧数据兼容保留，P2 迁移时统一映射为 DEFAULT；
 * 决策 D-05 不再实现其专属逻辑。
 */
public enum PlotType {
    DEFAULT,
    SHOP,
    FARM,
    EMBASSY,
    FORT,
    BANK,
    ARENA,
    JAIL,
    INN,
    WILDS,
    CAPITAL_CORE
}