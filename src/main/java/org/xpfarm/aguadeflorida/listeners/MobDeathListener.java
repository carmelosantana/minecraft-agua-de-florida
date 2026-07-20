package org.xpfarm.aguadeflorida.listeners;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.enchantments.Enchantment;
import org.xpfarm.aguadeflorida.AguaDeFloridaPlugin;
import org.xpfarm.aguadeflorida.utils.ConfigManager;
import org.xpfarm.aguadeflorida.utils.AguaItemBuilder;

import java.util.Random;

/**
 * Handles mob death events and drops Agua de Florida items
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

        // Check if mob drops are enabled
        if (!configManager.isMobDropsEnabled()) {
            return;
        }

        LivingEntity entity = event.getEntity();
        
        // Check if this mob type can drop Agua de Florida
        if (!configManager.getDropMobTypes().contains(entity.getType())) {
            return;
        }
        
        // Check if killed by a player
        Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }
        
        // Calculate drop chance
        double dropChance = calculateDropChance(killer);
        
        // Roll for drop
        if (random.nextDouble() < dropChance) {
            // Add Agua de Florida to drops
            ItemStack aguaItem = itemBuilder.getCachedItem();
            event.getDrops().add(aguaItem);
            
            // Log the drop if enabled
            if (configManager.isLogDrops()) {
                plugin.getLogger().info("Agua de Florida dropped by " + entity.getType().name() + 
                    " killed by " + killer.getName() + " (chance: " + String.format("%.2f%%", dropChance * 100) + ")");
            }
            
            plugin.debugLog("Agua de Florida dropped for " + killer.getName() + " from " + entity.getType().name());
        }
    }
    
    /**
     * Calculate the drop chance based on base rate and looting enchantment
     * @param killer The player who killed the mob
     * @return The calculated drop chance (0.0 to 1.0)
     */
    private double calculateDropChance(Player killer) {
        double baseRate = configManager.getDropRate();
        
        // Get looting level from killer's weapon
        ItemStack weapon = killer.getInventory().getItemInMainHand();
        int lootingLevel = 0;
        
        if (weapon != null && weapon.hasItemMeta()) {
            lootingLevel = weapon.getItemMeta().getEnchantLevel(Enchantment.LOOTING);
        }
        
        return computeDropRate(baseRate, lootingLevel, configManager.getLootingMultiplier());
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
     * @param baseRate The configured base drop rate
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
