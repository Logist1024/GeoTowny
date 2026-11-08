package com.geotowny.domain.war;

import com.geotowny.domain.model.WorldLocation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CampaignTest {

    private static Campaign campaign(Campaign.Type type) {
        return new Campaign(type, "attacker", "defender", "town", 10, 5,
                new WorldLocation("minecraft:overworld", 0, 64, 0, 0, 0));
    }

    @Test
    void siegePointsAccumulateUntilThreshold() {
        Campaign campaign = campaign(Campaign.Type.SIEGE);
        assertThat(campaign.addSiegePoints(40, 100)).isEqualTo(40);
        assertThat(campaign.addSiegePoints(60, 100)).isEqualTo(100);
        assertThat(campaign.getStatus()).isEqualTo(Campaign.Status.ACTIVE);
    }

    @Test
    void countdownTransitionsAreGuarded() {
        Campaign campaign = campaign(Campaign.Type.INVASION);
        long now = System.currentTimeMillis();
        assertThat(campaign.startCaptureCountdown(now)).isTrue();
        assertThat(campaign.getStatus()).isEqualTo(Campaign.Status.CAPTURE_COUNTDOWN);
        assertThat(campaign.startCaptureCountdown(now)).isFalse();
        assertThat(campaign.cancelCaptureCountdown()).isTrue();
        assertThat(campaign.getStatus()).isEqualTo(Campaign.Status.ACTIVE);
        assertThat(campaign.startFailCountdown(now, 30)).isTrue();
        assertThat(campaign.getStatus()).isEqualTo(Campaign.Status.FAIL_COUNTDOWN);
        assertThat(campaign.cancelFailCountdown()).isTrue();
        assertThat(campaign.getStatus()).isEqualTo(Campaign.Status.ACTIVE);
    }

    @Test
    void terminalStatusCannotBeOverwritten() {
        Campaign campaign = campaign(Campaign.Type.INVASION);
        assertThat(campaign.finish(Campaign.Status.SUCCESS)).isTrue();
        assertThat(campaign.isTerminal()).isTrue();
        assertThat(campaign.finish(Campaign.Status.FAILED)).isFalse();
        assertThat(campaign.getStatus()).isEqualTo(Campaign.Status.SUCCESS);
        assertThat(campaign.startCaptureCountdown(System.currentTimeMillis())).isFalse();
    }

    @Test
    void deathCountsIncreaseRespawnTime() {
        Campaign campaign = campaign(Campaign.Type.INVASION);
        assertThat(campaign.calcDefenderRespawnSeconds("p", 10, 0.10)).isEqualTo(10);
        campaign.incrementDefenderDeath("p");
        assertThat(campaign.calcDefenderRespawnSeconds("p", 10, 0.10)).isEqualTo(11);
        assertThat(campaign.calcAttackerRespawnSeconds("a", 0.10)).isEqualTo(5);
        campaign.incrementAttackerDeath("a");
        assertThat(campaign.calcAttackerRespawnSeconds("a", 0.10)).isEqualTo(6);
    }

    @Test
    void countdownProgressIsBounded() {
        Campaign campaign = campaign(Campaign.Type.INVASION);
        long now = System.currentTimeMillis();
        campaign.startCaptureCountdown(now);
        assertThat(campaign.captureProgress(now)).isBetween(0.0, 0.01);
        assertThat(campaign.captureProgress(now + 10_000)).isEqualTo(1.0);
        assertThat(campaign.remainingCaptureMillis(now + 20_000)).isZero();
    }
}