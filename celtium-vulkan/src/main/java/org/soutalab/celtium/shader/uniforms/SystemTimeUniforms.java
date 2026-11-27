/*
 * Derived from Iris / Angelica shader pack interpreter (LGPL-3.0).
 * Adapted natively for Celtium Vulkan 1.3 (no GLSM / Minecraft dependency).
 */
package org.soutalab.celtium.shader.uniforms;

public final class SystemTimeUniforms {
    public static final FrameCounter COUNTER = new FrameCounter();
    public static final Timer TIMER = new Timer();

    private SystemTimeUniforms() {
    }

    public static final class FrameCounter {
        private int count;

        public void beginFrame() {
            count++;
            if (count == 720720) {
                count = 0;
            }
        }

        public void reset() {
            count = 0;
        }

        public int getAsInt() {
            return count;
        }
    }

    public static final class Timer {
        private float frameTimeCounter;
        private float lastFrameTime = 1.0f / 60.0f;
        private long lastStartTimeNs;
        private boolean hasStarted;

        public void beginFrame(long frameStartTimeNs) {
            if (!hasStarted) {
                hasStarted = true;
                lastStartTimeNs = frameStartTimeNs;
                lastFrameTime = 1.0f / 60.0f;
                return;
            }
            long diffNs = frameStartTimeNs - lastStartTimeNs;
            lastStartTimeNs = frameStartTimeNs;
            lastFrameTime = Math.max(0.0f, diffNs * 1.0e-9f);
            frameTimeCounter = (frameTimeCounter + lastFrameTime) % 3600.0f;
        }

        public void reset() {
            frameTimeCounter = 0.0f;
            lastFrameTime = 1.0f / 60.0f;
            hasStarted = false;
        }

        public float getFrameTimeCounter() {
            return frameTimeCounter;
        }

        public float getLastFrameTime() {
            return lastFrameTime;
        }
    }
}
