// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 carmelosantana
package org.xpfarm.aguadeflorida.listeners;

import org.bukkit.damage.DamageSource;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.xpfarm.aguadeflorida.AguaDeFloridaPlugin;
import org.xpfarm.aguadeflorida.utils.AguaItemBuilder;
import org.xpfarm.aguadeflorida.utils.ConfigManager;

import java.util.Random;

/**
 * Handles mob death events and drops Agua de Florida items.
 */
public class MobDeathListener implements Listener {

    private final AguaDeFloridaPlugin plugin;
    private final ConfigManager configManager;
    private final AguaItemBuilder itemBuilder;
    private final Random random;

    public MobDeathListener(AguaDeFloridaPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.itemBuilder = plugin.getItemBuilder();
        this.random = new Random();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        // PlayerDeathEvent extends EntityDeathEvent, so this handler also fires for
        // player deaths. Agua de Florida is a mob drop only; never attach it to a
        // player's death drops, even if an admin puts PLAYER in mob_drops.mob_types.
        if (event instanceof PlayerDeathEvent) {
            return;
        }

        if (!configManager.isMobDropsEnabled()) {
            return;
        }

        LivingEntity entity = event.getEntity();

        // Rates are per mob type. An unlisted mob reads back as 0.0, so this doubles
        // as the "can this mob drop at all" check.
        double baseRate = configManager.getDropRate(entity.getType());
        if (!(baseRate > 0.0)) {
            return;
        }

        // Agua de Florida is a reward for a player kill. A mob that drowns, burns,
        // falls or is killed by another mob drops nothing, otherwise the item would
        // be farmable with no player involvement at all.
        Player killer = entity.getKiller();

        // The looting level comes from the weapon that actually dealt the killing
        // blow, not from whatever the killer happens to be holding now. See
        // resolveLootingWeapon for the attribution rules. A player kill whose weapon
        // cannot be attributed is still a drop, just at the unmodified base rate.
        int lootingLevel = lootingLevelOf(resolveLootingWeapon(event.getDamageSource()));

        double dropChance = dropChanceFor(
                killer != null, baseRate, lootingLevel, configManager.getLootingMultiplier());

        if (random.nextDouble() < dropChance) {
            ItemStack aguaItem = itemBuilder.getCachedItem();
            event.getDrops().add(aguaItem);

            if (configManager.isLogDrops()) {
                plugin.getLogger().info("Agua de Florida dropped by " + entity.getType().name()
                        + " killed by " + killer.getName()
                        + " (looting: " + lootingLevel
                        + ", chance: " + String.format("%.2f%%", dropChance * 100) + ")");
            }

            plugin.debugLog("Agua de Florida dropped for " + killer.getName()
                    + " from " + entity.getType().name());
        }
    }

    /**
     * Combines the two independent gates on a drop.
     *
     * These are deliberately kept separate. Whether a player got the kill decides
     * if there is a drop at all; whether a weapon could be attributed only decides
     * how much Looting scales it. A player kill with an unenchanted sword, or with
     * a projectile whose originating weapon is unknown, still drops at the base
     * rate. Collapsing "no looting attributable" into "no drop" would silently
     * disable the feature for every unenchanted kill.
     *
     * @param playerKilled Whether a player is credited with the kill
     * @param baseRate The configured base rate for the dying mob type
     * @param lootingLevel The looting level resolved from the killing blow, 0 if none
     * @param lootingMultiplier The configured per-level looting multiplier
     * @return The final drop chance, or 0.0 when no player is credited
     */
    static double dropChanceFor(boolean playerKilled, double baseRate, int lootingLevel, double lootingMultiplier) {
        if (!playerKilled) {
            return 0.0;
        }
        return computeDropRate(baseRate, lootingLevel, lootingMultiplier);
    }

    /**
     * Resolves the item whose Looting enchantment governs this kill.
     *
     * The old implementation read the killer's main hand at the moment
     * EntityDeathEvent fired. That is the wrong source for anything fired: by the
     * time an arrow lands the shooter has usually swapped, and for a dispenser or
     * a mob-fired arrow there is no held item to read at all. The damage source
     * carried by the death event names the entity that actually dealt the blow, so
     * the weapon is resolved from that instead.
     *
     * Attribution rules:
     * <ul>
     *   <li>Arrow-like projectile (arrow, spectral arrow, trident) - the weapon the
     *       projectile records as having fired it. AbstractArrow#getWeapon() is
     *       nullable, and is null for projectiles with no originating item, in which
     *       case there is nothing to read.</li>
     *   <li>Direct melee - the attacker's main hand, which for a melee blow is still
     *       the item that struck.</li>
     *   <li>Anything else (fall, fire, drowning, another mob's contact damage, TNT,
     *       non-arrow projectiles such as snowballs and eggs) - no weapon. Vanilla
     *       does not apply Looting to those paths either, so the base rate stands.</li>
     * </ul>
     *
     * @param source The damage source from the death event, may be null
     * @return The weapon item, or null when no weapon can be attributed
     */
    static ItemStack resolveLootingWeapon(DamageSource source) {
        if (source == null) {
            return null;
        }

        Entity direct = source.getDirectEntity();

        // Bow, crossbow and trident kills. getWeapon() is the item that launched the
        // projectile, which survives the shooter swapping hands mid-flight.
        if (direct instanceof AbstractArrow arrow) {
            return arrow.getWeapon();
        }

        // Melee. Read the main hand of whoever swung, player or mob.
        if (direct instanceof LivingEntity attacker && attacker.getEquipment() != null) {
            return attacker.getEquipment().getItemInMainHand();
        }

        return null;
    }

    /**
     * Reads the Looting level off a resolved weapon.
     *
     * @param weapon The weapon resolved from the damage source, may be null
     * @return The Looting level, or 0 when there is no weapon or no enchantment
     */
    static int lootingLevelOf(ItemStack weapon) {
        if (weapon == null || weapon.isEmpty()) {
            return 0;
        }
        return weapon.getEnchantmentLevel(Enchantment.LOOTING);
    }

    /**
     * Pure drop-rate arithmetic, split out so it can be tested without a live server.
     *
     * The result is clamped to [0.0, 1.0] at BOTH ends. Clamping only at the top used
     * to let a negative drop_rate or looting_multiplier produce a negative chance,
     * which silently disabled drops (random.nextDouble() is never below zero).
     *
     * The inputs are clamped too, not just the product. Clamping only the product
     * would let two negatives cancel into a large positive chance, so a negative
     * drop_rate paired with a negative looting_multiplier could drop on every kill.
     * ConfigManager already rejects those values at load time; this is the second line.
     *
     * @param baseRate The configured base drop rate for the dying mob type
     * @param lootingLevel The looting level on the killing weapon
     * @param lootingMultiplier The configured per-level looting multiplier
     * @return The final drop chance, clamped to 0.0 to 1.0
     */
    static double computeDropRate(double baseRate, int lootingLevel, double lootingMultiplier) {
        if (Double.isNaN(baseRate) || Double.isNaN(lootingMultiplier)) {
            return 0.0;
        }

        double safeBaseRate = Math.max(0.0, Math.min(baseRate, 1.0));
        double safeMultiplier = Math.max(0.0, lootingMultiplier);
        int safeLootingLevel = Math.max(0, lootingLevel);

        double finalRate = safeBaseRate * (1.0 + (safeLootingLevel * safeMultiplier));

        if (Double.isNaN(finalRate)) {
            return 0.0;
        }

        // Clamp to a real probability at both ends
        return Math.max(0.0, Math.min(finalRate, 1.0));
    }
}
