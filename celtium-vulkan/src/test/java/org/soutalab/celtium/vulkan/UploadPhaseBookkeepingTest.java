package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the canonical reserve figure's derivation (item 363 caveat 2, ITEM363-AUDIT §2.6):
 * {@link VulkanContext#celtium$reserveBookMicrosPerKCopy} subtracts the two non-booking parts of the
 * reserve bracket - the one-time ring creation and the ring-pressure waits - and normalises by the exact
 * reservation count, so the figure is microseconds of pure booking cost per 1,000 copies.
 */
final class UploadPhaseBookkeepingTest {

    @Test
    void subtractsInitAndWaitThenNormalisesPerThousandCopies() {
        // 10 ms of bracket, of which 1 ms ring creation and 3 ms waits, over 2,000 reservations:
        // 6 ms of booking over 2,000 copies = 3,000 µs per 1,000 copies.
        assertEquals(3_000L, VulkanContext.celtium$reserveBookMicrosPerKCopy(
                10_000_000L, 1_000_000L, 3_000_000L, 2_000L));
    }

    @Test
    void bracketThatIsAllInitAndWaitBooksZero() {
        assertEquals(0L, VulkanContext.celtium$reserveBookMicrosPerKCopy(
                4_000_000L, 1_000_000L, 3_000_000L, 500L));
    }

    @Test
    void noReservationsYetReadsZeroInsteadOfDividingByZero() {
        assertEquals(0L, VulkanContext.celtium$reserveBookMicrosPerKCopy(
                0L, 0L, 0L, 0L));
    }

    @Test
    void rawCumulativeFigureIsNotTheCanonicalOne() {
        // The 331 shape: a shorter run with heavy ring pressure reads a larger raw reserve than a longer
        // calm run, while the booking cost per copy is the other way round.
        long pressured = VulkanContext.celtium$reserveBookMicrosPerKCopy(
                14_604_000L, 200_000L, 12_404_000L, 1_000L);
        long calm = VulkanContext.celtium$reserveBookMicrosPerKCopy(
                4_996_000L, 200_000L, 796_000L, 1_000L);
        assertEquals(2_000L, pressured);
        assertEquals(4_000L, calm);
    }
}
