package com.geotowny.time;

import java.time.LocalDate;

/**
 * 现实日时钟：每日结算按服务器本地日期切换。
 */
public final class RealDayClock implements GameClock {

    @Override
    public long today() {
        return LocalDate.now().toEpochDay();
    }

    @Override
    public long nowMillis() {
        return System.currentTimeMillis();
    }
}