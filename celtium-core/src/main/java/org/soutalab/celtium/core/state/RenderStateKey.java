package org.soutalab.celtium.core.state;

/**
 * The fixed-function render state the non-terrain lane tracks, packed into one 32-bit key (design
 * V040-FINAL-PLAN section 2.3, interface 3 contract): the register's readers compare and hash this
 * key instead of re-reading a dozen GL state variables, and the pipeline cache keys off it.
 * <p>
 * Bit layout:
 * <ul>
 *   <li>bit 0: blend enabled; bit 1: depth test; bit 2: depth write; bit 3: stencil test;
 *       bit 4: scissor test; bit 5: colour logic op</li>
 *   <li>bits 6..7: cull mode (0 = off, 1 = back, 2 = front, 3 = front and back)</li>
 *   <li>bits 8..9: polygon mode (0 = fill, 1 = line, 2 = point)</li>
 *   <li>bits 10..13: colour write mask (R, G, B, A)</li>
 *   <li>bits 14..17: active texture unit (0..15)</li>
 *   <li>bits 18..31: spare, must be zero</li>
 * </ul>
 */
public record RenderStateKey(int value) {

    public static final int CULL_OFF = 0;
    public static final int CULL_BACK = 1;
    public static final int CULL_FRONT = 2;
    public static final int CULL_FRONT_AND_BACK = 3;

    public static final int POLYGON_FILL = 0;
    public static final int POLYGON_LINE = 1;
    public static final int POLYGON_POINT = 2;

    /** The shipping default state: depth test+write on, back-face culling, fill, all writes, unit 0. */
    public static final RenderStateKey DEFAULT = new Builder().build();

    public RenderStateKey {
        if ((value & 0xFFFC0000) != 0) {
            throw new IllegalArgumentException("spare bits 18..31 must be zero: " + Integer.toHexString(value));
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean blendEnabled() {
        return bit(0);
    }

    public boolean depthTestEnabled() {
        return bit(1);
    }

    public boolean depthWriteEnabled() {
        return bit(2);
    }

    public boolean stencilTestEnabled() {
        return bit(3);
    }

    public boolean scissorTestEnabled() {
        return bit(4);
    }

    public boolean logicOpEnabled() {
        return bit(5);
    }

    public int cullMode() {
        return (value >>> 6) & 0x3;
    }

    public int polygonMode() {
        return (value >>> 8) & 0x3;
    }

    public int colorWriteMask() {
        return (value >>> 10) & 0xF;
    }

    public int activeTextureUnit() {
        return (value >>> 14) & 0xF;
    }

    private boolean bit(int index) {
        return ((value >>> index) & 0x1) != 0;
    }

    /** A builder seeded with the {@link #DEFAULT} state, so callers name only what they change. */
    public static final class Builder {
        private boolean blendEnabled;
        private boolean depthTestEnabled = true;
        private boolean depthWriteEnabled = true;
        private boolean stencilTestEnabled;
        private boolean scissorTestEnabled;
        private boolean logicOpEnabled;
        private int cullMode = CULL_BACK;
        private int polygonMode = POLYGON_FILL;
        private int colorWriteMask = 0xF;
        private int activeTextureUnit;

        public Builder blendEnabled(boolean enabled) {
            this.blendEnabled = enabled;
            return this;
        }

        public Builder depthTestEnabled(boolean enabled) {
            this.depthTestEnabled = enabled;
            return this;
        }

        public Builder depthWriteEnabled(boolean enabled) {
            this.depthWriteEnabled = enabled;
            return this;
        }

        public Builder stencilTestEnabled(boolean enabled) {
            this.stencilTestEnabled = enabled;
            return this;
        }

        public Builder scissorTestEnabled(boolean enabled) {
            this.scissorTestEnabled = enabled;
            return this;
        }

        public Builder logicOpEnabled(boolean enabled) {
            this.logicOpEnabled = enabled;
            return this;
        }

        public Builder cullMode(int mode) {
            if (mode < 0 || mode > 3) {
                throw new IllegalArgumentException("cullMode must be 0..3");
            }
            this.cullMode = mode;
            return this;
        }

        public Builder polygonMode(int mode) {
            if (mode < 0 || mode > 2) {
                throw new IllegalArgumentException("polygonMode must be 0..2");
            }
            this.polygonMode = mode;
            return this;
        }

        public Builder colorWriteMask(int mask) {
            if (mask < 0 || mask > 0xF) {
                throw new IllegalArgumentException("colorWriteMask must be an RGBA bit set (0..15)");
            }
            this.colorWriteMask = mask;
            return this;
        }

        public Builder activeTextureUnit(int unit) {
            if (unit < 0 || unit > 15) {
                throw new IllegalArgumentException("activeTextureUnit must be 0..15");
            }
            this.activeTextureUnit = unit;
            return this;
        }

        public RenderStateKey build() {
            int value = 0;
            value |= blendEnabled ? 1 : 0;
            value |= (depthTestEnabled ? 1 : 0) << 1;
            value |= (depthWriteEnabled ? 1 : 0) << 2;
            value |= (stencilTestEnabled ? 1 : 0) << 3;
            value |= (scissorTestEnabled ? 1 : 0) << 4;
            value |= (logicOpEnabled ? 1 : 0) << 5;
            value |= cullMode << 6;
            value |= polygonMode << 8;
            value |= colorWriteMask << 10;
            value |= activeTextureUnit << 14;
            return new RenderStateKey(value);
        }
    }
}
