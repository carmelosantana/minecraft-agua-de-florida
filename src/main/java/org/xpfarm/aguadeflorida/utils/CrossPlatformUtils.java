package org.xpfarm.aguadeflorida.utils;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Utility class for handling cross-platform compatibility between Java and Bedrock players
 */
public class CrossPlatformUtils {
    
    private static boolean floodgateAvailable = false;
    
    static {
        try {
            Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            floodgateAvailable = true;
        } catch (ClassNotFoundException e) {
            floodgateAvailable = false;
        }
    }
    
    /**
     * Check if a player is a Bedrock player using Floodgate API
     * @param player The player to check
     * @return true if player is connecting from Bedrock Edition
     */
    public static boolean isBedrockPlayer(Player player) {
        if (!floodgateAvailable) {
            // Fallback: check for Geyser naming convention (username contains '.')
            return player.getName().contains(".");
        }
        
        try {
            // Use reflection to access FloodgateApi to avoid compile-time dependency
            Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object apiInstance = floodgateApiClass.getMethod("getInstance").invoke(null);
            return (Boolean) floodgateApiClass.getMethod("isFloodgatePlayer", java.util.UUID.class)
                    .invoke(apiInstance, player.getUniqueId());
        } catch (Exception e) {
            // Fallback: check for Geyser naming convention (username contains '.')
            return player.getName().contains(".");
        }
    }
    
    /**
     * Safely get an item from player's hands, prioritizing main hand for Bedrock players
     * @param player The player
     * @param inventory The player's inventory
     * @param itemChecker Function to check if an item is the desired type
     * @param autoMoveToMainHand Whether to automatically move offhand items to main hand for Bedrock players
     * @return HandSlotResult containing the item and slot information
     */
    public static HandSlotResult getItemFromHands(Player player, PlayerInventory inventory, 
                                                  java.util.function.Predicate<ItemStack> itemChecker,
                                                  boolean autoMoveToMainHand) {
        ItemStack mainHand = inventory.getItemInMainHand();
        ItemStack offHand = inventory.getItemInOffHand();
        
        boolean isBedrockPlayer = isBedrockPlayer(player);
        
        // Check main hand first (always works for both platforms)
        if (itemChecker.test(mainHand)) {
            return new HandSlotResult(mainHand, HandSlot.MAIN_HAND, true);
        }
        
        // For Bedrock players, be more cautious about offhand
        if (!isBedrockPlayer && itemChecker.test(offHand)) {
            return new HandSlotResult(offHand, HandSlot.OFF_HAND, true);
        } else if (isBedrockPlayer && itemChecker.test(offHand)) {
            // Bedrock player has item in offhand
            if (autoMoveToMainHand && (mainHand == null || mainHand.getType().isAir())) {
                // Auto-move enabled and main hand is empty, move offhand item there
                inventory.setItemInMainHand(offHand);
                inventory.setItemInOffHand(null);
                return new HandSlotResult(offHand, HandSlot.MAIN_HAND, true);
            } else {
                // Either auto-move is disabled or main hand is occupied
                // For Bedrock players, this might be problematic, but we'll allow it
                return new HandSlotResult(offHand, HandSlot.OFF_HAND, true);
            }
        }
        
        return new HandSlotResult(null, null, false);
    }
    
    /**
     * Safely set an item in a player's hand slot, with special handling for Bedrock players
     * @param player The player
     * @param inventory The player's inventory
     * @param item The item to set
     * @param slot The target slot
     */
    public static void setItemInHand(Player player, PlayerInventory inventory, ItemStack item, HandSlot slot) {
        boolean isBedrockPlayer = isBedrockPlayer(player);
        
        if (slot == HandSlot.OFF_HAND && isBedrockPlayer) {
            // For Bedrock players, try to avoid using offhand when possible
            ItemStack mainHand = inventory.getItemInMainHand();
            if (mainHand == null || mainHand.getType().isAir()) {
                // Main hand is empty, use it instead
                inventory.setItemInMainHand(item);
                return;
            }
            // Fall through to normal offhand setting if main hand is occupied
        }
        
        if (slot == HandSlot.MAIN_HAND) {
            inventory.setItemInMainHand(item);
        } else if (slot == HandSlot.OFF_HAND) {
            inventory.setItemInOffHand(item);
        }
    }
    
    /**
     * Safely consume an item from a player's hand, with cross-platform considerations
     * @param player The player
     * @param inventory The player's inventory
     * @param slot The slot to consume from
     * @param amount The amount to consume
     */
    public static void consumeItemFromHand(Player player, PlayerInventory inventory, HandSlot slot, int amount) {
        if (slot == HandSlot.MAIN_HAND) {
            ItemStack currentItem = inventory.getItemInMainHand();
            if (currentItem != null && !currentItem.getType().isAir()) {
                int newAmount = Math.max(0, currentItem.getAmount() - amount);
                if (newAmount == 0) {
                    inventory.setItemInMainHand(null);
                } else {
                    currentItem.setAmount(newAmount);
                    inventory.setItemInMainHand(currentItem);
                }
            }
        } else if (slot == HandSlot.OFF_HAND) {
            ItemStack currentItem = inventory.getItemInOffHand();
            if (currentItem != null && !currentItem.getType().isAir()) {
                int newAmount = Math.max(0, currentItem.getAmount() - amount);
                if (newAmount == 0) {
                    inventory.setItemInOffHand(null);
                } else {
                    currentItem.setAmount(newAmount);
                    inventory.setItemInOffHand(currentItem);
                }
            }
        }
    }
    
    /**
     * Enum representing hand slots
     */
    public enum HandSlot {
        MAIN_HAND,
        OFF_HAND
    }
    
    /**
     * Result class for hand slot operations
     */
    public static class HandSlotResult {
        private final ItemStack item;
        private final HandSlot slot;
        private final boolean found;
        
        public HandSlotResult(ItemStack item, HandSlot slot, boolean found) {
            this.item = item;
            this.slot = slot;
            this.found = found;
        }
        
        public ItemStack getItem() { return item; }
        public HandSlot getSlot() { return slot; }
        public boolean isFound() { return found; }
        public boolean isMainHand() { return slot == HandSlot.MAIN_HAND; }
        public boolean isOffHand() { return slot == HandSlot.OFF_HAND; }
    }
}
