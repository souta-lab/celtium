package org.soutalab.celtium.glcompat.hooks;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.DisplayListManager;
import org.soutalab.celtium.glcompat.GLContextState;
import org.soutalab.celtium.glcompat.GLSMTextures;
import org.soutalab.celtium.glcompat.GLStateManager;
import org.soutalab.celtium.glcompat.RenderSystem;
import org.soutalab.celtium.glcompat.backend.DirectGlExecutor;
import org.soutalab.celtium.glcompat.texture.TextureInfo;
import org.soutalab.celtium.glcompat.texture.TextureInfoCache;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public final class TextureUtilHooks {

    private static int pausedTexture;
    private static int pausedMin;
    private static int pausedMag;
    private static float pausedAniso;

    private static int nearestTexture;
    private static int nearestMin;
    private static int nearestMag;
    private static float nearestAniso;

    private TextureUtilHooks() {}

    public static void setMaxLevel(int mipmapLevels) {
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, mipmapLevels);
    }

    public static void captureBoundFilterTexture() {
        final GLContextState ctx = GLStateManager.ctx();
        final int id = GLSMTextures.getBoundTextureForServerState();
        ctx.savedFilterTextureId = id;
        final TextureInfo info = TextureInfoCache.INSTANCE.getInfo(id);
        if (info != null) {
            ctx.savedFilterMin = info.getMinFilter();
            ctx.savedFilterMag = info.getMagFilter();
            ctx.savedFilterAniso = info.getMaxAnisotropy();
        } else {
            ctx.savedFilterMin = -1;
        }
    }

    public static void clearSavedFilterTexture() {
        GLStateManager.ctx().savedFilterTextureId = 0;
    }

    public static boolean beginFilterSave(boolean blurred, boolean mipmap, float aniso) {
        captureBoundFilterTexture();
        return GLStateManager.beginExitTextureFilter(!blurred && !mipmap && aniso == 1);
    }

    public static void endFilterSave(boolean captured) {
        if (captured) GLStateManager.finishExitTextureFilterSave();
    }

    public static boolean beginFilterRestore() {
        return GLStateManager.beginExitTextureFilterRestore();
    }

    public static void endFilterRestore(boolean captured) {
        try {
            if (captured) GLStateManager.finishExitTextureFilterRestore();
        } finally {
            clearSavedFilterTexture();
        }
    }

    public static void filterFailed(boolean captured) {
        if (captured) GLStateManager.failExitTextureFilter();
    }

    public static void restoreFilter(int min, int mag) {
        final int id = GLStateManager.ctx().savedFilterTextureId;
        if (id != 0 && DisplayListManager.getRecordMode() == DisplayListManager.RecordMode.NONE) {
            RenderSystem.texParameteri(id, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, min);
            RenderSystem.texParameteri(id, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mag);
        } else {
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, min);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mag);
        }
    }

    public static void restoreAniso(float aniso) {
        final int id = GLStateManager.ctx().savedFilterTextureId;
        if (id != 0 && DisplayListManager.getRecordMode() == DisplayListManager.RecordMode.NONE) {
            RenderSystem.texParameterf(id, GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, aniso);
        } else {
            GLSMTextures.glTexParameterf(GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, aniso);
        }
    }

    public static boolean pauseTemporaryFilter() {
        final GLContextState ctx = GLStateManager.ctx();
        final int id = ctx.savedFilterTextureId;
        if (id == 0 || pausedTexture != 0 || ctx.savedFilterMin < 0
            || DisplayListManager.getRecordMode() != DisplayListManager.RecordMode.NONE) return false;
        final TextureInfo info = TextureInfoCache.INSTANCE.getInfo(id);
        if (info == null) return false;
        pausedTexture = id;
        pausedMin = info.getMinFilter();
        pausedMag = info.getMagFilter();
        pausedAniso = info.getMaxAnisotropy();
        setFilter(id, ctx.savedFilterMin, ctx.savedFilterMag, ctx.savedFilterAniso);
        return true;
    }

    public static void resumeTemporaryFilter() {
        if (pausedTexture == 0) return;
        final int id = pausedTexture;
        pausedTexture = 0;
        setFilter(id, pausedMin, pausedMag, pausedAniso);
    }

    /** TextureUtil.func_152777_a(false, false, 1) without using vanilla's single save slot, which the caller may hold. */
    public static void forceNearestFilter() {
        final int id = GLSMTextures.getBoundTextureForServerState();
        final TextureInfo info = id != 0 ? TextureInfoCache.INSTANCE.getInfo(id) : null;
        if (info == null) {
            nearestTexture = 0;
            return;
        }
        nearestTexture = id;
        nearestMin = info.getMinFilter();
        nearestMag = info.getMagFilter();
        nearestAniso = info.getMaxAnisotropy();
        setFilter(id, GL11.GL_NEAREST, GL11.GL_NEAREST, 1.0f);
    }

    public static void restoreFilterAfterNearest() {
        if (nearestTexture == 0) return;
        final int id = nearestTexture;
        nearestTexture = 0;
        setFilter(id, nearestMin, nearestMag, nearestAniso);
    }

    private static void setFilter(int id, int min, int mag, float aniso) {
        final boolean anisotropic = DirectGlExecutor.EXECUTOR.isAnisotropicSupported();
        if (DisplayListManager.getRecordMode() == DisplayListManager.RecordMode.NONE) {
            RenderSystem.texParameteri(id, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, min);
            RenderSystem.texParameteri(id, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mag);
            if (anisotropic) RenderSystem.texParameterf(id, GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, aniso);
        } else {
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, min);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mag);
            if (anisotropic) GLSMTextures.glTexParameterf(GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, aniso);
        }
    }
}
