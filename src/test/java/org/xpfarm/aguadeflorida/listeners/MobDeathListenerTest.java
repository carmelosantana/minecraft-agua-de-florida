// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 carmelosantana
package org.xpfarm.aguadeflorida.listeners;

import org.bukkit.damage.DamageSource;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for MobDeathListener.
 *
 * Two things cannot be exercised without a live server, so both are kept out of
 * the logic under test on purpose:
 *
 *  - ItemStack is a concrete class whose methods delegate to a CraftBukkit
 *    handle, so nothing here calls a method on one. resolveLootingWeapon returns
 *    the ItemStack untouched and lootingLevelOf (a two-line null/empty guard plus
 *    getEnchantmentLevel) is the only place that reads it. Tests assert on
 *    reference identity instead.
 *  - The Bukkit entity types are interfaces, so the damage-source graph is built
 *    from java.lang.reflect proxies rather than a running server.
 *
 * The PlayerDeathEvent guard and ignoreCancelled are verified at runtime.
 */
class MobDeathListenerTest {

    private static final double DELTA = 1e-9;

    // ---------------------------------------------------------------- helpers

    /**
     * An ItemStack that is only ever compared by reference. Calling any method on
     * it throws, which is deliberate: it proves the resolver does not inspect the
     * item it hands back.
     */
    private static final class SentinelStack extends ItemStack {
        private final String label;

        SentinelStack(String label) {
            super();
            this.label = label;
        }

        @Override
        public String toString() {
            return "SentinelStack(" + label + ")";
        }
    }

