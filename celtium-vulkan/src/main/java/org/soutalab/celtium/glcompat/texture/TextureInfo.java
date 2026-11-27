package org.soutalab.celtium.glcompat.texture;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.GLStateManager;
import org.soutalab.celtium.glcompat.RenderSystem;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import static org.soutalab.celtium.glcompat.backend.DirectGlExecutor.EXECUTOR;

public class TextureInfo {

    private static final int UNSET_INT = Integer.MIN_VALUE;
    private static final float UNSET_FLOAT = Float.NEGATIVE_INFINITY;

     protected final int id;
      protected int target = GL11.GL_TEXTURE_2D;
    protected int internalFormat = -1;
    protected int resolvedInternalFormat = -1;
    protected int width = -1;
    protected int height = -1;

    protected int minFilter = UNSET_INT;
    protected int magFilter = UNSET_INT;
     protected int wrapS = UNSET_INT;
     protected int wrapT = UNSET_INT;
     protected int maxLevel = UNSET_INT;
      protected int baseLevel = 0;
      protected int minLod = -1000;
      protected int maxLod = 1000;
      protected float lodBias = 0.0f;

     protected float maxAnisotropy = UNSET_FLOAT;

      protected boolean generateMipmap = false;
    /** Texels between this atlas texture's sprites, or {@code 0} when it is not an atlas. */
      protected int atlasGutter = 0;

     protected int uploadGeneration;
    /** Whether something wrote this texture without going through the tracked upload entry points. */
     protected boolean writtenOutsideUploads;
    /** Counts every content or sampler change, so caches holding a copy can tell when it is stale. */
     protected int changeStamp;

    void bumpUploadGeneration() {
        uploadGeneration++;
        changeStamp++;
    }

    protected TextureInfo(int id) {
        this.id = id;
    }

    public int getInternalFormat() {
        if (internalFormat == -1) {
            internalFormat = RenderSystem.getTexLevelParameteri(id, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
        }
        return internalFormat;
    }

    public int getResolvedInternalFormat() {
        if (resolvedInternalFormat == -1) {
            resolvedInternalFormat = RenderSystem.getTexLevelParameteri(id, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
        }
        return resolvedInternalFormat;
    }

    public boolean needsInternalFormatResolve() {
        return resolvedInternalFormat == -1;
    }

    public int getWidth() {
        if (width == -1) {
            width = RenderSystem.getTexLevelParameteri(id, 0, GL11.GL_TEXTURE_WIDTH);
        }
        return width;
    }

    public int getHeight() {
        if (height == -1) {
            height = RenderSystem.getTexLevelParameteri(id, 0, GL11.GL_TEXTURE_HEIGHT);
        }
        return height;
    }

    public void setMinFilter(int minFilter) {
        this.minFilter = minFilter;
        changeStamp++;
    }

    public void setMagFilter(int magFilter) {
        this.magFilter = magFilter;
        changeStamp++;
    }

    public int getMinFilter() {
        if (minFilter == UNSET_INT) minFilter = queryParameteri(GL11.GL_TEXTURE_MIN_FILTER);
        return minFilter;
    }

    public int getMagFilter() {
        if (magFilter == UNSET_INT) magFilter = queryParameteri(GL11.GL_TEXTURE_MAG_FILTER);
        return magFilter;
    }

    public int getWrapS() {
        if (wrapS == UNSET_INT) wrapS = queryParameteri(GL11.GL_TEXTURE_WRAP_S);
        return wrapS;
    }

    public int getWrapT() {
        if (wrapT == UNSET_INT) wrapT = queryParameteri(GL11.GL_TEXTURE_WRAP_T);
        return wrapT;
    }

    public int getMaxLevel() {
        if (maxLevel == UNSET_INT) maxLevel = queryParameteri(GL12.GL_TEXTURE_MAX_LEVEL);
        return maxLevel;
    }

    public float getMaxAnisotropy() {
        if (maxAnisotropy == UNSET_FLOAT) {
            if (!EXECUTOR.isAnisotropicSupported()) maxAnisotropy = 1.0f;
            else maxAnisotropy = queryParameterf(EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT);
        }
        return maxAnisotropy;
    }

    private int queryParameteri(int pname) {
        if (GLStateManager.getBoundTextureForServerState() == id) {
            return EXECUTOR.getTexParameteri(target, pname);
        }
        return EXECUTOR.getTextureParameteri(id, target, pname);
    }

    private float queryParameterf(int pname) {
        if (GLStateManager.getBoundTextureForServerState() == id) {
            return EXECUTOR.getTexParameterf(target, pname);
        }
        return EXECUTOR.getTextureParameterf(id, target, pname);
    }

    // -- explicit accessors (delomboked during the S4 native conversion) --
    public int getId() { return id; }
    public int getTarget() { return target; }
    public void setTarget(int target) { this.target = target; }
    public void setWrapS(int wrapS) { this.wrapS = wrapS; }
    public void setWrapT(int wrapT) { this.wrapT = wrapT; }
    public void setMaxLevel(int maxLevel) { this.maxLevel = maxLevel; }
    public int getBaseLevel() { return baseLevel; }
    public void setBaseLevel(int baseLevel) { this.baseLevel = baseLevel; }
    public int getMinLod() { return minLod; }
    public void setMinLod(int minLod) { this.minLod = minLod; }
    public int getMaxLod() { return maxLod; }
    public void setMaxLod(int maxLod) { this.maxLod = maxLod; }
    public float getLodBias() { return lodBias; }
    public void setLodBias(float lodBias) { this.lodBias = lodBias; }
    public void setMaxAnisotropy(float maxAnisotropy) { this.maxAnisotropy = maxAnisotropy; }
    public boolean isGenerateMipmap() { return generateMipmap; }
    public void setGenerateMipmap(boolean generateMipmap) { this.generateMipmap = generateMipmap; }
    public int getAtlasGutter() { return atlasGutter; }
    public void setAtlasGutter(int atlasGutter) { this.atlasGutter = atlasGutter; }
    public int getUploadGeneration() { return uploadGeneration; }
    public boolean isWrittenOutsideUploads() { return writtenOutsideUploads; }
    public int getChangeStamp() { return changeStamp; }
}
