package org.soutalab.celtium.vulkan;

/**
 * The offer funnel's pre-backend deaths (drawpath audit DP-01): finished immediate batches that died inside
 * {@code WorldVertexBufferUploaderMixin}'s offer method before the backend could accept or refuse them.
 * <p>
 * Three seats, none of which the backend's own refusal account ({@code offerRefusals}) can see, because the
 * batch never reaches {@code submitImmediateBatch}:
 * <ul>
 * <li>{@link #noteSwallowed()} - the offer method's broad catch. Its print budget stopped at three, and the
 * counting stopped with it: the fourth swallowed failure and every one after it left no trace at all.</li>
 * <li>{@link #noteNoData()} - the finished batch carried no readable byte buffer.</li>
 * <li>{@link #noteNoAddress()} - the buffer's memory address read as 0.</li>
 * </ul>
 * Always on and unconditional: a counter that can be switched off, or that stops counting when its print
 * budget runs out, is the defect this census exists to close. Read through {@link #summary()} in the
 * verification summary, beside the backend's refusal account, so "arrived but never decided" and "decided
 * and refused" stay two different numbers.
 */
public final class OfferFunnelCensus {
    private static long swallowed;
    private static long noData;
    private static long noAddress;

    private OfferFunnelCensus() {
    }

    /** One offer swallowed by the capture hook's broad catch, whatever the failure was. */
    public static void noteSwallowed() {
        swallowed++;
    }

    /** One finished batch whose byte buffer could not be read at all. */
    public static void noteNoData() {
        noData++;
    }

    /** One finished batch whose buffer address read as 0. */
    public static void noteNoAddress() {
        noAddress++;
    }

    public static long swallowed() {
        return swallowed;
    }

    public static long noData() {
        return noData;
    }

    public static long noAddress() {
        return noAddress;
    }

    /** {@return the one-line reading for the verification summary}. */
    public static String summary() {
        return "swallowed=" + swallowed + " noData=" + noData + " noAddress=" + noAddress;
    }
}
