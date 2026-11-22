package org.soutalab.celtium.vulkan.gl;

/**
 * The glDrawArrays / glDrawElements census, extracted from {@code CeltiumGlStub} (giant-split seam 5,
 * QUEUE 653). Behaviour is unchanged: this class owns the counters, the refusal records and the two
 * summary lines; {@code CeltiumGlStub} keeps the public doors ({@code drawArraySummary()} /
 * {@code drawElementSummary()}) and passes the client-array decode counters it still owns into
 * {@link #drawArraySummary(long, long, long, long, int, String)}.
 */
final class GlDrawCensus {

// ---- the glDrawArrays census ---------------------------------------------------------------------------------
//
// glDrawArrays left the no-op census because it is now drawn, and that is a claim the run has to be able to
// check: "no longer in the no-op list" is also what a draw that was refused everywhere would look like. The
// counters below separate the three outcomes - drawn, empty, refused with a named reason - and the accepted
// count is the one that says the geometry reached the frame. Read them together; none of them alone is evidence.

/** Arrivals at {@code glDrawArrays} since the process started. */
static final java.util.concurrent.atomic.AtomicLong DRAWARRAY_CALLS =
        new java.util.concurrent.atomic.AtomicLong();
/** Draws assembled from the mirrored array buffer and handed to the immediate path (accepted by it). */
static final java.util.concurrent.atomic.AtomicLong DRAWARRAY_DRAWN =
        new java.util.concurrent.atomic.AtomicLong();
/** Draws with no vertices at all: legitimate, and nothing is dropped because there was nothing to draw. */
static final java.util.concurrent.atomic.AtomicLong DRAWARRAY_EMPTY =
        new java.util.concurrent.atomic.AtomicLong();
/** Draws this class would not decode, or the backend would not take. Each one names its reason below. */
static final java.util.concurrent.atomic.AtomicLong DRAWARRAY_REFUSED =
        new java.util.concurrent.atomic.AtomicLong();
/** Vertices in the draws that were handed over, so "drawn" is a quantity and not only a call count. */
static final java.util.concurrent.atomic.AtomicLong DRAWARRAY_VERTICES =
        new java.util.concurrent.atomic.AtomicLong();
/** Refusals by reason, bounded by the number of distinct reasons this class can produce. */
static final java.util.Map<String, java.util.concurrent.atomic.AtomicLong> DRAWARRAY_REFUSALS =
        new java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong>();
/** The first accepted draw, in one line: the evidence that the layout decoded as position-only really did. */
static volatile String drawArrayFirst = "none";

/** Accepted draws whose group colour has red and green at exactly zero (counted by the stub's
 * accepted-colour note in the client-array area, reported here as part of this census). */
static final java.util.concurrent.atomic.AtomicLong DRAWARRAY_PURE_BLUE =
        new java.util.concurrent.atomic.AtomicLong();

/** glBegin/glEnd arrivals, and the distinct (mode, site) pairs already named, bounded to eight. */
static final java.util.concurrent.atomic.AtomicLong BEGIN_END_CALLS =
        new java.util.concurrent.atomic.AtomicLong();
static final java.util.Set<String> IMMEDIATE_SITES =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

/**
 * Names the site that opens a legacy immediate-mode run, once per (mode, site).
 * <p>
 * Bounded to eight lines and deduplicated, so a menu frame that opens thousands of runs adds eight lines rather
 * than thousands. The point is attribution, not volume: the loading screen's three {@code glBegin(GL_QUADS)}
 * sites in Forge's {@code SplashProgress} have to be visible as callers before the recording and submit steps
 * can be judged - the no-op census's {@code glBegin=38115} says how many, not who.
 */
static void celtium$noteImmediateBegin(int mode) {
    BEGIN_END_CALLS.incrementAndGet();
    if (IMMEDIATE_SITES.size() >= 8) {
        return;
    }
    String site = mode + "@" + CeltiumGlStub.celtium$callSite(6);
    if (IMMEDIATE_SITES.add(site)) {
        System.out.println("[Celtium] CELTIUM_IMMEDIATE_BEGIN mode=" + mode + "(" + immediateModeName(mode)
                + ") calls=" + BEGIN_END_CALLS.get() + " site=[" + CeltiumGlStub.celtium$callSite(6) + "]");
    }
}

/** {@return the GL primitive mode's name}, so the census line is readable without the enum. */
static String immediateModeName(int mode) {
    switch (mode) {
        case 0x0000: return "GL_POINTS";
        case 0x0001: return "GL_LINES";
        case 0x0003: return "GL_LINE_STRIP";
        case 0x0004: return "GL_TRIANGLES";
        case 0x0005: return "GL_TRIANGLE_STRIP";
        case 0x0006: return "GL_TRIANGLE_FAN";
        case 0x0007: return "GL_QUADS";
        case 0x0008: return "GL_QUAD_STRIP";
        case 0x0009: return "GL_POLYGON";
        default: return "mode" + mode;
    }
}

static void celtium$noteDrawArrayRefusal(String reason) {
    DRAWARRAY_REFUSED.incrementAndGet();
    java.util.concurrent.atomic.AtomicLong counter = DRAWARRAY_REFUSALS.get(reason);
    if (counter == null) {
        counter = new java.util.concurrent.atomic.AtomicLong();
        java.util.concurrent.atomic.AtomicLong existing = DRAWARRAY_REFUSALS.putIfAbsent(reason, counter);
        if (existing != null) {
            counter = existing;
        }
    }
    counter.incrementAndGet();
}

/**
 * Prints the census line at powers of two of the arrival count, in addition to the line at exit.
 * <p>
 * The exit line is the complete one and the one to read. This exists because a run can end without reaching it -
 * the process is torn down, or the fixture exits - and "the counter has no value" was the shape of a previous
 * mistake here. Powers of two bound the output to about twenty lines for any run length, and put the first line
 * in the log after the very first draw rather than after the last.
 */
static void celtium$reportDrawArrays(long clientPointerAccepted, long ownedClientBufferDecoded,
        long pointerOnlyDecoded, long ownedClientBufferMismatch, int readableMappingRanges,
        String readableMappingFailure) {
    long calls = DRAWARRAY_CALLS.get();
    if (!org.soutalab.celtium.vulkan.VulkanDiagnostics.enabled()) {
        // The powers-of-two lines are the repeating half of this instrument; the exit line below is not gated, so
        // a support log still carries the census once. See VulkanDiagnostics for why the gate exists.
        return;
    }
    if (calls > 0L && (calls & (calls - 1L)) == 0L) {
        System.out.println("[Celtium] CELTIUM_DRAWARRAYS " + drawArraySummary(clientPointerAccepted,
                ownedClientBufferDecoded, pointerOnlyDecoded, ownedClientBufferMismatch,
                readableMappingRanges, readableMappingFailure));
    }
}

/**
 * {@return the {@code glDrawArrays} census as one line: arrivals, drawn, empty, refused and why}.
 * <p>
 * The refusals are listed with their reasons rather than counted, because the reasons are the point: a draw this
 * class does not decode is a named gap, and a bare number would leave the next reader to guess which layout or
 * which mode it was. Sorted, so two runs of the same scene compare line by line.
 */
static String drawArraySummary(long clientPointerAccepted, long ownedClientBufferDecoded,
        long pointerOnlyDecoded, long ownedClientBufferMismatch, int readableMappingRanges,
        String readableMappingFailure) {
    StringBuilder reasons = new StringBuilder();
    for (java.util.Map.Entry<String, java.util.concurrent.atomic.AtomicLong> entry
            : new java.util.TreeMap<String, java.util.concurrent.atomic.AtomicLong>(DRAWARRAY_REFUSALS).entrySet()) {
        if (reasons.length() > 0) {
            reasons.append("; ");
        }
        reasons.append(entry.getKey()).append('=').append(entry.getValue().get());
    }
    return "glDrawArraysCalls=" + DRAWARRAY_CALLS.get()
            + " drawn=" + DRAWARRAY_DRAWN.get()
            + " vertices=" + DRAWARRAY_VERTICES.get()
            + " empty=" + DRAWARRAY_EMPTY.get()
            + " refused=" + DRAWARRAY_REFUSED.get()
            + " pureBlueGroups=" + DRAWARRAY_PURE_BLUE.get()
            + " clientMemoryDecoded=" + clientPointerAccepted
            + " ownedBufferDecoded=" + ownedClientBufferDecoded
            + " pointerOnlyDecoded=" + pointerOnlyDecoded
            + " ownedBufferMismatch=" + ownedClientBufferMismatch
            + " readableRanges=" + readableMappingRanges
            + " mappingFailure=" + readableMappingFailure
            + " first=[" + drawArrayFirst + "]"
            + " refusedByReason=[" + (reasons.length() == 0 ? "none" : reasons.toString()) + "]";
}

// ---- the glDrawElements census --------------------------------------------------------------------------------
//
// The indexed decoder kept no census at all while the unindexed one did, and that asymmetry is how a whole
// route stayed invisible: Demonica's GLSM streaming renderer draws every GUI batch it reroutes through here,
// its layouts were refused for want of the VAO half of the layout decision, and no counter anywhere moved.
// The counters below separate arrivals, draws handed to the backend, and refusals with their reasons - read
// them together, like the glDrawArrays ones; none of them alone is evidence.

/**
 * Holds the indexed-draw census state, in its own class initializer for the same reason as
 * {@code NoopDrawingNames}: this class's own {@code <clinit>} is at HotSpot's HugeMethodLimit.
 */
static final class DrawElementCensus {
    /** Arrivals at the indexed decoder since the process started. */
    static final java.util.concurrent.atomic.AtomicLong CALLS = new java.util.concurrent.atomic.AtomicLong();
    /** Draws assembled and handed to the immediate path. */
    static final java.util.concurrent.atomic.AtomicLong DRAWN = new java.util.concurrent.atomic.AtomicLong();
    /** Draws this decoder would not serve; each one names its reason below. */
    static final java.util.concurrent.atomic.AtomicLong REFUSED = new java.util.concurrent.atomic.AtomicLong();
    /** Refusals by reason, bounded by the number of distinct reasons this decoder can produce. */
    static final java.util.Map<String, java.util.concurrent.atomic.AtomicLong> REFUSALS =
            new java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong>();
}

static void celtium$noteDrawElementRefusal(String reason) {
    DrawElementCensus.REFUSED.incrementAndGet();
    java.util.concurrent.atomic.AtomicLong counter = DrawElementCensus.REFUSALS.get(reason);
    if (counter == null) {
        counter = new java.util.concurrent.atomic.AtomicLong();
        java.util.concurrent.atomic.AtomicLong existing = DrawElementCensus.REFUSALS.putIfAbsent(reason, counter);
        if (existing != null) {
            counter = existing;
        }
    }
    counter.incrementAndGet();
}

/** Prints the indexed census line at powers of two of the arrival count, like the unindexed one. */
static void celtium$reportDrawElements() {
    long calls = DrawElementCensus.CALLS.get();
    if (!org.soutalab.celtium.vulkan.VulkanDiagnostics.enabled()) {
        // The powers-of-two lines are the repeating half of this instrument; the exit line is not gated.
        return;
    }
    if (calls > 0L && (calls & (calls - 1L)) == 0L) {
        System.out.println("[Celtium] CELTIUM_DRAWELEMENTS " + drawElementSummary());
    }
}

/** {@return the indexed census as one line: arrivals, drawn, refused and why}. */
static String drawElementSummary() {
    StringBuilder reasons = new StringBuilder();
    for (java.util.Map.Entry<String, java.util.concurrent.atomic.AtomicLong> entry
            : new java.util.TreeMap<String, java.util.concurrent.atomic.AtomicLong>(
                    DrawElementCensus.REFUSALS).entrySet()) {
        if (reasons.length() > 0) {
            reasons.append("; ");
        }
        reasons.append(entry.getKey()).append('=').append(entry.getValue().get());
    }
    return "glDrawElementsCalls=" + DrawElementCensus.CALLS.get()
            + " drawn=" + DrawElementCensus.DRAWN.get()
            + " refused=" + DrawElementCensus.REFUSED.get()
            + " refusedByReason=[" + (reasons.length() == 0 ? "none" : reasons.toString()) + "]";
}
}
