package org.xpfarm.aguadeflorida.utils;

import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the config validation helpers in ConfigManager.
 *
 * ConfigManager itself takes an AguaDeFloridaPlugin and calls plugin.getConfig()
 * and plugin.getLogger(), and its effect parsing builds PotionEffect instances,
 * which need the Bukkit registries from a running server. So loadConfig() as a
 * whole is verified at runtime, and only the value clamping, colour parsing and
 * mob_drops shape are unit tested here, against the same YamlConfiguration
 * parsing that loadConfig() reads through.
 */
class ConfigManagerTest {

    private static final double DELTA = 1e-9;

    @Test
    @DisplayName("A value inside the range is left alone")
    void inRangeValueIsUnchanged() {
        assertEquals(0.05, ConfigManager.clamp(0.05, 0.0, 1.0, 0.05), DELTA);
    }

    @Test
    @DisplayName("A negative drop rate is clamped up to zero")
    void negativeIsClampedToMinimum() {
        assertEquals(0.0, ConfigManager.clamp(-0.25, 0.0, 1.0, 0.05), DELTA);
    }

    @Test
    @DisplayName("A drop rate above one is clamped down to one")
    void aboveMaximumIsClampedDown() {
        assertEquals(1.0, ConfigManager.clamp(4.0, 0.0, 1.0, 0.05), DELTA);
    }

    @Test
    @DisplayName("The range boundaries are themselves accepted")
    void boundariesAreAccepted() {
        assertEquals(0.0, ConfigManager.clamp(0.0, 0.0, 1.0, 0.05), DELTA);
        assertEquals(1.0, ConfigManager.clamp(1.0, 0.0, 1.0, 0.05), DELTA);
    }

    @Test
    @DisplayName("A negative looting multiplier is clamped up to zero")
    void negativeLootingMultiplierIsClamped() {
        assertEquals(0.0, ConfigManager.clamp(-2.0, 0.0, ConfigManager.MAX_LOOTING_MULTIPLIER, 0.5), DELTA);
    }

    @Test
    @DisplayName("An absurd looting multiplier is capped at the maximum")
    void hugeLootingMultiplierIsCapped() {
        assertEquals(ConfigManager.MAX_LOOTING_MULTIPLIER,
            ConfigManager.clamp(9999.0, 0.0, ConfigManager.MAX_LOOTING_MULTIPLIER, 0.5), DELTA);
    }

    @Test
    @DisplayName("A value that is not a real number falls back to the default")
    void notANumberFallsBackToDefault() {
        assertEquals(0.05, ConfigManager.clamp(Double.NaN, 0.0, 1.0, 0.05), DELTA);
    }

    @Test
    @DisplayName("Infinities are clamped to the range ends")
    void infinitiesAreClamped() {
        assertEquals(1.0, ConfigManager.clamp(Double.POSITIVE_INFINITY, 0.0, 1.0, 0.05), DELTA);
        assertEquals(0.0, ConfigManager.clamp(Double.NEGATIVE_INFINITY, 0.0, 1.0, 0.05), DELTA);
    }

    @Test
    @DisplayName("YAML supplies the raw out-of-range values that clamping then corrects")
    void yamlValuesAreClampedIntoRange() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("mob_drops.mob_types.WITCH", -0.5);
        yaml.set("mob_drops.looting_multiplier", -2.0);

        double rawRate = yaml.getDouble("mob_drops.mob_types.WITCH", 0.05);
        double rawMultiplier = yaml.getDouble("mob_drops.looting_multiplier", 0.5);

        assertEquals(-0.5, rawRate, DELTA);
        assertEquals(-2.0, rawMultiplier, DELTA);

