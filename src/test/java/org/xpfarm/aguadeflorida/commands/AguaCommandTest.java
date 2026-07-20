package org.xpfarm.aguadeflorida.commands;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the stack distribution arithmetic used by /agua give.
 */
class AguaCommandTest {

    @Test
    @DisplayName("A single item produces one stack of one")
    void singleItem() {
        assertEquals(List.of(1), AguaCommand.splitIntoStacks(1, 1));
        assertEquals(List.of(1), AguaCommand.splitIntoStacks(1, 64));
    }

    @Test
    @DisplayName("An amount equal to the max stack size produces one full stack")
    void amountEqualsMaxStack() {
        assertEquals(List.of(16), AguaCommand.splitIntoStacks(16, 16));
    }

    @Test
    @DisplayName("An amount one over the max stack size spills into a second stack")
    void amountOverMaxStack() {
        assertEquals(List.of(16, 1), AguaCommand.splitIntoStacks(17, 16));
    }

    @Test
    @DisplayName("64 of a max-stack-1 item produces 64 single stacks")
    void sixtyFourOfUnstackableItem() {
        List<Integer> stacks = AguaCommand.splitIntoStacks(64, 1);

        assertEquals(64, stacks.size());
        assertTrue(stacks.stream().allMatch(size -> size == 1));
        assertEquals(64, stacks.stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    @DisplayName("64 of a max-stack-64 item produces one full stack")
    void sixtyFourOfStackableItem() {
        assertEquals(List.of(64), AguaCommand.splitIntoStacks(64, 64));
    }

    @Test
    @DisplayName("Zero produces no stacks")
    void zeroAmount() {
        assertTrue(AguaCommand.splitIntoStacks(0, 64).isEmpty());
    }

    @Test
    @DisplayName("A negative amount produces no stacks")
    void negativeAmount() {
        assertTrue(AguaCommand.splitIntoStacks(-5, 64).isEmpty());
    }

    @Test
    @DisplayName("A non-positive max stack size still terminates and loses nothing")
    void degenerateMaxStackSize() {
        assertEquals(List.of(1, 1, 1), AguaCommand.splitIntoStacks(3, 0));
    }

    @Test
    @DisplayName("No item is ever lost: the split always sums back to the requested amount")
    void splitAlwaysConservesTotal() {
        for (int maxStackSize = 1; maxStackSize <= 64; maxStackSize++) {
            for (int amount = 1; amount <= 64; amount++) {
                List<Integer> stacks = AguaCommand.splitIntoStacks(amount, maxStackSize);
                int total = stacks.stream().mapToInt(Integer::intValue).sum();

                int perStack = maxStackSize;
                assertEquals(amount, total, "amount=" + amount + " maxStackSize=" + maxStackSize);
                assertTrue(stacks.stream().allMatch(size -> size >= 1 && size <= perStack));
            }
        }
    }
}