    /** Builds an interface proxy that answers only the methods it is given. */
    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, Map<String, Object> answers) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (answers.containsKey(method.getName())) {
                return answers.get(method.getName());
            }
            return switch (method.getName()) {
                case "toString" -> type.getSimpleName() + "@fake";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(
                        type.getSimpleName() + "." + method.getName() + " is not stubbed");
            };
        };
        return (T) Proxy.newProxyInstance(
                MobDeathListenerTest.class.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static DamageSource damageFrom(Entity direct) {
        Map<String, Object> answers = new HashMap<>();
        answers.put("getDirectEntity", direct);
        return fake(DamageSource.class, answers);
    }

    private static <T> T armed(Class<T> type, ItemStack mainHand) {
        EntityEquipment equipment = fake(EntityEquipment.class,
                Map.of("getItemInMainHand", mainHand));
        Map<String, Object> answers = new HashMap<>();
        answers.put("getEquipment", equipment);
        return fake(type, answers);
    }

    private static <T extends AbstractArrow> T firedFrom(Class<T> type, ItemStack weapon) {
        Map<String, Object> answers = new HashMap<>();
        answers.put("getWeapon", weapon);
        return fake(type, answers);
    }

    // --------------------------------------------- damage-source attribution

    @Test
    @DisplayName("A melee kill reads the attacker's main hand")
    void meleeKillReadsMainHand() {
        SentinelStack sword = new SentinelStack("sword");
        Player killer = armed(Player.class, sword);

        assertSame(sword, MobDeathListener.resolveLootingWeapon(damageFrom(killer)));
    }

    @Test
    @DisplayName("A bow kill reads the bow that fired the arrow, not the current main hand")
    void bowKillReadsTheFiringWeapon() {
        SentinelStack bow = new SentinelStack("bow");
        Arrow arrow = firedFrom(Arrow.class, bow);

        // The arrow proxy has no getShooter or getEquipment stub, so if the resolver
        // tried to fall back to the shooter's held item this would throw.
        assertSame(bow, MobDeathListener.resolveLootingWeapon(damageFrom(arrow)));
    }

    @Test
    @DisplayName("An arrow with no recorded weapon yields no looting source")
    void arrowWithoutWeaponYieldsNothing() {
        Arrow dispensed = firedFrom(Arrow.class, null);

        assertNull(MobDeathListener.resolveLootingWeapon(damageFrom(dispensed)));
    }

    @Test
    @DisplayName("A mob-fired arrow reads the mob's bow")
    void mobFiredArrowReadsTheMobsBow() {
        SentinelStack skeletonBow = new SentinelStack("skeleton-bow");
        Arrow arrow = firedFrom(Arrow.class, skeletonBow);

        assertSame(skeletonBow, MobDeathListener.resolveLootingWeapon(damageFrom(arrow)));
    }

    @Test
    @DisplayName("A mob melee kill reads the mob's held item")
    void mobMeleeKillReadsMobMainHand() {
        SentinelStack mobBlade = new SentinelStack("mob-blade");
        Skeleton skeleton = armed(Skeleton.class, mobBlade);

        assertSame(mobBlade, MobDeathListener.resolveLootingWeapon(damageFrom(skeleton)));
    }

    @Test
    @DisplayName("An attacker with no equipment at all yields no looting source")
    void attackerWithoutEquipmentYieldsNothing() {
        Map<String, Object> answers = new HashMap<>();
        answers.put("getEquipment", null);
        Skeleton bare = fake(Skeleton.class, answers);

        assertNull(MobDeathListener.resolveLootingWeapon(damageFrom(bare)));
    }

    @Test
    @DisplayName("Environmental damage has no direct entity and so no looting source")
    void environmentalDamageYieldsNothing() {
        // Fall damage, fire, drowning: getDirectEntity() is null
        assertNull(MobDeathListener.resolveLootingWeapon(damageFrom(null)));
    }

    @Test
    @DisplayName("A non-living direct damager yields no looting source")
    void nonLivingDamagerYieldsNothing() {
        // A primed TNT or similar is an Entity but not a LivingEntity
        Entity tnt = fake(Entity.class, new HashMap<>());

        assertNull(MobDeathListener.resolveLootingWeapon(damageFrom(tnt)));
    }

    @Test
    @DisplayName("A non-arrow projectile yields no looting source")
    void nonArrowProjectileYieldsNothing() {
        Snowball snowball = fake(Snowball.class, new HashMap<>());

        assertNull(MobDeathListener.resolveLootingWeapon(damageFrom(snowball)));
    }

    @Test
    @DisplayName("A creeper explosion yields no looting source")
    void creeperExplosionYieldsNothing() {
        // The creeper is living but carries nothing; its equipment is empty
        Map<String, Object> answers = new HashMap<>();
        answers.put("getEquipment", null);
        Creeper creeper = fake(Creeper.class, answers);

        assertNull(MobDeathListener.resolveLootingWeapon(damageFrom(creeper)));
    }

    @Test
    @DisplayName("A null damage source is tolerated rather than throwing")
    void nullDamageSourceYieldsNothing() {
        assertNull(MobDeathListener.resolveLootingWeapon(null));
    }

    // ------------------------------------------------------ looting level read

    @Test
    @DisplayName("No weapon means no looting")
    void nullWeaponHasNoLooting() {
        assertEquals(0, MobDeathListener.lootingLevelOf(null));
    }

    // ------------------------------------------------- player-kill gate

    @Test
    @DisplayName("A mob that dies without a player killer does not drop")
    void noPlayerKillerDoesNotDrop() {
        // Fall damage, fire, drowning, mob-on-mob: nothing is credited, so nothing
        // drops however generous the configured rate is
        assertEquals(0.0, MobDeathListener.dropChanceFor(false, 0.08, 0, 0.5), DELTA);
        assertEquals(0.0, MobDeathListener.dropChanceFor(false, 1.0, 3, 0.5), DELTA);
    }

    @Test
    @DisplayName("A player kill with an unattributable weapon still drops at the base rate")
    void playerKillWithoutWeaponStillDropsAtBaseRate() {
        // The whole point of keeping the two gates separate: no looting resolved
        // must not collapse into no drop
        assertEquals(0.08, MobDeathListener.dropChanceFor(true, 0.08, 0, 0.5), DELTA);
    }

    @Test
    @DisplayName("A player kill with an unenchanted weapon drops at the base rate")
    void playerKillWithUnenchantedWeaponDropsAtBaseRate() {
        // lootingLevelOf(sword with no Looting) is 0, same as no weapon at all
        assertEquals(0.25, MobDeathListener.dropChanceFor(true, 0.25, 0, 0.5), DELTA);
    }

    @Test
    @DisplayName("A player kill still scales with looting")
    void playerKillScalesWithLooting() {
        assertEquals(0.20, MobDeathListener.dropChanceFor(true, 0.08, 3, 0.5), DELTA);
    }

    @Test
    @DisplayName("The player gate runs end to end from an environmental damage source")
    void environmentalDeathResolvesToNoLootingAndNoDrop() {
        // resolveLootingWeapon returned null, lootingLevelOf turned that into 0,
        // and the absent player killer is what actually suppresses the drop
        int lootingLevel = MobDeathListener.lootingLevelOf(
                MobDeathListener.resolveLootingWeapon(damageFrom(null)));

        assertEquals(0, lootingLevel);
        assertEquals(0.0, MobDeathListener.dropChanceFor(false, 0.08, lootingLevel, 0.5), DELTA);
    }

    @Test
    @DisplayName("A player kill whose weapon cannot be attributed resolves to a base-rate drop")
    void unattributedPlayerKillResolvesToBaseRate() {
        // A dispenser-fired arrow reports no weapon; if a player were somehow
        // credited, the base rate still applies rather than zero
        int lootingLevel = MobDeathListener.lootingLevelOf(
                MobDeathListener.resolveLootingWeapon(damageFrom(firedFrom(Arrow.class, null))));

        assertEquals(0, lootingLevel);
        assertEquals(0.08, MobDeathListener.dropChanceFor(true, 0.08, lootingLevel, 0.5), DELTA);
    }

    // ----------------------------------------------------------- rate maths

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
    @DisplayName("The per-mob base rates scale independently")
    void perMobBaseRatesScaleIndependently() {
        // WITCH 8%, EVOKER 25%, DROWNED 1% at looting III with a 0.5 multiplier
        assertEquals(0.20, MobDeathListener.computeDropRate(0.08, 3, 0.5), DELTA);
        assertEquals(0.625, MobDeathListener.computeDropRate(0.25, 3, 0.5), DELTA);
        assertEquals(0.025, MobDeathListener.computeDropRate(0.01, 3, 0.5), DELTA);
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
