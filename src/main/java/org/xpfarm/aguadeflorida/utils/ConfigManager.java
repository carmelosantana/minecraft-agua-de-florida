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

    /** Highest per-level looting multiplier accepted from config. */
    static final double MAX_LOOTING_MULTIPLIER = 10.0;
    /** Highest restore_health accepted from config, in half-hearts. */
    static final double MAX_RESTORE_HEALTH = 1024.0;
    /** Highest potion amplifier accepted from config. */
    static final int MAX_EFFECT_AMPLIFIER = 255;
    /** Highest potion duration accepted from config, in ticks. */
    static final int MAX_EFFECT_DURATION = 1000000;

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
        // getString is declared nullable, so guard rather than assume the default applies
        String materialName = config.getString("item.material", "WATER_BUCKET");
        if (materialName == null || materialName.trim().isEmpty()) {
            plugin.getLogger().warning("Missing item.material, using WATER_BUCKET");
            itemMaterial = Material.WATER_BUCKET;
        } else {
            try {
                itemMaterial = Material.valueOf(materialName.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material: " + materialName + ", using WATER_BUCKET");
                itemMaterial = Material.WATER_BUCKET;
            }
        }
        itemEnchanted = config.getBoolean("item.enchanted", true);
        itemUnbreakable = config.getBoolean("item.unbreakable", true);
        
        // Load totem settings
        restoreHealth = loadClampedDouble("totem.restore_health", 1.0, 0.0, MAX_RESTORE_HEALTH);
        loadEffects();
        showAnimation = config.getBoolean("totem.show_animation", true);
        playSound = config.getBoolean("totem.play_sound", true);
        consumeOnUse = config.getBoolean("totem.consume_on_use", true);
        
        // Load mob drops settings
        mobDropsEnabled = config.getBoolean("mob_drops.enabled", true);
        loadDropMobTypes();
        // Validate at load time so an out-of-range value is reported to the admin,
        // instead of silently producing an impossible drop chance at kill time
        dropRate = loadClampedDouble("mob_drops.drop_rate", 0.05, 0.0, 1.0);
        lootingMultiplier = loadClampedDouble("mob_drops.looting_multiplier", 0.5, 0.0, MAX_LOOTING_MULTIPLIER);
        
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

        // PotionEffect rejects a negative duration or amplifier outright, which would
        // abort the whole config load, so every value is clamped and reported first.

        // Regeneration
        addEffect(PotionEffectType.REGENERATION, "regeneration", 900, 1);

        // Absorption
        addEffect(PotionEffectType.ABSORPTION, "absorption", 100, 1);

        // Fire Resistance
        addEffect(PotionEffectType.FIRE_RESISTANCE, "fire_resistance", 800, 0);
    }

    /**
     * Read one configured potion effect, clamping duration and amplifier to sane values
     * @param type The effect to apply
     * @param key The config key under totem.effects
     * @param defaultDuration The duration in ticks to use when unset or invalid
     * @param defaultAmplifier The amplifier to use when unset or invalid
     */
    private void addEffect(PotionEffectType type, String key, int defaultDuration, int defaultAmplifier) {
        int duration = loadClampedInt("totem.effects." + key + ".duration", defaultDuration, 0, MAX_EFFECT_DURATION);
        int amplifier = loadClampedInt("totem.effects." + key + ".amplifier", defaultAmplifier, 0, MAX_EFFECT_AMPLIFIER);
        effects.add(new PotionEffect(type, duration, amplifier));
    }

    /**
     * Read a double from config, clamping it to a range and warning when it was out of range
     * @param path The config path
     * @param defaultValue The value to use when unset or unusable
     * @param min The lowest accepted value
     * @param max The highest accepted value
     * @return The clamped value
     */
    private double loadClampedDouble(String path, double defaultValue, double min, double max) {
        double raw = config.getDouble(path, defaultValue);
        double value = clamp(raw, min, max, defaultValue);
        if (value != raw) {
            plugin.getLogger().warning("Invalid " + path + ": " + raw
                + " (expected " + min + " to " + max + "), using " + value);
        }
        return value;
    }

    /**
     * Read an int from config, clamping it to a range and warning when it was out of range
     * @param path The config path
     * @param defaultValue The value to use when unset
     * @param min The lowest accepted value
     * @param max The highest accepted value
     * @return The clamped value
     */
    private int loadClampedInt(String path, int defaultValue, int min, int max) {
        int raw = config.getInt(path, defaultValue);
        int value = Math.max(min, Math.min(raw, max));
        if (value != raw) {
            plugin.getLogger().warning("Invalid " + path + ": " + raw
                + " (expected " + min + " to " + max + "), using " + value);
        }
        return value;
    }

    /**
     * Clamp a configured double into a range, falling back for values that are not real numbers
     * @param value The configured value
     * @param min The lowest accepted value
     * @param max The highest accepted value
     * @param defaultValue The value to use when NaN
     * @return The clamped value
     */
    static double clamp(double value, double min, double max, double defaultValue) {
        if (Double.isNaN(value)) {
            return defaultValue;
        }
        return Math.max(min, Math.min(value, max));
    }
    
    /**
     * Load mob types that can drop Agua de Florida
     */
    private void loadDropMobTypes() {
        dropMobTypes = new HashSet<>();
        List<String> mobTypeNames = config.getStringList("mob_drops.mob_types");
        
        for (String mobTypeName : mobTypeNames) {
            if (mobTypeName == null || mobTypeName.trim().isEmpty()) {
                continue;
            }
            try {
                EntityType entityType = EntityType.valueOf(mobTypeName.trim().toUpperCase());
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
        // getString is declared nullable, and callers may pass a null default
        String message = config.getString("messages." + path, defaultValue);
        if (message == null) {
            message = defaultValue;
        }
        return message == null ? "" : message.replace('&', '§');
    }
}
