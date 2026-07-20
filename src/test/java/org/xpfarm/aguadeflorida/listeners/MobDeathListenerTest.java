package org.xpfarm.aguadeflorida.listeners;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the drop-rate arithmetic in MobDeathListener.
 *
 * The event handler itself needs a live server (EntityDeathEvent, ItemStack and
 * Player cannot be built without one), so only the pure math is covered here.
 * The PlayerDeathEvent guard and ignoreCancelled are verified at runtime.
 */
class MobDeathListenerTest {

    private static final double DELTA = 1e-9;

    @Test
    @DisplayName("Without looting the base rate is used unchanged")
    void noLootingKeepsBaseRate() {
        assertEquals(0.05, MobDeathListener.computeDropRate(0.05, 0, 0.5), DELTA);
    }

    @Test
    @DisplayName("Looting raises the rate by the multiplier per level")
    void lootingScalesWithLevel() {
        assertEquals(0.075, MobDeathListener.computeDropRate(0.05, 1, 0.5), DELTA);
        assertEquals(0.100, MobDeathListener.computeDropRate(0.05, 2, 0.5), DELTA);
        assertEquals(0.125, MobDeathListener.computeDropRate(0.05, 3, 0.5), DELTA);
    }

    @Test
    @DisplayName("A base rate of zero never drops, whatever the looting level")
    void zeroBaseRateNeverDrops() {
        assertEquals(0.0, MobDeathListener.computeDropRate(0.0, 0, 0.5), DELTA);
        assertEquals(0.0, MobDeathListener.computeDropRate(0.0, 5, 0.5), DELTA);
    }

    @Test
    @DisplayName("A base rate of one always drops")
    void fullBaseRateAlwaysDrops() {
        assertEquals(1.0, MobDeathListener.computeDropRate(1.0, 0, 0.5), DELTA);
        assertEquals(1.0, MobDeathListener.computeDropRate(1.0, 3, 0.5), DELTA);
    }

    @Test
    @DisplayName("A base rate above one is capped at one")
    void baseRateAboveOneIsCapped() {
        assertEquals(1.0, MobDeathListener.computeDropRate(2.5, 0, 0.5), DELTA);
    }

    @Test
    @DisplayName("Looting that would push the rate past one is capped at one")
    void lootingOverflowIsCapped() {
        // 0.8 * (1 + 3 * 0.5) = 2.0 before clamping
        assertEquals(1.0, MobDeathListener.computeDropRate(0.8, 3, 0.5), DELTA);
    }

    @Test
    @DisplayName("A negative base rate is clamped to zero rather than going negative")
    void negativeBaseRateIsClampedToZero() {
        assertEquals(0.0, MobDeathListener.computeDropRate(-0.5, 0, 0.5), DELTA);
        assertEquals(0.0, MobDeathListener.computeDropRate(-0.5, 3, 0.5), DELTA);
    }

    @Test
    @DisplayName("A negative looting multiplier cannot drive the rate below zero")
    void negativeMultiplierCannotGoNegative() {
        // 0.05 * (1 + 3 * -2.0) = -0.25 if the multiplier were used as written;
        // the multiplier is ignored instead, leaving the base rate
        assertEquals(0.05, MobDeathListener.computeDropRate(0.05, 3, -2.0), DELTA);
    }

    @Test
    @DisplayName("A negative multiplier is ignored rather than reducing the rate")
    void negativeMultiplierIsIgnored() {
        // Treated as a multiplier of 0, leaving the base rate intact
        assertEquals(0.5, MobDeathListener.computeDropRate(0.5, 1, -0.5), DELTA);
    }

    @Test
    @DisplayName("Two negatives do not multiply into a guaranteed drop")
    void negativeBaseAndNegativeMultiplierStayAtZero() {
        // -0.5 * (1 + 3 * -1.0) = 1.0 if only the product were clamped
        assertEquals(0.0, MobDeathListener.computeDropRate(-0.5, 3, -1.0), DELTA);
    }

    @Test
    @DisplayName("A negative looting level is treated as no looting")
    void negativeLootingLevelIsTreatedAsZero() {
        assertEquals(0.05, MobDeathListener.computeDropRate(0.05, -3, 0.5), DELTA);
    }

    @Test
    @DisplayName("A rate that is not a real number falls back to never dropping")
    void nonRealRateFallsBackToZero() {
        assertEquals(0.0, MobDeathListener.computeDropRate(Double.NaN, 1, 0.5), DELTA);
        assertEquals(0.0, MobDeathListener.computeDropRate(0.05, 1, Double.NaN), DELTA);
        assertEquals(0.0, MobDeathListener.computeDropRate(Double.NEGATIVE_INFINITY, 1, 0.5), DELTA);
        assertEquals(1.0, MobDeathListener.computeDropRate(Double.POSITIVE_INFINITY, 1, 0.5), DELTA);
    }
}
