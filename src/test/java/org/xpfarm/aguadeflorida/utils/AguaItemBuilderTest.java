package org.xpfarm.aguadeflorida.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the parts of AguaItemBuilder that do not need a live server.
 *
 * Anything touching ItemStack, ItemMeta or the recipe manager requires a running
 * Bukkit instance (new ItemStack(...) calls Bukkit.getItemFactory()), so those
 * paths are verified at runtime rather than here.
 */
class AguaItemBuilderTest {

    @Test
    @DisplayName("A missing tag is not a genuine item")
    void absentTagIsNotGenuine() {
        assertFalse(AguaItemBuilder.isTagPresentAndTrue(null));
    }

    @Test
    @DisplayName("A tag explicitly set to false is not a genuine item")
    void falseTagIsNotGenuine() {
        assertFalse(AguaItemBuilder.isTagPresentAndTrue(Boolean.FALSE));
    }

    @Test
    @DisplayName("A tag set to true is a genuine item")
    void trueTagIsGenuine() {
        assertTrue(AguaItemBuilder.isTagPresentAndTrue(Boolean.TRUE));
    }
}
