package org.xpfarm.aguadeflorida.utils;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the parts of AguaItemBuilder that do not need a live server.
 *
 * Building the item itself cannot be unit tested: new ItemStack(...) goes through
 * Bukkit.getItemFactory(), and every ConsumeEffect factory resolves through a
 * server-provided bridge. So the effect list is planned as plain values here and
 * translated to Paper types in a separate step, letting the ordering and contents
 * be asserted without a server. The component actually landing on the item is
 * verified at runtime through verifyDeathProtectionApplied().
 */
class AguaItemBuilderTest {

    /**
     * A stand-in effect list of the requested size.
     *
     * PotionEffect needs the Bukkit registries to construct a PotionEffectType, so the
     * elements are null. The planning layer only ever copies the list, never reads an
     * element, which is exactly the boundary these tests are pinning down.
     */
    private static List<PotionEffect> effectListOfSize(int size) {
        return Collections.nCopies(size, (PotionEffect) null);
    }

    // --- Persistent data tag ---

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

    // --- Material hard break ---

    @Test
    @DisplayName("The item material is POTION, so right-click has no vanilla behaviour to gate")
    void itemMaterialIsPotion() {
        assertEquals(Material.POTION, AguaItemBuilder.ITEM_MATERIAL);
    }

    @Test
    @DisplayName("A tagged POTION is recognised")
    void taggedPotionIsGenuine() {
        assertTrue(AguaItemBuilder.isGenuineItem(Material.POTION, Boolean.TRUE));
    }

    @Test
    @DisplayName("A legacy tagged WATER_BUCKET is no longer recognised")
    void taggedWaterBucketIsNotGenuine() {
        // 2.0.0 is a deliberate hard break: 1.x items keep their tag but stop matching,
        // and no conversion code ships. Acceptance check 18.
        assertFalse(AguaItemBuilder.isGenuineItem(Material.WATER_BUCKET, Boolean.TRUE));
    }

    @Test
    @DisplayName("An untagged POTION is not recognised")
    void untaggedPotionIsNotGenuine() {
        assertFalse(AguaItemBuilder.isGenuineItem(Material.POTION, null));
    }

    @Test
    @DisplayName("A POTION tagged explicitly false is not recognised")
    void falseTaggedPotionIsNotGenuine() {
        assertFalse(AguaItemBuilder.isGenuineItem(Material.POTION, Boolean.FALSE));
    }

    @Test
    @DisplayName("A null material is not recognised")
    void nullMaterialIsNotGenuine() {
        assertFalse(AguaItemBuilder.isGenuineItem(null, Boolean.TRUE));
    }

    // --- Death protection effect plan ---

    @Test
    @DisplayName("The plan opens with the glass-break sound")
    void planStartsWithGlassBreakSound() {
        List<AguaItemBuilder.DeathEffect> planned = AguaItemBuilder.planDeathEffects(effectListOfSize(3));

        AguaItemBuilder.DeathEffect.PlaySound sound =
            assertInstanceOf(AguaItemBuilder.DeathEffect.PlaySound.class, planned.get(0));
        assertEquals("minecraft", sound.sound().namespace());
        assertEquals("block.glass.break", sound.sound().value());
    }

    @Test
    @DisplayName("Configured effects are declared once, in full, with certainty")
    void planCarriesConfiguredEffects() {
        List<PotionEffect> configured = effectListOfSize(3);

        List<AguaItemBuilder.DeathEffect> planned = AguaItemBuilder.planDeathEffects(configured);

        AguaItemBuilder.DeathEffect.ApplyStatusEffects apply =
            assertInstanceOf(AguaItemBuilder.DeathEffect.ApplyStatusEffects.class, planned.get(1));
        assertEquals(configured, apply.effects());
        assertEquals(1.0f, apply.probability());
    }

    @Test
    @DisplayName("The plan always ends by wiping existing effects, like a vanilla totem")
    void planEndsByClearingEffects() {
        List<AguaItemBuilder.DeathEffect> planned = AguaItemBuilder.planDeathEffects(effectListOfSize(3));

        assertEquals(3, planned.size());
        assertInstanceOf(AguaItemBuilder.DeathEffect.ClearAllStatusEffects.class, planned.get(2));
    }

    @Test
    @DisplayName("An empty effect config declares no apply-effects entry at all")
    void emptyEffectsAreNotDeclared() {
        List<AguaItemBuilder.DeathEffect> planned = AguaItemBuilder.planDeathEffects(List.of());

        assertEquals(2, planned.size());
        assertInstanceOf(AguaItemBuilder.DeathEffect.PlaySound.class, planned.get(0));
        assertInstanceOf(AguaItemBuilder.DeathEffect.ClearAllStatusEffects.class, planned.get(1));
    }

    @Test
    @DisplayName("A null effect config still yields the sound and the effect wipe")
    void nullEffectsStillPlanSoundAndClear() {
        List<AguaItemBuilder.DeathEffect> planned = AguaItemBuilder.planDeathEffects(null);

        assertEquals(2, planned.size());
        assertInstanceOf(AguaItemBuilder.DeathEffect.PlaySound.class, planned.get(0));
        assertInstanceOf(AguaItemBuilder.DeathEffect.ClearAllStatusEffects.class, planned.get(1));
    }

    @Test
    @DisplayName("The planned effects are a snapshot, so a later config reload cannot mutate a built item")
    void plannedEffectsAreSnapshotted() {
        List<PotionEffect> configured = new ArrayList<>(effectListOfSize(2));

        List<AguaItemBuilder.DeathEffect> planned = AguaItemBuilder.planDeathEffects(configured);
        configured.clear();

        AguaItemBuilder.DeathEffect.ApplyStatusEffects apply =
            assertInstanceOf(AguaItemBuilder.DeathEffect.ApplyStatusEffects.class, planned.get(1));
        assertEquals(2, apply.effects().size());
    }

    @Test
    @DisplayName("The planned effect list is immutable to callers")
    void plannedEffectListIsImmutable() {
        AguaItemBuilder.DeathEffect.ApplyStatusEffects apply =
            assertInstanceOf(AguaItemBuilder.DeathEffect.ApplyStatusEffects.class,
                AguaItemBuilder.planDeathEffects(effectListOfSize(1)).get(1));

        assertThrows(UnsupportedOperationException.class, () -> apply.effects().add(null));
    }

    // --- Potion colour ---

    @Test
    @DisplayName("A configured colour is used as given")
    void configuredColorIsUsed() {
        Color configured = Color.fromRGB(0x123456);

        assertSame(configured, AguaItemBuilder.resolveColor(configured));
    }

    @Test
    @DisplayName("A null colour falls back to the documented default rather than reaching the API")
    void nullColorFallsBackToDefault() {
        assertEquals(ConfigManager.defaultItemColor(), AguaItemBuilder.resolveColor(null));
    }

    @Test
    @DisplayName("The documented default tint is #1E90FF")
    void defaultTintIsDodgerBlue() {
        Color resolved = AguaItemBuilder.resolveColor(null);

        assertEquals(0x1E, resolved.getRed());
        assertEquals(0x90, resolved.getGreen());
        assertEquals(0xFF, resolved.getBlue());
    }
}
