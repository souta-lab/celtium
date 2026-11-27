package org.soutalab.celtium.glcompat;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.GLContextState;
import org.soutalab.celtium.glcompat.stacks.BooleanStateStack;
import org.soutalab.celtium.glcompat.stacks.CowDispatch;
import org.soutalab.celtium.glcompat.stacks.CowStateStack;
import org.soutalab.celtium.glcompat.stacks.RetainedState;
import java.util.List;
import org.lwjgl.opengl.GL15;
import org.soutalab.celtium.glcompat.DisplayListManager.RecordMode;
import org.soutalab.celtium.glcompat.ffp.ShaderManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import static org.soutalab.celtium.glcompat.GLStateManager.CLIENT_ATTRIB_STACK_DEPTH;
import static org.soutalab.celtium.glcompat.GLStateManager.MAX_ATTRIB_STACK_DEPTH;
import static org.soutalab.celtium.glcompat.GLStateManager.rejectExitCapture;
import static org.soutalab.celtium.glcompat.GLStateManager.ctx;
import static org.soutalab.celtium.glcompat.GLStateManager.restorePixelStore;
import static org.soutalab.celtium.glcompat.GLStateManager.warnOnce;
import static org.soutalab.celtium.glcompat.GLStateManager.RETAINED_SLOT;
import static org.soutalab.celtium.glcompat.GLStateManager.applyHintState;
import static org.soutalab.celtium.glcompat.GLStateManager.applyPointState;
import static org.soutalab.celtium.glcompat.GLStateManager.applyPolygonOffset;
import static org.soutalab.celtium.glcompat.GLStateManager.applyRetainedChanges;
import static org.soutalab.celtium.glcompat.GLStateManager.glUseProgram;
import static org.soutalab.celtium.glcompat.GLStateManager.issueBlendFunc;
import static org.soutalab.celtium.glcompat.GLStateManager.lineWidthMax;
import static org.soutalab.celtium.glcompat.GLStateManager.lineWidthMin;
import static org.soutalab.celtium.glcompat.GLStateManager.programLifetimeGeneration;
import static org.soutalab.celtium.glcompat.backend.DirectGlExecutor.EXECUTOR;

public final class GLSMAttribStack {

    private GLSMAttribStack() {}

    public static void glPushClientAttrib(int mask) {
        rejectExitCapture();
        final int slot = ctx().clientAttribStackPointer;
        if (slot >= CLIENT_ATTRIB_STACK_DEPTH) {
            warnOnce("pushclientattrib-overflow", "glPushClientAttrib called with a full client attribute stack ({} deep); ignoring", CLIENT_ATTRIB_STACK_DEPTH);
            return;
        }
        ctx().vaos.saveClientArrays(slot, mask, ctx().boundVAO, ctx().boundVBO);
        ctx().clientAttribSavedTextureUnit[slot] = ctx().clientActiveTextureUnit;
        ctx().clientAttribSavedVertexFlags[slot] = ShaderManager.getInstance().getCurrentVertexFlags();
        if ((mask & GL11.GL_CLIENT_PIXEL_STORE_BIT) != 0) {
            ctx().clientAttribSavedPixelUnpack[slot] = ctx().pixelUnpackState;
            ctx().clientAttribSavedPixelPack[slot] = ctx().pixelPackState;
        }
        ctx().clientAttribStackPointer = slot + 1;
    }

    public static void glPopClientAttrib() {
        rejectExitCapture();
        if (ctx().clientAttribStackPointer == 0) {
            warnOnce("popclientattrib-underflow", "glPopClientAttrib called with an empty client attribute stack; ignoring");
            return;
        }
        final int slot = --ctx().clientAttribStackPointer;
        final int mask = ctx().vaos.savedClientAttribMask(slot);
        if ((mask & GL11.GL_CLIENT_PIXEL_STORE_BIT) != 0) {
            restorePixelStore(ctx().clientAttribSavedPixelUnpack[slot], ctx().clientAttribSavedPixelPack[slot]);
            ctx().clientAttribSavedPixelUnpack[slot] = null;
            ctx().clientAttribSavedPixelPack[slot] = null;
        }
        if ((mask & GL11.GL_CLIENT_VERTEX_ARRAY_BIT) == 0) return;
        ctx().vaos.restoreClientArrays(slot);
        ctx().clientActiveTextureUnit = ctx().clientAttribSavedTextureUnit[slot];
        ShaderManager.getInstance().setCurrentVertexFlags(ctx().clientAttribSavedVertexFlags[slot]);
    }

