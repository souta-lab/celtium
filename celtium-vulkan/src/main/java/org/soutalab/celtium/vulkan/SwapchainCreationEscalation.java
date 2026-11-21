package org.soutalab.celtium.vulkan;

/**
 * The swapchain creation escalation: how many creations in a row the platform has refused, and whether
 * that run has earned the crash the window-renderer path already has.
 * <p>
 * {@code VulkanTerrainGpuBackend.ensureSwapchain()} creates the swapchain lazily at the first present and
 * refuses fail-closed with its reasons when creation is refused - but the present path only counted the
 * skip and returned, and nothing anywhere consumed a failure count, so a surface that refuses forever (no
 * WSI extension, a permanently failing {@code SDL_Vulkan_CreateSurface}, a non-presenting queue family, a
 * {@code vkCreateSwapchainKHR} that never succeeds) presented an endless black window, loading screen
 * included, with the reason sitting in a log nobody was shown. The sibling judgement already exists for
 * the renderer itself: a GL-less window has no fallback, so after a bounded retry window the game stops
 * with a crash report naming the failure instead of staying alive and black. This class is that judgement
 * for the swapchain, kept as state and a decision only, because the backend cannot throw the escalation
 * itself: the swap point's hook wraps the present in a catch that swallows {@code RuntimeException}, so a
 * throw from here would never reach a crash report, and this module is not compiled against Minecraft, so
 * it cannot call {@code displayCrashReport} either. The mixin polls {@code celtium$swapchainCreationFailure}
 * outside that catch, beside the renderer escalation, and owns the report and the throw.
 * <p>
 * Only refused creations are counted: the calls that return before attempting one (no window handle yet, a
 * minimised window with no drawable size) never reach this class, so a game sitting in the menu before its
 * window exists accrues nothing. A successful creation resets the run, so the count is consecutive, not
 * cumulative: an ordinary rebuild (a resize, a present-mode change) drops and recreates inside one
 * {@code ensureSwapchain()} call and lands on the success, and a surface that refuses only while a driver
 * finishes initialising recovers the moment one creation lands. The limit is in attempts because creation
 * is retried unthrottled on every present: the renderer's eight attempts are spaced over seconds, while
 * 120 consecutive refusals here is roughly two seconds of presented frames at 60 Hz - far beyond any
 * transient rebuild, and short enough that a permanently refusing surface stops the game with its reason
 * named instead of a black window for the whole session.
 * <p>
 * Touched only from {@code ensureSwapchain()} and the swap point's poll, both on the client thread.
 */
final class SwapchainCreationEscalation {

    /** Consecutive refused creations that earn the escalation. Pinned by the test, not tuned per run. */
    static final int FAILURE_LIMIT = 120;

    private int consecutiveRefusals;
    private String lastRefusal = "no refusal recorded";

    /** Records one refused creation and the reasons it gave. */
    void recordRefusal(String reason) {
        this.consecutiveRefusals++;
        this.lastRefusal = reason == null || reason.isEmpty() ? "no reason recorded" : reason;
    }

    /** Records a creation that landed: the run of refusals is over, however long it was. */
    void recordSuccess() {
        this.consecutiveRefusals = 0;
    }

    /** Whether the run of refusals has reached the limit. */
    boolean escalationDue() {
        return this.consecutiveRefusals >= FAILURE_LIMIT;
    }

    /** How many creations in a row have been refused since the last success. */
    int consecutiveRefusals() {
        return this.consecutiveRefusals;
    }

    /** The reasons the most recent refused creation gave, for the crash report's line. */
    String lastRefusal() {
        return this.lastRefusal;
    }
}
