package com.geotowny.time;

/**
 * 时间口径抽象。决策 D-02：默认统一现实日。
 */
public interface GameClock {

    long today();

    long nowMillis();
}