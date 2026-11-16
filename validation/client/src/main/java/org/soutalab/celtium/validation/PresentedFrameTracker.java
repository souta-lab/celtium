package org.soutalab.celtium.validation;

/**
 * Tracks the active presented-frame window across world entry, pre-first-present skips,
 * counter restarts, and backend identity changes so the validation fixture compares
 * presented-frame counts within the active window instead of falsely failing when early
 * world frames do not present.
 */
final class PresentedFrameTracker {

    /** Snapshot of the counters at the start of one presented-counter window. */
    static final class WindowSnapshot {
        final int framesStart;
        final int presentedStart;
        final int skipsStart;
        final int attemptsStart;
        final int hookCallsStart;
        final int backendIdentity;

        WindowSnapshot(int framesStart, int presentedStart, int skipsStart,
                int attemptsStart, int hookCallsStart, int backendIdentity) {
            this.framesStart = framesStart;
            this.presentedStart = presentedStart;
            this.skipsStart = skipsStart;
            this.attemptsStart = attemptsStart;
            this.hookCallsStart = hookCallsStart;
            this.backendIdentity = backendIdentity;
        }
    }

    /** Per-frame transition flags reported back to the fixture for logging and JSON fields. */
    static final class FrameObservation {
        final boolean backendChanged;
        final int previousBackendIdentity;
        final int currentBackendIdentity;
        final boolean counterRestarted;
        final int restartFrom;
        final int restartTo;

        FrameObservation(boolean backendChanged, int previousBackendIdentity, int currentBackendIdentity,
                boolean counterRestarted, int restartFrom, int restartTo) {
            this.backendChanged = backendChanged;
            this.previousBackendIdentity = previousBackendIdentity;
            this.currentBackendIdentity = currentBackendIdentity;
            this.counterRestarted = counterRestarted;
            this.restartFrom = restartFrom;
            this.restartTo = restartTo;
        }
    }

    /** Result of comparing a completed dump's presented frame against the world window. */
    static final class VerdictEvaluation {
        final int presentedInWorld;
        final int framesInWorld;
        final int skipsInWindow;
        final int attemptsInWindow;
        final int failedAttemptsInWindow;
        final int expectedPresented;
        final int unadjustedDelta;
        final int delta;
        final boolean passed;
        final boolean usedPreFirstPresentAnchor;
        final boolean restartAfterCapture;
        final WindowSnapshot window;

        VerdictEvaluation(int presentedInWorld, int framesInWorld, int skipsInWindow,
                int attemptsInWindow, int failedAttemptsInWindow, int expectedPresented,
                int unadjustedDelta, int delta, boolean passed, boolean usedPreFirstPresentAnchor,
                boolean restartAfterCapture, WindowSnapshot window) {
            this.presentedInWorld = presentedInWorld;
            this.framesInWorld = framesInWorld;
            this.skipsInWindow = skipsInWindow;
            this.attemptsInWindow = attemptsInWindow;
            this.failedAttemptsInWindow = failedAttemptsInWindow;
            this.expectedPresented = expectedPresented;
            this.unadjustedDelta = unadjustedDelta;
            this.delta = delta;
            this.passed = passed;
            this.usedPreFirstPresentAnchor = usedPreFirstPresentAnchor;
            this.restartAfterCapture = restartAfterCapture;
            this.window = window;
        }
    }

    private boolean initialized;
    private WindowSnapshot currentWindow;
    private WindowSnapshot previousWindow;
    private int lastPresentedSample = -1;
    private int lastBackendIdentity;
    private int lastNonZeroBackendIdentity;
    private int firstBackendIdentity;
    private int firstPresentedWorldFrame = -1;
    private int presentedFramesHighWater;
    private int presentedCounterRestarts;
    private int backendIdentityChanges;
    private int windowFirstPresentedWorldFrame = -1;
    private int windowLastZeroPresentedWorldFrame = -1;
    private int skipsAtWindowLastZero = -1;