    public static void glPushAttrib(int mask) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordPushAttrib(mask);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        GLDebug.pushGroup("pushState");
        GLStateManager.pushState(mask);
    }

    public static void glPopAttrib() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordPopAttrib();
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        GLDebug.popGroup();
        ctx().poppingAttributes = true;
        GLDebug.pushGroup("popState");
        try {
            popState();
        } finally {
            GLDebug.popGroup();
            ctx().poppingAttributes = false;
        }
    }

    public static void glPushAttrib() {
        glPushAttrib(GL11.GL_ENABLE_BIT);
    }

    public static void registerModifiedState(int id) {
            final GLContextState glCtx = ctx();
            if (glCtx.attribDepth > 0) {
                final int depth = glCtx.attribDepth - 1;
                glCtx.modifiedIds[depth][glCtx.modifiedCount[depth]++] = id;
            }
        }

    public static void registerModifiedState(GLContextState c, int id) {
        if (c.attribDepth > 0) {
            final int depth = c.attribDepth - 1;
            c.modifiedIds[depth][c.modifiedCount[depth]++] = id;
        }
    }

    public static int pushState(StateSet set) {
        return GLStateManager.pushState(set);
    }

    public static void pushState(int mask) {
        GLStateManager.pushState(mask);
    }

    public static void popStateTo(int depth) {
        GLStateManager.popStateTo(depth);
    }

    public static void popState() {
        GLStateManager.popState();
    }

    public static void retainModifiedState(int depth, RetainedState out) {
            final GLContextState glCtx = ctx();
            if (depth < 0 || depth != glCtx.attribDepth - 1) {
                throw new IllegalStateException("retainModifiedState: depth " + depth + " is not the open push (attrib depth " + glCtx.attribDepth + ")");
            }
            glCtx.retainedOwner = out;
            final int count = glCtx.modifiedCount[depth];
            final int[] ids = glCtx.modifiedIds[depth];
            final CowStateStack<?>[] stackById = glCtx.stackById;
            out.ensureCapacity(count);
            for (int i = 0; i < count; i++) {
                final int id = ids[i];
                stackById[id].captureSlot(RETAINED_SLOT);
                out.ids[i] = id;
            }
            out.count = count;
            out.program = glCtx.activeProgram;
            out.programGen = programLifetimeGeneration;
        }

    public static void applyRetainedState(RetainedState in) {
        rejectExitCapture();
            final GLContextState glCtx = ctx();
            if (glCtx.retainedOwner != in) return;
            final CowStateStack<?>[] stackById = glCtx.stackById;
            final byte[] kindById = glCtx.kindById;
            final int[] ids = in.ids;
            final int count = in.count;
            int first = 0;
            while (first < count && !CowDispatch.slotChanged(kindById[ids[first]], stackById[ids[first]], RETAINED_SLOT)) first++;
            if (first < count) applyRetainedChanges(glCtx, in, first);
            if (in.program != glCtx.activeProgram && (in.program == 0 || in.programGen == programLifetimeGeneration)) {
                glUseProgram(in.program);
            }
        }

    public static void replayStateToBackend() {
        rejectExitCapture();
            final GLContextState glCtx = ctx();

            final List<BooleanStateStack> booleanStates = glCtx.allBooleanStates;
            for (int i = 0; i < booleanStates.size(); i++) {
                booleanStates.get(i).applyToBackend();
            }

            EXECUTOR.bindVertexArray(glCtx.boundVAO != 0 ? glCtx.boundVAO : glCtx.defaultVAO);
            EXECUTOR.bindBuffer(GL15.GL_ARRAY_BUFFER, glCtx.boundVBO);
            EXECUTOR.useProgram(glCtx.activeProgram);
            glCtx.ffp.invalidateProgram();

            EXECUTOR.depthFunc(glCtx.depthState.getFunc());
            EXECUTOR.depthMask(glCtx.depthState.isMaskEnabled());
            EXECUTOR.clearDepth(glCtx.depthState.getClearValue());

            GLStateManager.issueBlendFunc(glCtx.blendState);
            EXECUTOR.blendEquationSeparate(glCtx.blendState.getEquationRgb(), glCtx.blendState.getEquationAlpha());
            EXECUTOR.blendColor(glCtx.blendState.getBlendColorR(), glCtx.blendState.getBlendColorG(), glCtx.blendState.getBlendColorB(), glCtx.blendState.getBlendColorA());
            EXECUTOR.colorMask(glCtx.colorMask.red, glCtx.colorMask.green, glCtx.colorMask.blue, glCtx.colorMask.alpha);
            EXECUTOR.clearColor(glCtx.clearColor.getRed(), glCtx.clearColor.getGreen(), glCtx.clearColor.getBlue(), glCtx.clearColor.getAlpha());
            EXECUTOR.logicOp(glCtx.logicOpMode.getValue());

            EXECUTOR.stencilFuncSeparate(GL11.GL_FRONT, glCtx.stencilState.getFuncFront(), glCtx.stencilState.getRefFront(), glCtx.stencilState.getValueMaskFront());
            EXECUTOR.stencilFuncSeparate(GL11.GL_BACK, glCtx.stencilState.getFuncBack(), glCtx.stencilState.getRefBack(), glCtx.stencilState.getValueMaskBack());
            EXECUTOR.stencilOpSeparate(GL11.GL_FRONT, glCtx.stencilState.getFailOpFront(), glCtx.stencilState.getZFailOpFront(), glCtx.stencilState.getZPassOpFront());
            EXECUTOR.stencilOpSeparate(GL11.GL_BACK, glCtx.stencilState.getFailOpBack(), glCtx.stencilState.getZFailOpBack(), glCtx.stencilState.getZPassOpBack());
            EXECUTOR.stencilMaskSeparate(GL11.GL_FRONT, glCtx.stencilState.getWriteMaskFront());
            EXECUTOR.stencilMaskSeparate(GL11.GL_BACK, glCtx.stencilState.getWriteMaskBack());
            EXECUTOR.clearStencil(glCtx.stencilState.getClearValue());

            EXECUTOR.viewport(glCtx.viewportState.x, glCtx.viewportState.y, glCtx.viewportState.width, glCtx.viewportState.height);
            EXECUTOR.depthRange(glCtx.viewportState.depthRangeNear, glCtx.viewportState.depthRangeFar);
            EXECUTOR.scissor(glCtx.scissorState.x, glCtx.scissorState.y, glCtx.scissorState.width, glCtx.scissorState.height);
            EXECUTOR.lineWidth(Math.max(lineWidthMin, Math.min(lineWidthMax, glCtx.lineState.getWidth())));
            applyPointState(glCtx);
            applyHintState(glCtx);

            EXECUTOR.polygonMode(GL11.GL_FRONT_AND_BACK, glCtx.polygonState.getFrontMode());
            applyPolygonOffset(glCtx);
            EXECUTOR.cullFace(glCtx.polygonState.getCullFaceMode());
            EXECUTOR.frontFace(glCtx.polygonState.getFrontFace());
            EXECUTOR.bindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, glCtx.drawFramebuffer);
            EXECUTOR.bindFramebuffer(GL30.GL_READ_FRAMEBUFFER, glCtx.readFramebuffer);
        }

    public static boolean isPoppingAttributes() {
            return ctx().poppingAttributes;
        }

    public static int getAttribDepth() {
            return ctx().attribDepth;
        }
}
