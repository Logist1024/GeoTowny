package com.geotowny.domain;

import com.geotowny.domain.model.Ideology;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdeologyTest {

    @Test
    void keepsFourCoreGovernments() {
        assertThat(Ideology.DEMOCRACY.isWarRequiresVote()).isTrue();
        assertThat(Ideology.DEMOCRACY.isImpeachment()).isTrue();
        assertThat(Ideology.DEMOCRACY.isTermLimit()).isTrue();

        assertThat(Ideology.EMPIRE.isProvinceSystem()).isTrue();
        assertThat(Ideology.EMPIRE.getProvinceTaxBonus()).isEqualTo(0.5);

        assertThat(Ideology.FEDERATION.isFreeLeave()).isTrue();
        assertThat(Ideology.FEDERATION.isTownAutonomy()).isTrue();
        assertThat(Ideology.FEDERATION.getNationMaxTownsMod()).isEqualTo(2.0);

        assertThat(Ideology.THEOCRACY.isClergySystem()).isTrue();
        assertThat(Ideology.THEOCRACY.isHolyWar()).isTrue();
        assertThat(Ideology.THEOCRACY.isHeresyPenalty()).isTrue();
    }

    @Test
    void degradedGovernmentsKeepOnlyCostsAndTitles() {
        assertThat(Ideology.MONARCHY.getLeaderTitle()).isEqualTo("国王");
        assertThat(Ideology.MONARCHY.isWarRequiresVote()).isFalse();
        assertThat(Ideology.MONARCHY.isProvinceSystem()).isFalse();

        assertThat(Ideology.REPUBLIC.isWarRequiresVote()).isFalse();
        assertThat(Ideology.REPUBLIC.getEconomyMod()).isEqualTo(1.15);

        assertThat(Ideology.TRIBAL.getTownCreationCostMod()).isEqualTo(0.7);
        assertThat(Ideology.TRIBAL.getClaimCostMod()).isEqualTo(0.8);
    }

    @Test
    void everyGovernmentHasDisplayNameAndTitles() {
        for (Ideology ideology : Ideology.values()) {
            assertThat(ideology.getDisplayName()).isNotBlank();
            assertThat(ideology.getLeaderTitle()).isNotBlank();
            assertThat(ideology.getDeputyTitle()).isNotBlank();
        }
    }
}