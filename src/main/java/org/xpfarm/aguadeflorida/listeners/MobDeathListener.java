package org.xpfarm.aguadeflorida.listeners;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
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
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDeath(EntityDeathEvent event) {
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
        
        // Calculate final drop rate
        double lootingMultiplier = configManager.getLootingMultiplier();
        double finalRate = baseRate * (1.0 + (lootingLevel * lootingMultiplier));
        
        // Cap at 100% chance
        return Math.min(finalRate, 1.0);
    }
}