    FrameObservation onWorldFrame(int worldFrames, int backendIdentity, int presentedNow,
            int skipsNow, int attemptsNow, int hookCallsNow) {
        if (!this.initialized) {
            this.initialized = true;
            this.firstBackendIdentity = backendIdentity;
            this.lastNonZeroBackendIdentity = backendIdentity;
            // Keep presentedStart=0 on the initial world so an un-reset pre-world counter still trips the check.
            this.currentWindow = new WindowSnapshot(0, 0, nonNegative(skipsNow),
                    nonNegative(attemptsNow), nonNegative(hookCallsNow), backendIdentity);
            this.previousWindow = this.currentWindow;
            this.lastBackendIdentity = backendIdentity;
            this.lastPresentedSample = presentedNow;
            recordWindowPresentedProgress(worldFrames, presentedNow, skipsNow);
            return new FrameObservation(false, 0, backendIdentity, false, -1, -1);
        }

        boolean backendChanged = false;
        int prevBackend = this.lastBackendIdentity;
        boolean counterRestarted = false;
        int restartFrom = -1;
        int restartTo = -1;

        if (backendIdentity != 0 && this.lastBackendIdentity == 0) {
            if (this.firstBackendIdentity == 0) {
                this.firstBackendIdentity = backendIdentity;
            }
            if (this.lastNonZeroBackendIdentity != 0 && backendIdentity != this.lastNonZeroBackendIdentity) {
                this.backendIdentityChanges++;
            }
            openNewWindow(worldFrames - 1, nonNegative(presentedNow), skipsNow, attemptsNow, hookCallsNow,
                    backendIdentity);
            backendChanged = true;
        } else if (this.lastBackendIdentity != 0 && backendIdentity != this.lastBackendIdentity) {
            backendChanged = true;
            if (backendIdentity != 0) {
                this.backendIdentityChanges++;
                openNewWindow(worldFrames, nonNegative(presentedNow), skipsNow, attemptsNow, hookCallsNow,
                        backendIdentity);
            }
        } else if (this.lastPresentedSample >= 0 && presentedNow >= 0 && presentedNow < this.lastPresentedSample) {
            counterRestarted = true;
            restartFrom = this.lastPresentedSample;
            restartTo = presentedNow;
            this.presentedCounterRestarts++;
            openNewWindow(worldFrames, presentedNow, skipsNow, attemptsNow, hookCallsNow, backendIdentity);
        }

        recordWindowPresentedProgress(worldFrames, presentedNow, skipsNow);
        this.lastBackendIdentity = backendIdentity;
        if (backendIdentity != 0) {
            this.lastNonZeroBackendIdentity = backendIdentity;
        }
        this.lastPresentedSample = presentedNow;
        return new FrameObservation(backendChanged, prevBackend, backendIdentity,
                counterRestarted, restartFrom, restartTo);
    }

    private void openNewWindow(int framesStart, int presentedStart, int skipsNow,
            int attemptsNow, int hookCallsNow, int backendIdentity) {
        this.previousWindow = this.currentWindow;
        this.currentWindow = new WindowSnapshot(framesStart, presentedStart, nonNegative(skipsNow),
                nonNegative(attemptsNow), nonNegative(hookCallsNow), backendIdentity);
        this.windowFirstPresentedWorldFrame = -1;
        this.windowLastZeroPresentedWorldFrame = -1;
        this.skipsAtWindowLastZero = -1;
    }

    private void recordWindowPresentedProgress(int worldFrames, int presentedNow, int skipsNow) {
        if (presentedNow > 0) {
            if (this.firstPresentedWorldFrame < 0) {
                this.firstPresentedWorldFrame = worldFrames;
            }
            if (this.windowFirstPresentedWorldFrame < 0) {
                this.windowFirstPresentedWorldFrame = worldFrames;
            }
            if (presentedNow > this.presentedFramesHighWater) {
                this.presentedFramesHighWater = presentedNow;
            }
        } else if (presentedNow == 0 && this.windowFirstPresentedWorldFrame < 0) {
            this.windowLastZeroPresentedWorldFrame = worldFrames;
            this.skipsAtWindowLastZero = nonNegative(skipsNow);
        }
    }

    WindowSnapshot snapshotCurrentWindow() {
        return this.currentWindow;
    }

    WindowSnapshot windowForCapture(int captureWorldFrames) {
        if (this.currentWindow == null) {
            return new WindowSnapshot(0, 0, 0, 0, 0, 0);
        }
        if (this.currentWindow.framesStart > captureWorldFrames && this.previousWindow != null) {
            return this.previousWindow;
        }
        return this.currentWindow;
    }

