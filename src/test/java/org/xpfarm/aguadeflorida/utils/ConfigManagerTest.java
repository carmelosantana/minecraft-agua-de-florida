package org.xpfarm.aguadeflorida.utils;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for the config validation helpers in ConfigManager.
 *
 * ConfigManager itself takes an AguaDeFloridaPlugin and calls plugin.getConfig()
 * and plugin.getLogger(), and its effect parsing builds PotionEffect instances,
 * which need the Bukkit registries from a running server. So loadConfig() as a
 * whole is verified at runtime, and only the value clamping is unit tested here,
 * against the same YamlConfiguration parsing that loadConfig() reads through.
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
        yaml.set("mob_drops.drop_rate", -0.5);
        yaml.set("mob_drops.looting_multiplier", -2.0);

        double rawRate = yaml.getDouble("mob_drops.drop_rate", 0.05);
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
        assertEquals(0.05, yaml.getDouble("mob_drops.drop_rate", 0.05), DELTA);
    }

    @Test
    @DisplayName("A key written with no value counts as absent and uses the default")
    void emptyKeyIsTreatedAsAbsent() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("item:\n  material:\nmob_drops:\n  drop_rate:\n");

        assertFalse(yaml.contains("item.material"));
        assertEquals("WATER_BUCKET", yaml.getString("item.material", "WATER_BUCKET"));
        assertEquals(0.05, yaml.getDouble("mob_drops.drop_rate", 0.05), DELTA);
    }

    @Test
    @DisplayName("Reading with no default at all still yields null")
    void missingKeyWithNoDefaultIsNull() {
        YamlConfiguration yaml = new YamlConfiguration();
        assertNull(yaml.getString("item.material"));
    }
}
