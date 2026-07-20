package org.xpfarm.aguadeflorida.utils;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.xpfarm.aguadeflorida.AguaDeFloridaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for creating and managing Agua de Florida items
 */
public class AguaItemBuilder {
    
    private final AguaDeFloridaPlugin plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey recipeKey;
    private ItemStack cachedItem;
    private boolean recipeRegistered = false;
    
    public AguaItemBuilder(AguaDeFloridaPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "agua_de_florida");
        this.recipeKey = new NamespacedKey(plugin, "agua_de_florida_recipe");
        updateCachedItem();
    }
    
    /**
     * Create a new Agua de Florida item based on current configuration
     * @return ItemStack representing Agua de Florida
     */
    public ItemStack createAguaDeFloridaItem() {
        ConfigManager config = plugin.getConfigManager();
        Material material = config.getItemMaterial();
        
        // Safety check for null material
        if (material == null) {
            plugin.getLogger().warning("Material is null, using WATER_BUCKET as fallback");
            material = Material.WATER_BUCKET;
        }
        
        ItemStack item = new ItemStack(material);
        
        item.editMeta(meta -> {
            // Set display name (italics off so the config formatting shows as written)
            Component displayName = LegacyComponentSerializer.legacySection()
                .deserialize(config.getItemName())
                .decoration(TextDecoration.ITALIC, false);
            meta.displayName(displayName);

            // Set lore
            List<Component> lore = new ArrayList<>();
            for (String loreLine : config.getItemLore()) {
                Component component = LegacyComponentSerializer.legacySection()
                    .deserialize(loreLine)
                    .decoration(TextDecoration.ITALIC, false);
                lore.add(component);
            }
            meta.lore(lore);
            
            // Add enchantment glow if configured
            if (config.isItemEnchanted()) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            
            // Set unbreakable if configured
            if (config.isItemUnbreakable()) {
                meta.setUnbreakable(true);
                meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            }
            
            // Add custom data to identify this as Agua de Florida
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BOOLEAN, true);
        });
        
        return item;
    }
    
    /**
     * Check if an ItemStack is Agua de Florida
     * @param item The item to check
     * @return true if the item is Agua de Florida
     */
    public boolean isAguaDeFloridaItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        // Read the value, not just key presence: an item tagged explicitly false is not ours
        return isTagPresentAndTrue(meta.getPersistentDataContainer().get(itemKey, PersistentDataType.BOOLEAN));
    }

    /**
     * Evaluate a persistent data tag value as a genuine-item marker
     * @param tagValue The value read from the container, may be null when absent
     * @return true only when the tag is present and true
     */
    static boolean isTagPresentAndTrue(Boolean tagValue) {
        return Boolean.TRUE.equals(tagValue);
    }

    /**
     * Get a cached Agua de Florida item for performance
     * @return Cached ItemStack
     */
    public ItemStack getCachedItem() {
        if (cachedItem == null) {
            updateCachedItem();
        }
        return cachedItem.clone();
    }
    
    /**
     * Update the cached item when configuration changes
     */
    public void updateCachedItem() {
        cachedItem = createAguaDeFloridaItem();
    }
    
    /**
     * Register the crafting recipe for Agua de Florida
     */
    public void registerRecipe() {
        if (recipeRegistered) {
            return;
        }
        
        try {
            ShapedRecipe recipe = new ShapedRecipe(recipeKey, createAguaDeFloridaItem());
            
            // Set recipe pattern from config
            recipe.shape("FLF", "LWL", "FCF");
            
            // Set ingredients from config
            recipe.setIngredient('F', Material.YELLOW_DYE);
            recipe.setIngredient('L', Material.SWEET_BERRIES);
            recipe.setIngredient('W', Material.WATER_BUCKET);
            recipe.setIngredient('C', Material.GOLD_NUGGET);
            
            plugin.getServer().addRecipe(recipe);
            recipeRegistered = true;
            plugin.debugLog("Agua de Florida recipe registered");
            
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to register Agua de Florida recipe: " + e.getMessage());
        }
    }
    
    /**
     * Unregister the crafting recipe
     */
    public void unregisterRecipe() {
        if (!recipeRegistered) {
            return;
        }
        
        try {
            plugin.getServer().removeRecipe(recipeKey);
            plugin.debugLog("Agua de Florida recipe unregistered");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to unregister Agua de Florida recipe: " + e.getMessage());
        } finally {
            // Always clear the flag: leaving it set would make registerRecipe() early-return
            // forever, so a reload could never rebuild the recipe with the new result item
            recipeRegistered = false;
        }
    }
    
    /**
     * Check if the recipe is currently registered
     * @return true if registered
     */
    public boolean isRecipeRegistered() {
        return recipeRegistered;
    }
}
