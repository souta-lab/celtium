package org.soutalab.celtium.glcompat;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.DisplayListManager.RecordMode;
import org.soutalab.celtium.glcompat.ffp.VAOManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

import static org.soutalab.celtium.glcompat.GLStateManager.ctx;
import static org.soutalab.celtium.glcompat.GLStateManager.disableAlphaTest;
import static org.soutalab.celtium.glcompat.GLStateManager.disableBlend;
import static org.soutalab.celtium.glcompat.GLStateManager.disableColorMaterial;
import static org.soutalab.celtium.glcompat.GLStateManager.disableCull;
import static org.soutalab.celtium.glcompat.GLStateManager.disableDepthTest;
import static org.soutalab.celtium.glcompat.GLStateManager.disableFog;
import static org.soutalab.celtium.glcompat.GLStateManager.disableLight;
import static org.soutalab.celtium.glcompat.GLStateManager.disableLighting;
import static org.soutalab.celtium.glcompat.GLStateManager.disableRescaleNormal;
import static org.soutalab.celtium.glcompat.GLStateManager.disableScissorTest;
import static org.soutalab.celtium.glcompat.GLStateManager.disableTexture;
import static org.soutalab.celtium.glcompat.GLStateManager.enableAlphaTest;
import static org.soutalab.celtium.glcompat.GLStateManager.enableBlend;
import static org.soutalab.celtium.glcompat.GLStateManager.enableColorMaterial;
import static org.soutalab.celtium.glcompat.GLStateManager.enableCull;
import static org.soutalab.celtium.glcompat.GLStateManager.enableDepthTest;
import static org.soutalab.celtium.glcompat.GLStateManager.enableFog;
import static org.soutalab.celtium.glcompat.GLStateManager.enableLight;
import static org.soutalab.celtium.glcompat.GLStateManager.enableLighting;
import static org.soutalab.celtium.glcompat.GLStateManager.enableRescaleNormal;
import static org.soutalab.celtium.glcompat.GLStateManager.enableScissorTest;
import static org.soutalab.celtium.glcompat.GLStateManager.enableTexture;
import static org.soutalab.celtium.glcompat.GLStateManager.rejectExitCapture;
import static org.soutalab.celtium.glcompat.GLStateManager.shouldBypassCache;
import static org.soutalab.celtium.glcompat.backend.DirectGlExecutor.EXECUTOR;

public final class GLSMEnable {

    private GLSMEnable() {}

    public static void glEnable(int cap) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(cap);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (ClipPlaneBooleanState.isClipPlane(cap)) {
            ctx().clipPlaneStates[cap - GL11.GL_CLIP_PLANE0].enable();
            return;
        }

