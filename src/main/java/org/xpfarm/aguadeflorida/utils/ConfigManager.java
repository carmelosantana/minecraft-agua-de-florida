package org.xpfarm.aguadeflorida.utils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.xpfarm.aguadeflorida.AguaDeFloridaPlugin;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

/**
 * Manages configuration loading and provides easy access to config values
 */
public class ConfigManager {
    
    private final AguaDeFloridaPlugin plugin;
    private FileConfiguration config;
    
    // Cached values for performance
    private String itemName;
    private List<String> itemLore;
    private Material itemMaterial;
    private boolean itemEnchanted;
    private boolean itemUnbreakable;
    private double restoreHealth;
    private List<PotionEffect> effects;
    private boolean showAnimation;
    private boolean playSound;
    private boolean consumeOnUse;
    private boolean mobDropsEnabled;
    private Set<EntityType> dropMobTypes;
    private double dropRate;
    private double lootingMultiplier;
    private boolean recipeEnabled;
    private boolean debugEnabled;
    private boolean logSaves;
    private boolean logDrops;
    private boolean autoMoveToMainHand;
    
    public ConfigManager(AguaDeFloridaPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Load configuration from file and cache values
     */
    public void loadConfig() {
        config = plugin.getConfig();
        
        // Load item settings
        itemName = config.getString("item.name", "&b&lAgua de Florida &r&7(Spiritual Protection)");
        itemLore = config.getStringList("item.lore");
        String materialName = config.getString("item.material", "WATER_BUCKET");
        try {
            itemMaterial = Material.valueOf(materialName.toUpperCase());
            if (itemMaterial == null) {
                throw new IllegalArgumentException("Material resolved to null");
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material: " + materialName + ", using WATER_BUCKET");
            itemMaterial = Material.WATER_BUCKET;
        }
        itemEnchanted = config.getBoolean("item.enchanted", true);
        itemUnbreakable = config.getBoolean("item.unbreakable", true);
        
        // Load totem settings
        restoreHealth = config.getDouble("totem.restore_health", 1.0);
        loadEffects();
        showAnimation = config.getBoolean("totem.show_animation", true);
        playSound = config.getBoolean("totem.play_sound", true);
        consumeOnUse = config.getBoolean("totem.consume_on_use", true);
        
        // Load mob drops settings
        mobDropsEnabled = config.getBoolean("mob_drops.enabled", true);
        loadDropMobTypes();
        dropRate = config.getDouble("mob_drops.drop_rate", 0.05);
        lootingMultiplier = config.getDouble("mob_drops.looting_multiplier", 0.5);
        
        // Load recipe settings
        recipeEnabled = config.getBoolean("recipe.enabled", false);
        
        // Load debug settings
        debugEnabled = config.getBoolean("debug.enabled", false);
        logSaves = config.getBoolean("debug.log_saves", true);
        logDrops = config.getBoolean("debug.log_drops", true);
        
        // Load cross-platform settings
        autoMoveToMainHand = config.getBoolean("cross_platform.auto_move_to_main_hand", false);
    }
    
    /**
     * Load potion effects from configuration
     */
    private void loadEffects() {
        effects = new ArrayList<>();
        
        // Regeneration
        int regenDuration = config.getInt("totem.effects.regeneration.duration", 900);
        int regenAmplifier = config.getInt("totem.effects.regeneration.amplifier", 1);
        effects.add(new PotionEffect(PotionEffectType.REGENERATION, regenDuration, regenAmplifier));
        
        // Absorption
        int absorpDuration = config.getInt("totem.effects.absorption.duration", 100);
        int absorpAmplifier = config.getInt("totem.effects.absorption.amplifier", 1);
        effects.add(new PotionEffect(PotionEffectType.ABSORPTION, absorpDuration, absorpAmplifier));
        
        // Fire Resistance
        int fireresDuration = config.getInt("totem.effects.fire_resistance.duration", 800);
        int fireresAmplifier = config.getInt("totem.effects.fire_resistance.amplifier", 0);
        effects.add(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, fireresDuration, fireresAmplifier));
    }
    
    /**
     * Load mob types that can drop Agua de Florida
     */
    private void loadDropMobTypes() {
        dropMobTypes = new HashSet<>();
        List<String> mobTypeNames = config.getStringList("mob_drops.mob_types");
        
        for (String mobTypeName : mobTypeNames) {
            try {
                EntityType entityType = EntityType.valueOf(mobTypeName.toUpperCase());
                dropMobTypes.add(entityType);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid mob type: " + mobTypeName);
            }
        }
    }
    
    // Getter methods for cached values
    
    public String getItemName() { return itemName; }
    public List<String> getItemLore() { return itemLore; }
    public Material getItemMaterial() { return itemMaterial; }
    public boolean isItemEnchanted() { return itemEnchanted; }
    public boolean isItemUnbreakable() { return itemUnbreakable; }
    public double getRestoreHealth() { return restoreHealth; }
    public List<PotionEffect> getEffects() { return effects; }
    public boolean isShowAnimation() { return showAnimation; }
    public boolean isPlaySound() { return playSound; }
    public boolean isConsumeOnUse() { return consumeOnUse; }
    public boolean isMobDropsEnabled() { return mobDropsEnabled; }
    public Set<EntityType> getDropMobTypes() { return dropMobTypes; }
    public double getDropRate() { return dropRate; }
    public double getLootingMultiplier() { return lootingMultiplier; }
    public boolean isRecipeEnabled() { return recipeEnabled; }
    public boolean isDebugEnabled() { return debugEnabled; }
    public boolean isLogSaves() { return logSaves; }
    public boolean isLogDrops() { return logDrops; }
    public boolean isAutoMoveToMainHand() { return autoMoveToMainHand; }
    
    /**
     * Get a message from config with color codes translated
     * @param path The config path
     * @param defaultValue The default value if not found
     * @return The translated message
     */
    public String getMessage(String path, String defaultValue) {
        String message = config.getString("messages." + path, defaultValue);
        return message.replace('&', '§');
    }
}
