package com.geotowny.domain;

import com.geotowny.domain.model.NationRank;
import com.geotowny.domain.model.Resident;
import com.geotowny.domain.model.TownRank;
import com.geotowny.domain.money.Money;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ResidentTest {

    private Resident resident() {
        Resident resident = new Resident(UUID.randomUUID(), "Alice", Money.of(100.0));
        resident.setTownUuid("town-1");
        resident.setTownRank(TownRank.RESIDENT);
        return resident;
    }

    @Test
    void singleTownMembership() {
        Resident resident = resident();

        assertThat(resident.hasTown()).isTrue();
        assertThat(resident.isInTown("town-1")).isTrue();
        assertThat(resident.isInTown("town-2")).isFalse();

        resident.joinTown("town-2");
        assertThat(resident.getTownUuid()).isEqualTo("town-2");
        assertThat(resident.isInTown("town-1")).isFalse();
    }

    @Test
    void leavingTownClearsRank() {
        Resident resident = resident();
        resident.setTownRank(TownRank.MAYOR);
        resident.setNationRank(NationRank.KING);
        resident.leaveTown();

        assertThat(resident.hasTown()).isFalse();
        assertThat(resident.getTownRank()).isNull();
        assertThat(resident.getNationRank()).isNull();
    }

    @Test
    void depositAndWithdrawUseMoney() {
        Resident resident = resident();

        resident.deposit(Money.of(50.0));
        assertThat(resident.getBalance()).isEqualTo(Money.of(150.0));

        assertThat(resident.withdraw(Money.of(30.0))).isTrue();
        assertThat(resident.getBalance()).isEqualTo(Money.of(120.0));

        assertThat(resident.withdraw(Money.of(1000.0))).isFalse();
        assertThat(resident.getBalance()).isEqualTo(Money.of(120.0));
    }

    @Test
    void rejectsNegativeBalance() {
        Resident resident = resident();
        resident.setBalance(Money.ofCents(-1));

        assertThat(resident.getBalance()).isEqualTo(Money.of(100.0));
    }

    @Test
    void kingFlagFollowsNationRank() {
        Resident resident = resident();
        resident.setNationRank(NationRank.KING);

        assertThat(resident.isKing()).isTrue();
        assertThat(resident.isMayorOf("town-1")).isFalse();
    }
}