        switch (cap) {
            case GL11.GL_ALPHA_TEST -> enableAlphaTest();
            case GL11.GL_AUTO_NORMAL -> ctx().autoNormalState.enable();
            case GL11.GL_BLEND -> enableBlend();
            case GL11.GL_COLOR_MATERIAL -> enableColorMaterial();
            case GL11.GL_COLOR_LOGIC_OP -> ctx().colorLogicOpState.enable();
            case GL14.GL_COLOR_SUM -> ctx().colorSumState.enable();
            case GL11.GL_CULL_FACE -> enableCull();
            case GL11.GL_DEPTH_TEST -> enableDepthTest();
            case GL11.GL_DITHER -> ctx().ditherState.enable();
            case GL11.GL_FOG -> enableFog();
            case GL11.GL_INDEX_LOGIC_OP -> ctx().indexLogicOpState.enable();
            case GL11.GL_LIGHTING -> enableLighting();
            case GL11.GL_LIGHT0 -> enableLight(0);
            case GL11.GL_LIGHT1 -> enableLight(1);
            case GL11.GL_LIGHT2 -> enableLight(2);
            case GL11.GL_LIGHT3 -> enableLight(3);
            case GL11.GL_LIGHT4 -> enableLight(4);
            case GL11.GL_LIGHT5 -> enableLight(5);
            case GL11.GL_LIGHT6 -> enableLight(6);
            case GL11.GL_LIGHT7 -> enableLight(7);
            case GL11.GL_LINE_SMOOTH -> ctx().lineSmoothState.enable();
            case GL11.GL_LINE_STIPPLE -> ctx().lineStippleState.enable();
            case GL11.GL_MAP1_COLOR_4 -> ctx().map1Color4State.enable();
            case GL11.GL_MAP1_INDEX -> ctx().map1IndexState.enable();
            case GL11.GL_MAP1_NORMAL -> ctx().map1NormalState.enable();
            case GL11.GL_MAP1_TEXTURE_COORD_1 -> ctx().map1TextureCoord1State.enable();
            case GL11.GL_MAP1_TEXTURE_COORD_2 -> ctx().map1TextureCoord2State.enable();
            case GL11.GL_MAP1_TEXTURE_COORD_3 -> ctx().map1TextureCoord3State.enable();
            case GL11.GL_MAP1_TEXTURE_COORD_4 -> ctx().map1TextureCoord4State.enable();
            case GL11.GL_MAP1_VERTEX_3 -> ctx().map1Vertex3State.enable();
            case GL11.GL_MAP1_VERTEX_4 -> ctx().map1Vertex4State.enable();
            case GL11.GL_MAP2_COLOR_4 -> ctx().map2Color4State.enable();
            case GL11.GL_MAP2_INDEX -> ctx().map2IndexState.enable();
            case GL11.GL_MAP2_NORMAL -> ctx().map2NormalState.enable();
            case GL11.GL_MAP2_TEXTURE_COORD_1 -> ctx().map2TextureCoord1State.enable();
            case GL11.GL_MAP2_TEXTURE_COORD_2 -> ctx().map2TextureCoord2State.enable();
            case GL11.GL_MAP2_TEXTURE_COORD_3 -> ctx().map2TextureCoord3State.enable();
            case GL11.GL_MAP2_TEXTURE_COORD_4 -> ctx().map2TextureCoord4State.enable();
            case GL11.GL_MAP2_VERTEX_3 -> ctx().map2Vertex3State.enable();
            case GL11.GL_MAP2_VERTEX_4 -> ctx().map2Vertex4State.enable();
            case GL13.GL_MULTISAMPLE -> ctx().multisampleState.enable();
            case GL11.GL_NORMALIZE -> ctx().normalizeState.enable();
            case GL11.GL_POINT_SMOOTH -> ctx().pointSmoothState.enable();
            case GL11.GL_POLYGON_OFFSET_POINT -> ctx().polygonOffsetPointState.enable();
            case GL11.GL_POLYGON_OFFSET_LINE -> ctx().polygonOffsetLineState.enable();
            case GL11.GL_POLYGON_OFFSET_FILL -> ctx().polygonOffsetFillState.enable();
            case GL11.GL_POLYGON_SMOOTH -> ctx().polygonSmoothState.enable();
            case GL11.GL_POLYGON_STIPPLE -> ctx().polygonStippleState.enable();
            case GL12.GL_RESCALE_NORMAL -> enableRescaleNormal();
            case GL13.GL_SAMPLE_ALPHA_TO_COVERAGE -> ctx().sampleAlphaToCoverageState.enable();
            case GL13.GL_SAMPLE_ALPHA_TO_ONE -> ctx().sampleAlphaToOneState.enable();
            case GL13.GL_SAMPLE_COVERAGE -> ctx().sampleCoverageState.enable();
            case GL11.GL_SCISSOR_TEST -> enableScissorTest();
            case GL11.GL_STENCIL_TEST -> ctx().stencilTest.enable();
            case GL11.GL_TEXTURE_1D -> ctx().textures.getTexture1DStates(ctx().activeTextureUnit.getValue()).enable();
            case GL11.GL_TEXTURE_2D -> enableTexture();
            case GL12.GL_TEXTURE_3D -> ctx().textures.getTexture3DStates(ctx().activeTextureUnit.getValue()).enable();
            case GL13.GL_TEXTURE_CUBE_MAP -> ctx().textures.getTextureCubeMapStates(ctx().activeTextureUnit.getValue()).enable();
            case GL20.GL_POINT_SPRITE -> ctx().pointSpriteState.enable();
            case GL20.GL_VERTEX_PROGRAM_TWO_SIDE -> ctx().vertexProgramTwoSideState.enable();
            case GL11.GL_TEXTURE_GEN_S -> ctx().textures.getTexGenSStates(ctx().activeTextureUnit.getValue()).enable();
            case GL11.GL_TEXTURE_GEN_T -> ctx().textures.getTexGenTStates(ctx().activeTextureUnit.getValue()).enable();
            case GL11.GL_TEXTURE_GEN_R -> ctx().textures.getTexGenRStates(ctx().activeTextureUnit.getValue()).enable();
            case GL11.GL_TEXTURE_GEN_Q -> ctx().textures.getTexGenQStates(ctx().activeTextureUnit.getValue()).enable();
            default -> {
                ctx().exitCapture.reject();
                EXECUTOR.enable(cap);
            }
        }
    }

    public static void glDisable(int cap) {
        final RecordMode mode = DisplayListManager.getRecordMode();        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(cap);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (ClipPlaneBooleanState.isClipPlane(cap)) {
            ctx().clipPlaneStates[cap - GL11.GL_CLIP_PLANE0].disable();
            return;
        }

        switch (cap) {
            case GL11.GL_ALPHA_TEST -> disableAlphaTest();
            case GL11.GL_AUTO_NORMAL -> ctx().autoNormalState.disable();
            case GL11.GL_BLEND -> disableBlend();
            case GL11.GL_COLOR_MATERIAL -> disableColorMaterial();
            case GL11.GL_COLOR_LOGIC_OP -> ctx().colorLogicOpState.disable();
            case GL14.GL_COLOR_SUM -> ctx().colorSumState.disable();
            case GL11.GL_CULL_FACE -> disableCull();
            case GL11.GL_DEPTH_TEST -> disableDepthTest();
            case GL11.GL_DITHER -> ctx().ditherState.disable();
            case GL11.GL_FOG -> disableFog();
            case GL11.GL_INDEX_LOGIC_OP -> ctx().indexLogicOpState.disable();
            case GL11.GL_LIGHTING -> disableLighting();
            case GL11.GL_LIGHT0 -> disableLight(0);
            case GL11.GL_LIGHT1 -> disableLight(1);
            case GL11.GL_LIGHT2 -> disableLight(2);
            case GL11.GL_LIGHT3 -> disableLight(3);
            case GL11.GL_LIGHT4 -> disableLight(4);
            case GL11.GL_LIGHT5 -> disableLight(5);
            case GL11.GL_LIGHT6 -> disableLight(6);
            case GL11.GL_LIGHT7 -> disableLight(7);
            case GL11.GL_LINE_SMOOTH -> ctx().lineSmoothState.disable();
            case GL11.GL_LINE_STIPPLE -> ctx().lineStippleState.disable();
            case GL11.GL_MAP1_COLOR_4 -> ctx().map1Color4State.disable();
            case GL11.GL_MAP1_INDEX -> ctx().map1IndexState.disable();
            case GL11.GL_MAP1_NORMAL -> ctx().map1NormalState.disable();
            case GL11.GL_MAP1_TEXTURE_COORD_1 -> ctx().map1TextureCoord1State.disable();
            case GL11.GL_MAP1_TEXTURE_COORD_2 -> ctx().map1TextureCoord2State.disable();
            case GL11.GL_MAP1_TEXTURE_COORD_3 -> ctx().map1TextureCoord3State.disable();
            case GL11.GL_MAP1_TEXTURE_COORD_4 -> ctx().map1TextureCoord4State.disable();
            case GL11.GL_MAP1_VERTEX_3 -> ctx().map1Vertex3State.disable();
            case GL11.GL_MAP1_VERTEX_4 -> ctx().map1Vertex4State.disable();
            case GL11.GL_MAP2_COLOR_4 -> ctx().map2Color4State.disable();
            case GL11.GL_MAP2_INDEX -> ctx().map2IndexState.disable();
            case GL11.GL_MAP2_NORMAL -> ctx().map2NormalState.disable();
            case GL11.GL_MAP2_TEXTURE_COORD_1 -> ctx().map2TextureCoord1State.disable();
            case GL11.GL_MAP2_TEXTURE_COORD_2 -> ctx().map2TextureCoord2State.disable();
            case GL11.GL_MAP2_TEXTURE_COORD_3 -> ctx().map2TextureCoord3State.disable();
            case GL11.GL_MAP2_TEXTURE_COORD_4 -> ctx().map2TextureCoord4State.disable();
            case GL11.GL_MAP2_VERTEX_3 -> ctx().map2Vertex3State.disable();
            case GL11.GL_MAP2_VERTEX_4 -> ctx().map2Vertex4State.disable();
            case GL13.GL_MULTISAMPLE -> ctx().multisampleState.disable();
            case GL11.GL_NORMALIZE -> ctx().normalizeState.disable();
            case GL11.GL_POINT_SMOOTH -> ctx().pointSmoothState.disable();
            case GL11.GL_POLYGON_OFFSET_POINT -> ctx().polygonOffsetPointState.disable();
            case GL11.GL_POLYGON_OFFSET_LINE -> ctx().polygonOffsetLineState.disable();
            case GL11.GL_POLYGON_OFFSET_FILL -> ctx().polygonOffsetFillState.disable();
            case GL11.GL_POLYGON_SMOOTH -> ctx().polygonSmoothState.disable();
            case GL11.GL_POLYGON_STIPPLE -> ctx().polygonStippleState.disable();
            case GL12.GL_RESCALE_NORMAL -> disableRescaleNormal();
            case GL13.GL_SAMPLE_ALPHA_TO_COVERAGE -> ctx().sampleAlphaToCoverageState.disable();
            case GL13.GL_SAMPLE_ALPHA_TO_ONE -> ctx().sampleAlphaToOneState.disable();
            case GL13.GL_SAMPLE_COVERAGE -> ctx().sampleCoverageState.disable();
            case GL11.GL_SCISSOR_TEST -> disableScissorTest();
            case GL11.GL_STENCIL_TEST -> ctx().stencilTest.disable();
            case GL11.GL_TEXTURE_1D -> ctx().textures.getTexture1DStates(ctx().activeTextureUnit.getValue()).disable();
            case GL11.GL_TEXTURE_2D -> disableTexture();
            case GL12.GL_TEXTURE_3D -> ctx().textures.getTexture3DStates(ctx().activeTextureUnit.getValue()).disable();
            case GL13.GL_TEXTURE_CUBE_MAP -> ctx().textures.getTextureCubeMapStates(ctx().activeTextureUnit.getValue()).disable();
            case GL20.GL_POINT_SPRITE -> ctx().pointSpriteState.disable();
            case GL20.GL_VERTEX_PROGRAM_TWO_SIDE -> ctx().vertexProgramTwoSideState.disable();
            case GL11.GL_TEXTURE_GEN_S -> ctx().textures.getTexGenSStates(ctx().activeTextureUnit.getValue()).disable();
            case GL11.GL_TEXTURE_GEN_T -> ctx().textures.getTexGenTStates(ctx().activeTextureUnit.getValue()).disable();
            case GL11.GL_TEXTURE_GEN_R -> ctx().textures.getTexGenRStates(ctx().activeTextureUnit.getValue()).disable();
            case GL11.GL_TEXTURE_GEN_Q -> ctx().textures.getTexGenQStates(ctx().activeTextureUnit.getValue()).disable();
            default -> {
                ctx().exitCapture.reject();
                EXECUTOR.disable(cap);
            }
        }
    }

    public static boolean glIsEnabled(int cap) {
        rejectExitCapture();
        if (shouldBypassCache() && !(ClipPlaneBooleanState.isClipPlane(cap) && RenderSystem.isClipDistanceEmulated())) {
            return EXECUTOR.getBoolean(cap);
        }
        if (ClipPlaneBooleanState.isClipPlane(cap)) {
            return ctx().clipPlaneStates[cap - GL11.GL_CLIP_PLANE0].isEnabled();
        }

        return switch (cap) {
            case GL11.GL_ALPHA_TEST -> ctx().alphaTest.isEnabled();
            case GL11.GL_AUTO_NORMAL -> ctx().autoNormalState.isEnabled();
            case GL11.GL_BLEND -> ctx().blendMode.isEnabled();
            case GL11.GL_COLOR_MATERIAL -> ctx().colorMaterial.isEnabled();
            case GL11.GL_COLOR_LOGIC_OP -> ctx().colorLogicOpState.isEnabled();
            case GL14.GL_COLOR_SUM -> ctx().colorSumState.isEnabled();
            case GL11.GL_CULL_FACE -> ctx().cullState.isEnabled();
            case GL11.GL_DEPTH_TEST -> ctx().depthTest.isEnabled();
            case GL11.GL_DITHER -> ctx().ditherState.isEnabled();
            case GL11.GL_FOG -> ctx().fogMode.isEnabled();
            case GL11.GL_INDEX_LOGIC_OP -> ctx().indexLogicOpState.isEnabled();
            case GL11.GL_LIGHTING -> ctx().lightingState.isEnabled();
            case GL11.GL_LIGHT0 -> ctx().lightStates[0].isEnabled();
            case GL11.GL_LIGHT1 -> ctx().lightStates[1].isEnabled();
            case GL11.GL_LIGHT2 -> ctx().lightStates[2].isEnabled();
            case GL11.GL_LIGHT3 -> ctx().lightStates[3].isEnabled();
            case GL11.GL_LIGHT4 -> ctx().lightStates[4].isEnabled();
            case GL11.GL_LIGHT5 -> ctx().lightStates[5].isEnabled();
            case GL11.GL_LIGHT6 -> ctx().lightStates[6].isEnabled();
            case GL11.GL_LIGHT7 -> ctx().lightStates[7].isEnabled();
            case GL11.GL_LINE_SMOOTH -> ctx().lineSmoothState.isEnabled();
            case GL11.GL_LINE_STIPPLE -> ctx().lineStippleState.isEnabled();
            case GL11.GL_MAP1_COLOR_4 -> ctx().map1Color4State.isEnabled();
            case GL11.GL_MAP1_INDEX -> ctx().map1IndexState.isEnabled();
            case GL11.GL_MAP1_NORMAL -> ctx().map1NormalState.isEnabled();
            case GL11.GL_MAP1_TEXTURE_COORD_1 -> ctx().map1TextureCoord1State.isEnabled();
            case GL11.GL_MAP1_TEXTURE_COORD_2 -> ctx().map1TextureCoord2State.isEnabled();
            case GL11.GL_MAP1_TEXTURE_COORD_3 -> ctx().map1TextureCoord3State.isEnabled();
            case GL11.GL_MAP1_TEXTURE_COORD_4 -> ctx().map1TextureCoord4State.isEnabled();
            case GL11.GL_MAP1_VERTEX_3 -> ctx().map1Vertex3State.isEnabled();
            case GL11.GL_MAP1_VERTEX_4 -> ctx().map1Vertex4State.isEnabled();
            case GL11.GL_MAP2_COLOR_4 -> ctx().map2Color4State.isEnabled();
            case GL11.GL_MAP2_INDEX -> ctx().map2IndexState.isEnabled();
            case GL11.GL_MAP2_NORMAL -> ctx().map2NormalState.isEnabled();
            case GL11.GL_MAP2_TEXTURE_COORD_1 -> ctx().map2TextureCoord1State.isEnabled();
            case GL11.GL_MAP2_TEXTURE_COORD_2 -> ctx().map2TextureCoord2State.isEnabled();
            case GL11.GL_MAP2_TEXTURE_COORD_3 -> ctx().map2TextureCoord3State.isEnabled();
            case GL11.GL_MAP2_TEXTURE_COORD_4 -> ctx().map2TextureCoord4State.isEnabled();
            case GL11.GL_MAP2_VERTEX_3 -> ctx().map2Vertex3State.isEnabled();
            case GL11.GL_MAP2_VERTEX_4 -> ctx().map2Vertex4State.isEnabled();
            case GL13.GL_MULTISAMPLE -> ctx().multisampleState.isEnabled();
            case GL11.GL_NORMALIZE -> ctx().normalizeState.isEnabled();
            case GL11.GL_POINT_SMOOTH -> ctx().pointSmoothState.isEnabled();
            case GL11.GL_POLYGON_OFFSET_POINT -> ctx().polygonOffsetPointState.isEnabled();
            case GL11.GL_POLYGON_OFFSET_LINE -> ctx().polygonOffsetLineState.isEnabled();
            case GL11.GL_POLYGON_OFFSET_FILL -> ctx().polygonOffsetFillState.isEnabled();
            case GL11.GL_POLYGON_SMOOTH -> ctx().polygonSmoothState.isEnabled();
            case GL11.GL_POLYGON_STIPPLE -> ctx().polygonStippleState.isEnabled();
            case GL12.GL_RESCALE_NORMAL -> ctx().rescaleNormalState.isEnabled();
            case GL13.GL_SAMPLE_ALPHA_TO_COVERAGE -> ctx().sampleAlphaToCoverageState.isEnabled();
            case GL13.GL_SAMPLE_ALPHA_TO_ONE -> ctx().sampleAlphaToOneState.isEnabled();
            case GL13.GL_SAMPLE_COVERAGE -> ctx().sampleCoverageState.isEnabled();
            case GL11.GL_SCISSOR_TEST -> ctx().scissorTest.isEnabled();
            case GL11.GL_STENCIL_TEST -> ctx().stencilTest.isEnabled();
            case GL11.GL_TEXTURE_1D -> ctx().textures.getTexture1DStates(ctx().activeTextureUnit.getValue()).isEnabled();
            case GL11.GL_TEXTURE_2D -> ctx().textures.getTextureUnitStates(ctx().activeTextureUnit.getValue()).isEnabled();
            case GL12.GL_TEXTURE_3D -> ctx().textures.getTexture3DStates(ctx().activeTextureUnit.getValue()).isEnabled();
            case GL13.GL_TEXTURE_CUBE_MAP -> ctx().textures.getTextureCubeMapStates(ctx().activeTextureUnit.getValue()).isEnabled();
            case GL20.GL_POINT_SPRITE -> ctx().pointSpriteState.isEnabled();
            case GL20.GL_VERTEX_PROGRAM_TWO_SIDE -> ctx().vertexProgramTwoSideState.isEnabled();
            case GL11.GL_TEXTURE_GEN_S -> ctx().textures.getTexGenSStates(ctx().activeTextureUnit.getValue()).isEnabled();
            case GL11.GL_TEXTURE_GEN_T -> ctx().textures.getTexGenTStates(ctx().activeTextureUnit.getValue()).isEnabled();
            case GL11.GL_TEXTURE_GEN_R -> ctx().textures.getTexGenRStates(ctx().activeTextureUnit.getValue()).isEnabled();
            case GL11.GL_TEXTURE_GEN_Q -> ctx().textures.getTexGenQStates(ctx().activeTextureUnit.getValue()).isEnabled();
            default -> {
                final int conv = VAOManager.pnameToConventional(cap, ctx().clientActiveTextureUnit);
                yield conv >= 0 ? ctx().vaos.isConventionalEnabled(conv) : EXECUTOR.getBoolean(cap);
            }
        };
    }

    public static boolean glIsEnabledi(int cap, int index) {
        GLStateManager.rejectExitCapture();
        return EXECUTOR.isEnabledIndexed(cap, index);
    }

    public static void glEnablei(int cap, int index) {
        rejectExitCapture();
        EXECUTOR.enablei(cap, index);
        markIndexedCapUnknown(cap);
    }

    public static void glDisablei(int cap, int index) {
        rejectExitCapture();
        EXECUTOR.disablei(cap, index);
        markIndexedCapUnknown(cap);
    }

    private static void markIndexedCapUnknown(int cap) {
        if (cap == GL11.GL_BLEND) {
            ctx().blendMode.setUnknownState();
        } else if (cap == GL11.GL_SCISSOR_TEST) {
            ctx().scissorTest.setUnknownState();
        }
    }
}
