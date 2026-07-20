package org.xpfarm.aguadeflorida.listeners;

import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the hand-resolution rule of the observation listener.
 *
 * <p>Only the slot planning is unit tested. Everything else in the listener touches a live
 * player, and the listener is deliberately non-load-bearing: whether the event fires at all
 * for a custom death-protection item is a runtime question, not one a unit test can settle.</p>
 */
class AguaResurrectListenerTest {

    @Test
    @DisplayName("a reported hand is the only slot inspected")
    void reportedHandIsUsedAlone() {
        assertEquals(List.of(EquipmentSlot.HAND), AguaResurrectListener.slotsToInspect(EquipmentSlot.HAND));
        assertEquals(List.of(EquipmentSlot.OFF_HAND), AguaResurrectListener.slotsToInspect(EquipmentSlot.OFF_HAND));
    }

    @Test
    @DisplayName("a null hand falls back to both hands rather than assuming one")
    void nullHandInspectsBothHands() {
        // EntityResurrectEvent has a one-arg constructor that leaves the hand unset, so
        // getHand() is genuinely nullable and must not be dereferenced or guessed.
        assertEquals(List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND),
            AguaResurrectListener.slotsToInspect(null));
    }

    @Test
    @DisplayName("a non-hand slot is honoured rather than silently widened")
    void nonHandSlotIsHonoured() {
        // The event only ever reports a hand in practice, but the method must not invent
        // extra slots to search: a wider search is a wider chance of a false save message.
        assertEquals(List.of(EquipmentSlot.CHEST), AguaResurrectListener.slotsToInspect(EquipmentSlot.CHEST));
    }

    @Test
    @DisplayName("planned slots are never empty, so a save is never unattributable by default")
    void plannedSlotsAreNeverEmpty() {
        assertFalse(AguaResurrectListener.slotsToInspect(null).isEmpty());
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            assertFalse(AguaResurrectListener.slotsToInspect(slot).isEmpty(), "empty plan for " + slot);
        }
    }

    @Test
    @DisplayName("the default life-saved message carries legacy colour codes")
    void defaultMessageIsLegacyFormatted() {
        // The listener deserializes with LegacyComponentSerializer.legacySection(); a default
        // written with '&' instead of the section sign would print literal codes to players.
        assertTrue(AguaResurrectListener.DEFAULT_LIFE_SAVED.indexOf('§') >= 0);
        assertFalse(AguaResurrectListener.DEFAULT_LIFE_SAVED.contains("&"));
    }
}
