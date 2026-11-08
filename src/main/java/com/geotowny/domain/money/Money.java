package com.geotowny.domain.money;

import java.math.BigDecimal;

/**
 * 不可变金额，内部以「分」为单位存储，彻底消除 double 精度漂移。
 * <p>迁移约定：旧 double 金额通过 {@link #of(double)} 一次性四舍五入到分。
 */
public final class Money implements Comparable<Money> {

    public static final Money ZERO = new Money(0L);

    private final long cents;

    private Money(long cents) {
        this.cents = cents;
    }

    public static Money ofCents(long cents) {
        return cents == 0L ? ZERO : new Money(cents);
    }

    public static Money of(double units) {
        if (!Double.isFinite(units)) {
            throw new IllegalArgumentException("金额必须是有限数值: " + units);
        }
        return ofCents(Math.round(units * 100.0d));
    }

    public static Money parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("金额不能为空");
        }
        return ofCents(new BigDecimal(text.trim()).movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact());
    }

    public long cents() {
        return cents;
    }

    public double doubleValue() {
        return cents / 100.0d;
    }

    public Money plus(Money other) {
        return ofCents(Math.addExact(cents, other.cents));
    }

    public Money minus(Money other) {
        return ofCents(Math.subtractExact(cents, other.cents));
    }

    public Money times(double factor) {
        if (!Double.isFinite(factor)) {
            throw new IllegalArgumentException("倍率必须是有限数值: " + factor);
        }
        return ofCents(Math.round(cents * factor));
    }

    public boolean isZero() {
        return cents == 0L;
    }

    public boolean isNegative() {
        return cents < 0L;
    }

    public boolean isPositive() {
        return cents > 0L;
    }

    public boolean atLeast(Money other) {
        return cents >= other.cents;
    }

    public boolean greaterThan(Money other) {
        return cents > other.cents;
    }

    public String format() {
        return BigDecimal.valueOf(cents, 2).toPlainString();
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(cents, other.cents);
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Money money && cents == money.cents);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(cents);
    }

    @Override
    public String toString() {
        return format();
    }
}