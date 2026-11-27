/*
 * Built-in shader pack engine for Celtium (LGPL-3.0).
 */
package org.soutalab.celtium.vulkan.pack;

import org.lwjgl.vulkan.VkCommandBuffer;
import org.soutalab.celtium.core.frame.ActiveRenderTargets;
import org.soutalab.celtium.core.frame.CameraPose;
import org.soutalab.celtium.core.frame.FrustumPlanes;
import org.soutalab.celtium.core.shader.NonTerrainPackTarget;

import java.util.List;
import java.util.Optional;

/**
 * Everything the pack executor cannot know on its own, supplied per frame by the frame's
 * owner (the frame coordinator in the integrated tree, the standalone leg driver in the
 * executor's own verification): cameras, the present target, the weather facts, the
 * non-terrain lane's draw windows and the texture names that live outside the plan
 * (block atlas, lightmap, noise).
 */
public interface PackFrameEnvironment {

    /** One block entity the terrain driver gathered inside the shadow frustum (gap G-7). */
    record ShadowBlockEntity(int blockX, int blockY, int blockZ, int blockEntityTypeId) {
    }

    /** A texture resolved outside the plan: everything a combined-sampler write needs. */
    record ResolvedTexture(long imageView, long sampler) {
    }

    /**
     * The non-terrain lane's draw windows inside a pack stage. The executor brackets each
     * window in its own dynamic-rendering scope over the stage's targets with LOAD semantics;
     * implementations record their draws into {@code cmd} against {@code target}.
     */
    interface Lane2Windows {

        /** Block entities (and entity shadows) inside the shadow stage, between CUTOUT and TRANSLUCENT. */
        void drawShadow(VkCommandBuffer cmd, NonTerrainPackTarget target,
                        List<ShadowBlockEntity> blockEntities);

        /** Entities and block entities inside the gbuffer stage, between CUTOUT and TRANSLUCENT. */
        void drawMainEntities(VkCommandBuffer cmd, NonTerrainPackTarget target);

        /** Hand, particles and weather inside the gbuffer stage, after TRANSLUCENT. */
        void drawMainTranslucentTail(VkCommandBuffer cmd, NonTerrainPackTarget target);

        Lane2Windows NONE = new Lane2Windows() {
            @Override
            public void drawShadow(VkCommandBuffer cmd, NonTerrainPackTarget target,
                                   List<ShadowBlockEntity> blockEntities) {
            }

            @Override
            public void drawMainEntities(VkCommandBuffer cmd, NonTerrainPackTarget target) {
            }

            @Override
            public void drawMainTranslucentTail(VkCommandBuffer cmd, NonTerrainPackTarget target) {
            }
        };
    }

    CameraPose mainCamera();

    FrustumPlanes mainFrustum();

    CameraPose shadowCamera();

    FrustumPlanes shadowFrustum();

    float shadowDistance();

    /**
     * The swapchain attachments the final pass renders into, or {@code null} when this frame
     * has no present target - the executor then skips the final pass (and only it).
     */
    ActiveRenderTargets swapchainTargets();

    PackWeatherFeed.WeatherState weather();

    Lane2Windows lane2();

    /** Resolves a pack texture name that is not a plan render target. */
    Optional<ResolvedTexture> texture(String name);
}
