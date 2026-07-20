package org.xpfarm.aguadeflorida.utils;

import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.entity.EntityType;
import org.xpfarm.aguadeflorida.AguaDeFloridaPlugin;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Manages configuration loading and provides easy access to config values
 */
public class ConfigManager {

    /** Highest per-level looting multiplier accepted from config. */
    static final double MAX_LOOTING_MULTIPLIER = 10.0;
    /** Highest potion amplifier accepted from config. */
    static final int MAX_EFFECT_AMPLIFIER = 255;
    /** Highest potion duration accepted from config, in ticks. */
    static final int MAX_EFFECT_DURATION = 1000000;

    /** Documented fallback tint for item.color when the configured value is unusable. */
    static final String DEFAULT_ITEM_COLOR_HEX = "#1E90FF";
    /** Documented fallback base drop rate for a mob listed without its own rate. */
    static final double DEFAULT_DROP_RATE = 0.05;

    private final AguaDeFloridaPlugin plugin;
    private FileConfiguration config;

    // Cached values for performance
    private String itemName;
    private List<String> itemLore;
    private boolean itemEnchanted;
    private Color itemColor;
    private List<PotionEffect> effects;
    private boolean mobDropsEnabled;
    private Map<EntityType, Double> dropRates;
    private double defaultDropRate;
    private double lootingMultiplier;
    private boolean debugEnabled;
    private boolean logSaves;
    private boolean logDrops;

    public ConfigManager(AguaDeFloridaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Load configuration from file and cache values
     */
    public void loadConfig() {
        config = plugin.getConfig();

        // Load item settings
        // item.material is deliberately not configurable: Material.POTION is what makes
        // the item inert on right-click, so exposing it would reinstate the 1.x defect.
        itemName = config.getString("item.name", "&b&lAgua de Florida &r&7(Spiritual Protection)");
        itemLore = config.getStringList("item.lore");
        itemEnchanted = config.getBoolean("item.enchanted", true);
        // A bad hex string must never abort the load, so parsing falls back instead of throwing
        String colorValue = config.getString("item.color", DEFAULT_ITEM_COLOR_HEX);
        itemColor = parseColor(colorValue, defaultItemColor());
        if (itemColor.equals(defaultItemColor()) && !isDefaultColorHex(colorValue)) {
            plugin.getLogger().warning("Invalid item.color: " + colorValue
                + " (expected an RGB hex string such as " + DEFAULT_ITEM_COLOR_HEX
                + "), using " + DEFAULT_ITEM_COLOR_HEX);
        }

        // Load totem settings
        // The resurrection itself is vanilla now (minecraft:death_protection), so the old
        // restore_health / show_animation / play_sound / consume_on_use keys are gone.
        loadEffects();

        // Load mob drops settings
        mobDropsEnabled = config.getBoolean("mob_drops.enabled", true);
        // Validate at load time so an out-of-range value is reported to the admin,
        // instead of silently producing an impossible drop chance at kill time
        defaultDropRate = loadClampedDouble("mob_drops.default_rate", DEFAULT_DROP_RATE, 0.0, 1.0);
        loadDropRates();
        lootingMultiplier = loadClampedDouble("mob_drops.looting_multiplier", 0.5, 0.0, MAX_LOOTING_MULTIPLIER);

        // Load debug settings
        debugEnabled = config.getBoolean("debug.enabled", false);
        logSaves = config.getBoolean("debug.log_saves", true);
        logDrops = config.getBoolean("debug.log_drops", true);
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
     * Parse an RGB hex string into a colour, tolerating a missing leading '#'
     * and falling back rather than throwing on anything unusable.
     * @param raw The configured value, may be null
     * @param fallback The colour to use when the value is missing or malformed
     * @return The parsed colour, or the fallback
     */
    static Color parseColor(String raw, Color fallback) {
        if (raw == null) {
            return fallback;
        }
        String hex = raw.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != 6) {
            return fallback;
        }
        try {
            int rgb = Integer.parseInt(hex, 16);
            return Color.fromRGB(rgb);
        } catch (IllegalArgumentException e) {
            // Covers both a non-hex string and a component out of Color's own range
            return fallback;
        }
    }

    /**
     * @return The documented default potion tint
     */
    static Color defaultItemColor() {
        // Parsed rather than hard-coded so the constant and the colour cannot drift apart
        return Color.fromRGB(Integer.parseInt(DEFAULT_ITEM_COLOR_HEX.substring(1), 16));
    }

    /**
     * Whether a configured value is just the default hex written out, so a successful
     * parse of it is not mistaken for a parse failure when logging
     * @param raw The configured value, may be null
     * @return true when the value names the default colour
     */
    private static boolean isDefaultColorHex(String raw) {
        if (raw == null) {
            return false;
        }
        String hex = raw.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        return hex.equalsIgnoreCase(DEFAULT_ITEM_COLOR_HEX.substring(1));
    }

    /**
     * Load the per-mob base drop rates.
     *
     * The 2.0.0 shape is a mapping of entity type to rate under mob_drops.mob_types.
     * A plain list is still accepted so a 1.x config keeps working: every listed mob
     * then uses mob_drops.default_rate.
     */
    private void loadDropRates() {
        dropRates = new EnumMap<>(EntityType.class);

        ConfigurationSection section = config.getConfigurationSection("mob_drops.mob_types");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                EntityType type = parseEntityType(key);
                if (type == null) {
                    continue;
                }
                double rate = loadClampedDouble("mob_drops.mob_types." + key, defaultDropRate, 0.0, 1.0);
                dropRates.put(type, rate);
            }
            return;
        }

        // Legacy list form: names only, no per-mob rate
        for (String name : config.getStringList("mob_drops.mob_types")) {
            EntityType type = parseEntityType(name);
            if (type != null) {
                dropRates.put(type, defaultDropRate);
            }
        }
    }

    /**
     * Resolve a configured mob name, warning and returning null when it names nothing
     * @param name The configured mob name, may be null
     * @return The entity type, or null when unusable
     */
    private EntityType parseEntityType(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        try {
            return EntityType.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid mob type: " + name);
            return null;
        }
    }

    // Getter methods for cached values

    public String getItemName() { return itemName; }
    public List<String> getItemLore() { return itemLore; }
    public boolean isItemEnchanted() { return itemEnchanted; }
    public Color getItemColor() { return itemColor; }
    public List<PotionEffect> getEffects() { return effects; }
    public boolean isMobDropsEnabled() { return mobDropsEnabled; }
    public double getLootingMultiplier() { return lootingMultiplier; }
    public boolean isDebugEnabled() { return debugEnabled; }
    public boolean isLogSaves() { return logSaves; }
    public boolean isLogDrops() { return logDrops; }

    /**
     * @return The mob types configured to drop the item, never null
     */
    public Set<EntityType> getDropMobTypes() {
        return Collections.unmodifiableSet(dropRates.keySet());
    }

    /**
     * Base drop chance for one mob type, before any looting bonus.
     *
     * Returns 0.0 for a mob that is not configured to drop at all, so callers can
     * use this as the single check instead of testing membership first.
     * @param type The mob that died, may be null
     * @return The base rate in [0.0, 1.0]
     */
    public double getDropRate(EntityType type) {
        if (type == null) {
            return 0.0;
        }
        return dropRates.getOrDefault(type, 0.0);
    }

    /**
     * @return The rate applied to a listed mob that has no rate of its own
     */
    public double getDefaultDropRate() { return defaultDropRate; }

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
