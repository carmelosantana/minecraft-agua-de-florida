package org.xpfarm.aguadeflorida.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link PlayerLookup}'s pure functions -- the candidate-name expansion and the
 * failure message -- with no Bukkit types and no running server.
 *
 * <p>{@code resolve} and {@code resolveAllowingPartial} are deliberately not tested here:
 * they call {@code Bukkit.getPlayerExact} / {@code Bukkit.getOnlinePlayers}, which need a
 * live server, and there is no MockBukkit dependency in this project. The Floodgate
 * decision those methods make lives entirely in {@code targetNameCandidates}, which is
 * what is pinned below.
 */
class PlayerLookupTest {

    @Nested
    @DisplayName("target resolution")
    class TargetResolution {

        @Test
        @DisplayName("a bare name also tries the Floodgate '.' prefix")
        void bareNameTriesFloodgatePrefix() {
            assertEquals(List.of("carm", ".carm"), PlayerLookup.targetNameCandidates("carm"));
        }

        @Test
        @DisplayName("an already-prefixed name is not prefixed twice")
        void prefixedNameIsNotDoubled() {
            assertEquals(List.of(".acarm"), PlayerLookup.targetNameCandidates(".acarm"));
        }

        @Test
        @DisplayName("surrounding whitespace is trimmed")
        void whitespaceIsTrimmed() {
            assertEquals(List.of("carm", ".carm"), PlayerLookup.targetNameCandidates("  carm  "));
        }

        @Test
        @DisplayName("null and blank yield no candidates")
        void nullAndBlankYieldNothing() {
            assertTrue(PlayerLookup.targetNameCandidates(null).isEmpty());
            assertTrue(PlayerLookup.targetNameCandidates("   ").isEmpty());
        }

        @Test
        @DisplayName("the failure message lists who is actually online")
        void failureMessageListsOnlinePlayers() {
            String message = PlayerLookup.noSuchPlayerMessage("carm", List.of(".acarm", "Steve"));
            assertTrue(message.contains("carm"), message);
            assertTrue(message.contains(".acarm"), message);
            assertTrue(message.contains("Steve"), message);
        }

        @Test
        @DisplayName("the failure message says so plainly when nobody is online")
        void failureMessageWhenNobodyOnline() {
            String message = PlayerLookup.noSuchPlayerMessage("carm", List.of());
            assertTrue(message.contains("carm"), message);
            assertTrue(message.toLowerCase(Locale.ROOT).contains("no players"), message);
        }
    }
}