    VerdictEvaluation evaluate(int captureWorldFrames, int evidencePresentedFrame,
            int presentedFramesAtRequest, int skipsAtRequest, int attemptsAtRequest,
            WindowSnapshot windowAtRequest) {
        boolean restartAfterCapture = this.currentWindow != null && this.currentWindow.framesStart > captureWorldFrames;
        WindowSnapshot window;
        if (windowAtRequest != null && windowAtRequest.framesStart <= captureWorldFrames) {
            window = windowAtRequest;
        } else {
            window = windowForCapture(captureWorldFrames);
        }

        int presentedInWorld = evidencePresentedFrame - window.presentedStart;
        int framesInWorld = captureWorldFrames - window.framesStart;
        int unadjustedDelta = presentedInWorld - framesInWorld;

        int skipsInWindow = (skipsAtRequest >= 0 && window.skipsStart >= 0)
                ? Math.max(0, skipsAtRequest - window.skipsStart)
                : 0;
        int attemptsInWindow = (attemptsAtRequest >= 0 && window.attemptsStart >= 0)
                ? Math.max(0, attemptsAtRequest - window.attemptsStart)
                : -1;
        int presentedUpToRequest = presentedFramesAtRequest >= 0
                ? Math.max(0, presentedFramesAtRequest - window.presentedStart)
                : -1;
        int failedAttemptsInWindow = (attemptsInWindow >= 0 && presentedUpToRequest >= 0)
                ? Math.max(0, attemptsInWindow - presentedUpToRequest)
                : -1;

        int expectedAfterSkips = Math.max(0, framesInWorld - skipsInWindow);
        int deltaAfterSkips = presentedInWorld - expectedAfterSkips;
        if (Math.abs(deltaAfterSkips) <= 1) {
            return new VerdictEvaluation(presentedInWorld, framesInWorld, skipsInWindow, attemptsInWindow,
                    failedAttemptsInWindow, expectedAfterSkips, unadjustedDelta, deltaAfterSkips, true, false,
                    restartAfterCapture, window);
        }

        // If presenting stayed at 0 through early world frames (e.g. modpack world-entry before the
        // swapchain/target began presenting) without failing swapchain attempts, anchor to the last 0 frame.
        boolean zeroWindowEligible = deltaAfterSkips < -1
                && this.windowLastZeroPresentedWorldFrame > window.framesStart + 1
                && (this.windowFirstPresentedWorldFrame < 0
                    || this.windowLastZeroPresentedWorldFrame >= this.windowFirstPresentedWorldFrame - 1)
                && attemptsInWindow >= 0
                && failedAttemptsInWindow <= 1;
        if (zeroWindowEligible) {
            int anchorFrame = Math.min(captureWorldFrames, this.windowLastZeroPresentedWorldFrame);
            int skipsSinceAnchor = (skipsAtRequest >= 0 && this.skipsAtWindowLastZero >= 0)
                    ? Math.max(0, skipsAtRequest - this.skipsAtWindowLastZero)
                    : 0;
            int expectedSinceFirst = Math.max(0, captureWorldFrames - anchorFrame - skipsSinceAnchor);
            int deltaSinceFirst = presentedInWorld - expectedSinceFirst;
            if (Math.abs(deltaSinceFirst) <= 1) {
                return new VerdictEvaluation(presentedInWorld, framesInWorld, skipsInWindow, attemptsInWindow,
                        failedAttemptsInWindow, expectedSinceFirst, unadjustedDelta, deltaSinceFirst, true, true,
                        restartAfterCapture, window);
            }
        }

        return new VerdictEvaluation(presentedInWorld, framesInWorld, skipsInWindow, attemptsInWindow,
                failedAttemptsInWindow, expectedAfterSkips, unadjustedDelta, deltaAfterSkips, false, false,
                restartAfterCapture, window);
    }

    int firstPresentedWorldFrame() {
        return this.firstPresentedWorldFrame;
    }

    int lastZeroPresentedWorldFrame() {
        return this.windowLastZeroPresentedWorldFrame;
    }

    int presentedFramesHighWater() {
        return this.presentedFramesHighWater;
    }

    int presentedCounterRestarts() {
        return this.presentedCounterRestarts;
    }

    int backendIdentityChanges() {
        return this.backendIdentityChanges;
    }

    int firstBackendIdentity() {
        return this.firstBackendIdentity;
    }

    private static int nonNegative(int value) {
        return value < 0 ? -1 : value;
    }
}
