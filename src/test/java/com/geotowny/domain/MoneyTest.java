package com.geotowny.domain;

import com.geotowny.domain.money.Money;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void convertsDoubleToCentsWithHalfUp() {
        assertThat(Money.of(100.0).cents()).isEqualTo(10_000L);
        assertThat(Money.of(123.455).cents()).isEqualTo(12_346L);
        assertThat(Money.of(0.005).cents()).isEqualTo(1L);
    }

    @Test
    void parsesDecimalStrings() {
        assertThat(Money.parse("12.30").cents()).isEqualTo(1_230L);
        assertThat(Money.parse("0.01").cents()).isEqualTo(1L);
    }

    @Test
    void formatsWithoutFloatingPointNoise() {
        assertThat(Money.ofCents(12_345).format()).isEqualTo("123.45");
        assertThat(Money.ofCents(-5).format()).isEqualTo("-0.05");
    }

    @Test
    void arithmeticKeepsCentPrecision() {
        Money hundred = Money.of(100.0);
        Money thirty = Money.of(30.0);

        assertThat(hundred.plus(thirty)).isEqualTo(Money.of(130.0));
        assertThat(hundred.minus(thirty)).isEqualTo(Money.of(70.0));
        assertThat(hundred.times(0.85)).isEqualTo(Money.of(85.0));
    }

    @Test
    void comparesAndDetectsSigns() {
        Money ten = Money.of(10.0);
        Money twenty = Money.of(20.0);

        assertThat(ten.greaterThan(twenty)).isFalse();
        assertThat(twenty.atLeast(ten)).isTrue();
        assertThat(Money.ZERO.isZero()).isTrue();
        assertThat(Money.ofCents(-1).isNegative()).isTrue();
    }

    @Test
    void rejectsNonFiniteInput() {
        assertThatThrownBy(() -> Money.of(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of(Double.POSITIVE_INFINITY)).isInstanceOf(IllegalArgumentException.class);
    }
}