package org.xpfarm.aguadeflorida.listeners;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.xpfarm.aguadeflorida.AguaDeFloridaPlugin;
import org.xpfarm.aguadeflorida.utils.ConfigManager;
import org.xpfarm.aguadeflorida.utils.AguaItemBuilder;
import org.xpfarm.aguadeflorida.utils.CrossPlatformUtils;

/**
 * Handles player death events and activates Agua de Florida if present
 */
public class PlayerDeathListener implements Listener {
    
    private final AguaDeFloridaPlugin plugin;
    private final ConfigManager configManager;
    private final AguaItemBuilder itemBuilder;
    
    public PlayerDeathListener(AguaDeFloridaPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.itemBuilder = plugin.getItemBuilder();
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PlayerInventory inventory = player.getInventory();
        
                // Check for Agua de Florida in player's hands using cross-platform compatibility
        CrossPlatformUtils.HandSlotResult result = CrossPlatformUtils.getItemFromHands(
            player, 
            player.getInventory(), 
            item -> itemBuilder.isAguaDeFloridaItem(item),
            configManager.isAutoMoveToMainHand()
        );
        
        // If no Agua de Florida found, let death proceed normally
        if (!result.isFound()) {
            return;
        }
        
        // Log platform information for debugging
        boolean isBedrockPlayer = CrossPlatformUtils.isBedrockPlayer(player);
        plugin.debugLog("Agua de Florida activated for " + player.getName() + 
                       " (Platform: " + (isBedrockPlayer ? "Bedrock" : "Java") + 
                       ", Slot: " + result.getSlot() + ")");
        
        // Prevent the death
        event.setCancelled(true);
        
        // Activate the Agua de Florida effect
        activateAguaDeFloridaEffect(player, result.getItem(), result.getSlot());
        
        // Log the save if enabled
        if (configManager.isLogSaves()) {
            plugin.getLogger().info("Player " + player.getName() + " was saved by Agua de Florida" +
                                   (isBedrockPlayer ? " (Bedrock player)" : " (Java player)"));
        }
    }
    
    /**
     * Activate the Agua de Florida effect, replicating totem behavior
     * @param player The player being saved
     * @param aguaItem The Agua de Florida item
     * @param slot The hand slot containing the item
     */
    private void activateAguaDeFloridaEffect(Player player, ItemStack aguaItem, CrossPlatformUtils.HandSlot slot) {
        // Restore health
        double restoreHealth = Math.max(configManager.getRestoreHealth(), 0.5); // Minimum 0.5 hearts
        player.setHealth(restoreHealth);
        
        // Apply potion effects
        for (PotionEffect effect : configManager.getEffects()) {
            player.addPotionEffect(effect);
        }
        
        // Play totem sound if enabled
        if (configManager.isPlaySound()) {
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
        }
        
        // Show totem animation if enabled (requires scheduling to avoid event conflicts)
        if (configManager.isShowAnimation()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    // Create a totem item stack for the animation
                    ItemStack totemForAnimation = new ItemStack(org.bukkit.Material.TOTEM_OF_UNDYING);
                    
                    // Trigger the totem animation by temporarily setting totem in hand
                    PlayerInventory inventory = player.getInventory();
                    ItemStack originalItem = slot == CrossPlatformUtils.HandSlot.MAIN_HAND ? 
                        inventory.getItemInMainHand() : inventory.getItemInOffHand();
                    
                    // Set totem temporarily for animation using cross-platform utility
                    CrossPlatformUtils.setItemInHand(player, inventory, totemForAnimation, slot);
                    
                    // Schedule restoration of original item after animation
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            CrossPlatformUtils.setItemInHand(player, inventory, originalItem, slot);
                        }
                    }.runTaskLater(plugin, 5L); // Restore after 5 ticks (0.25 seconds)
                }
            }.runTaskLater(plugin, 1L); // Run after 1 tick to avoid event conflicts
        }
        
        // Consume the item if configured
        if (configManager.isConsumeOnUse()) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    PlayerInventory inventory = player.getInventory();
                    ItemStack currentItem = slot == CrossPlatformUtils.HandSlot.MAIN_HAND ? 
                        inventory.getItemInMainHand() : inventory.getItemInOffHand();
                    
                    if (itemBuilder.isAguaDeFloridaItem(currentItem)) {
                        CrossPlatformUtils.consumeItemFromHand(player, inventory, slot, 1);
                    }
                }
            }.runTaskLater(plugin, 10L); // Consume after animation completes
        }
        
        // Send message to player
        String message = configManager.getMessage("life_saved", 
            "§eThe Agua de Florida has cleansed your spirit and granted you a second chance!");
        player.sendMessage(Component.text(message.replace('§', '&'), NamedTextColor.YELLOW));
        
        // Clear death-related effects
        player.setFireTicks(0);
        player.setFallDistance(0);
    }
}
