package org.soutalab.celtium.glcompat.dsa;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.soutalab.celtium.glcompat.backend.DirectGlExecutor.EXECUTOR;

public class DSAEXT extends DSACore {
    @Override
    public void textureImage2D(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        EXECUTOR.textureImage2DEXT(texture, target, level, internalformat, width, height, border, format, type, pixels);
    }

    @Override
    public void textureImage2D(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, IntBuffer pixels) {
        EXECUTOR.textureImage2DEXT(texture, target, level, internalformat, width, height, border, format, type, pixels);
    }
}
