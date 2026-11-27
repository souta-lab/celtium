package org.soutalab.celtium.glcompat.states;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.GLStateManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.ARBImaging;
import org.lwjgl.opengl.GL11;

public class MatrixMode implements ISettableState<MatrixMode> {
    private static final Logger LOGGER = LogManager.getLogger(MatrixMode.class);

    protected int mode = GL11.GL_MODELVIEW;

    public void setMode(int mode) {
        if(mode != GL11.GL_MODELVIEW && mode != GL11.GL_PROJECTION && mode != GL11.GL_TEXTURE && mode != GL11.GL_COLOR) {
            // Invalid mode, do nothing on the cache
            return;
        }

        final boolean bypass = GLStateManager.shouldBypassCache();
        if (bypass || this.mode != mode) {
            if (!bypass) {
                this.mode = mode;
            }
        }
    }

    public int getMatrix() {
        return switch(mode) {
            case GL11.GL_MODELVIEW -> GL11.GL_MODELVIEW_MATRIX;
            case GL11.GL_PROJECTION -> GL11.GL_PROJECTION_MATRIX;
            case GL11.GL_TEXTURE -> GL11.GL_TEXTURE_MATRIX;
            case GL11.GL_COLOR -> ARBImaging.GL_COLOR_MATRIX;
            default -> {
                LOGGER.warn("Unknown matrix mode {}, falling back to GL_MODELVIEW_MATRIX", mode);
                yield GL11.GL_MODELVIEW_MATRIX;
            }
        };
    }

    @Override
    public MatrixMode set(MatrixMode state) {
        this.mode = state.mode;
        return this;
    }

    @Override
    public boolean sameAs(Object state) {
        if(this == state) return true;
        if(!(state instanceof MatrixMode matrixMode)) return false;
        return mode == matrixMode.mode;
    }

    @Override
    public MatrixMode copy() {
        return new MatrixMode().set(this);
    }

    // -- explicit accessors (delomboked during the S4 native conversion) --
    public static Logger getLOGGER() { return LOGGER; }
    public int getMode() { return mode; }
}