        assertEquals(0.0, ConfigManager.clamp(rawRate, 0.0, 1.0, 0.05), DELTA);
        assertEquals(0.0, ConfigManager.clamp(rawMultiplier, 0.0, ConfigManager.MAX_LOOTING_MULTIPLIER, 0.5), DELTA);
    }

    @Test
    @DisplayName("A missing key falls through to the supplied default")
    void missingKeyUsesDefault() {
        YamlConfiguration yaml = new YamlConfiguration();
        assertEquals(0.05, yaml.getDouble("mob_drops.default_rate", 0.05), DELTA);
    }

    @Test
    @DisplayName("A key written with no value counts as absent and uses the default")
    void emptyKeyIsTreatedAsAbsent() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("item:\n  color:\nmob_drops:\n  default_rate:\n");

        assertFalse(yaml.contains("item.color"));
        assertEquals(ConfigManager.DEFAULT_ITEM_COLOR_HEX, yaml.getString("item.color", ConfigManager.DEFAULT_ITEM_COLOR_HEX));
        assertEquals(0.05, yaml.getDouble("mob_drops.default_rate", 0.05), DELTA);
    }

    @Test
    @DisplayName("Reading with no default at all still yields null")
    void missingKeyWithNoDefaultIsNull() {
        YamlConfiguration yaml = new YamlConfiguration();
        assertNull(yaml.getString("item.color"));
    }

    // item.color parsing

    @Test
    @DisplayName("A hex colour with a leading hash is parsed")
    void hexWithHashIsParsed() {
        Color parsed = ConfigManager.parseColor("#1E90FF", Color.BLACK);
        assertEquals(0x1E, parsed.getRed());
        assertEquals(0x90, parsed.getGreen());
        assertEquals(0xFF, parsed.getBlue());
    }

    @Test
    @DisplayName("A hex colour without a leading hash is parsed the same way")
    void hexWithoutHashIsParsed() {
        assertEquals(ConfigManager.parseColor("#1E90FF", Color.BLACK),
            ConfigManager.parseColor("1E90FF", Color.BLACK));
    }

    @Test
    @DisplayName("Surrounding whitespace and lower case hex are both tolerated")
    void whitespaceAndCaseAreTolerated() {
        assertEquals(ConfigManager.parseColor("#1E90FF", Color.BLACK),
            ConfigManager.parseColor("  #1e90ff  ", Color.BLACK));
    }

    @Test
    @DisplayName("Pure black and pure white are accepted as written")
    void blackAndWhiteAreAccepted() {
        assertEquals(Color.fromRGB(0, 0, 0), ConfigManager.parseColor("#000000", Color.RED));
        assertEquals(Color.fromRGB(255, 255, 255), ConfigManager.parseColor("FFFFFF", Color.RED));
    }

    @Test
    @DisplayName("A malformed colour falls back instead of throwing")
    void malformedColourFallsBack() {
        Color fallback = ConfigManager.defaultItemColor();
        assertSame(fallback, ConfigManager.parseColor("not-a-colour", fallback));
        assertSame(fallback, ConfigManager.parseColor("#12345", fallback));      // too short
        assertSame(fallback, ConfigManager.parseColor("#1234567", fallback));    // too long
        assertSame(fallback, ConfigManager.parseColor("#ZZZZZZ", fallback));     // not hex
        assertSame(fallback, ConfigManager.parseColor("", fallback));
        assertSame(fallback, ConfigManager.parseColor(null, fallback));
    }

    @Test
    @DisplayName("The documented default colour is the one named in the constant")
    void defaultColourMatchesConstant() {
        assertEquals("#1E90FF", ConfigManager.DEFAULT_ITEM_COLOR_HEX);
        assertEquals(ConfigManager.parseColor(ConfigManager.DEFAULT_ITEM_COLOR_HEX, Color.BLACK),
            ConfigManager.defaultItemColor());
    }

    // mob_drops shape

    @Test
    @DisplayName("mob_types reads as a section of per-mob rates")
    void mobTypesIsAMappingOfRates() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
            mob_drops:
              default_rate: 0.05
              mob_types:
                WITCH: 0.08
                EVOKER: 0.25
                DROWNED: 0.01
              looting_multiplier: 0.5
            """);

        ConfigurationSection section = yaml.getConfigurationSection("mob_drops.mob_types");
        assertNotNull(section);
        assertEquals(List.of("WITCH", "EVOKER", "DROWNED"), List.copyOf(section.getKeys(false)));
        assertEquals(0.08, section.getDouble("WITCH"), DELTA);
        assertEquals(0.25, section.getDouble("EVOKER"), DELTA);
        assertEquals(0.01, section.getDouble("DROWNED"), DELTA);
        assertFalse(section.contains("VINDICATOR"));
    }

    @Test
    @DisplayName("An unparseable per-mob rate falls back to default_rate rather than throwing")
    void unparseablePerMobRateFallsBackToDefault() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
            mob_drops:
              default_rate: 0.05
              mob_types:
                WITCH: "not a number"
            """);

        ConfigurationSection section = yaml.getConfigurationSection("mob_drops.mob_types");
        assertNotNull(section);
        assertTrue(section.getKeys(false).contains("WITCH"));
        double defaultRate = yaml.getDouble("mob_drops.default_rate", ConfigManager.DEFAULT_DROP_RATE);
        assertEquals(0.05, yaml.getDouble("mob_drops.mob_types.WITCH", defaultRate), DELTA);
    }

    @Test
    @DisplayName("A mob entry written with no rate at all is dropped by YAML, so it is simply not listed")
    void blankPerMobRateIsTreatedAsAbsent() throws Exception {
        // Bukkit treats a null-valued key as absent, so such an entry never reaches
        // the loader at all. Documented in config.yml: every mob needs an explicit rate.
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
            mob_drops:
              mob_types:
                WITCH:
                EVOKER: 0.25
            """);

        ConfigurationSection section = yaml.getConfigurationSection("mob_drops.mob_types");
        assertNotNull(section);
        assertEquals(List.of("EVOKER"), List.copyOf(section.getKeys(false)));
        assertFalse(yaml.contains("mob_drops.mob_types.WITCH"));
    }

    @Test
    @DisplayName("A 1.x list-shaped mob_types is still readable as a list")
    void legacyListShapeIsStillReadable() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
            mob_drops:
              mob_types:
                - WITCH
                - EVOKER
            """);

        assertNull(yaml.getConfigurationSection("mob_drops.mob_types"));
        assertEquals(List.of("WITCH", "EVOKER"), yaml.getStringList("mob_drops.mob_types"));
    }

    @Test
    @DisplayName("The shipped defaults carry the 2.0.0 mob list and rates")
    void shippedDefaultsMatchTheReleaseSpec() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (var in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(in, "config.yml must be on the test classpath");
            yaml.loadFromString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }

        ConfigurationSection mobs = yaml.getConfigurationSection("mob_drops.mob_types");
        assertNotNull(mobs);
        assertEquals(List.of("WITCH", "EVOKER", "DROWNED"), List.copyOf(mobs.getKeys(false)));
        assertEquals(0.08, mobs.getDouble("WITCH"), DELTA);
        assertEquals(0.25, mobs.getDouble("EVOKER"), DELTA);
        assertEquals(0.01, mobs.getDouble("DROWNED"), DELTA);
        assertEquals(0.5, yaml.getDouble("mob_drops.looting_multiplier"), DELTA);
        assertEquals(ConfigManager.DEFAULT_ITEM_COLOR_HEX, yaml.getString("item.color"));
    }

    @Test
    @DisplayName("The shipped defaults no longer carry the keys removed in 2.0.0")
    void shippedDefaultsDropRemovedKeys() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (var in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(in, "config.yml must be on the test classpath");
            yaml.loadFromString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }

        assertFalse(yaml.contains("item.material"));
        assertFalse(yaml.contains("item.unbreakable"));
        assertFalse(yaml.contains("recipe"));
        assertFalse(yaml.contains("cross_platform"));
        assertFalse(yaml.contains("totem.restore_health"));
        assertFalse(yaml.contains("totem.show_animation"));
        assertFalse(yaml.contains("totem.play_sound"));
        assertFalse(yaml.contains("totem.consume_on_use"));
        assertFalse(yaml.contains("mob_drops.drop_rate"));

        // Retained keys must still be present
        assertTrue(yaml.contains("item.name"));
        assertTrue(yaml.contains("item.lore"));
        assertTrue(yaml.contains("item.enchanted"));
        assertTrue(yaml.contains("totem.effects.regeneration.duration"));
        assertTrue(yaml.contains("messages.item_given"));
        assertTrue(yaml.contains("debug.enabled"));
    }
}
