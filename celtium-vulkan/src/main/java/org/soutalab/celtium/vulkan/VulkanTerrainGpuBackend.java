package org.soutalab.celtium.vulkan;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.embeddedt.embeddium.impl.common.util.MathUtil;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkSortOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkTaskOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkJobResult;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltSectionMeshParts;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.fog.FogService;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.BatchAssembler;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.CachedBatch;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegionManager;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkSubmitInfo;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.lwjgl.vulkan.VK10.*;

/**
 * Terrain GPU backend implemented with Vulkan.
 * <p>
 * It consumes the same meshing output, the same per-section metadata, the same culling and batch assembly and the
 * same render lists as the OpenGL backend; only the GPU resources and the draw calls differ. Renders into the
 * game's framebuffer through {@link VulkanFrameTarget}, which shares the colour and depth images with OpenGL.
 * <p>
 * Runs on the client/render thread with a current GL context, like the OpenGL backend it replaces.
 */
public final class VulkanTerrainGpuBackend implements org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend {
    /** Alignment required for dynamic uniform buffer offsets. Tightened at construction from the device limits. */
    private static final int MIN_UNIFORM_ALIGNMENT = 256;

    /**
     * Bytes per dynamic uniform block. Mirrors {@code CeltiumSolidUniforms} in
     * {@code celtium-vulkan/src/main/shaders/terrain_solid.vert}: region offset, fog colour and parameters,
     * material flags and the (currently unused) chunk fade parameters.
     */
    private static final int UNIFORM_BYTES = 80;

    private final VulkanContext context;
    private final RenderPassConfiguration<?> configuration;
    private final RenderRegionManager regionManager;
    private final FogService fogService;
    private final int commonVertexStride;
    private final int uniformAlignment;

    private final Reference2ReferenceMap<RenderRegion, RegionResources> regionResources = new Reference2ReferenceOpenHashMap<>();
    private final Reference2ReferenceMap<TerrainRenderPass, VulkanTerrainPipeline> pipelines = new Reference2ReferenceOpenHashMap<>();
    private final List<String> diagnostics = new ArrayList<>();

    private final Reference2ReferenceMap<ChunkPrimitiveType, VulkanBuffer> sharedIndexBuffers = new Reference2ReferenceOpenHashMap<>();
    private final Reference2ReferenceMap<ChunkPrimitiveType, Integer> sharedIndexPrimitives = new Reference2ReferenceOpenHashMap<>();

    private VulkanUniformBuffer uniforms;
    private long uniformStride;
    private int uniformBlocks;
    private long uniformWriteCursor;

    private VulkanFrameTarget frameTarget;
    private int targetWidth;
    private int targetHeight;
    private long framebuffer;
    private long framebufferRenderPass;

    private VulkanGlTexture atlasTexture;
    private VulkanGlTexture lightmapTexture;
    private int atlasGlId = -1;
    private int lightmapGlId = -1;
    private long atlasRefreshCountdown;
    private long lightmapRefreshCountdown;

    private long commandPool;
    private VkCommandBuffer commandBuffer;
    private long fence;
    private long frameIndex;

    /**
     * The OpenGL projection remapped from the OpenGL clip-depth range [-1, 1] to Vulkan's [0, 1]. Without it every
     * fragment below the near plane would clamp to depth 0 and the terrain would fight the depth buffer that
     * OpenGL wrote. Reused instead of allocated per frame.
     */
    private final Matrix4f vulkanProjection = new Matrix4f();

    private long lastDrawCommands;
    private int command0Logged;
    private long lastSectionsDrawn;
    private long lastUploadedBytes;

    public VulkanTerrainGpuBackend(VulkanContext context,
                                   RenderPassConfiguration<?> configuration,
                                   RenderRegionManager regionManager,
                                   FogService fogService) {
        this.context = context;
        this.configuration = configuration;
        this.regionManager = regionManager;
        this.fogService = fogService;
        this.commonVertexStride = configuration.getAllKnownRenderPasses()
                .mapToInt(pass -> pass.vertexType().getVertexFormat().getStride())
                .reduce(1, MathUtil::lcm);
        this.uniformAlignment = Math.max(MIN_UNIFORM_ALIGNMENT,
                Integer.highestOneBit(Math.max(MIN_UNIFORM_ALIGNMENT, this.alignmentFromDevice())));
        this.uniformStride = align(UNIFORM_BYTES, this.uniformAlignment);
        createCommandResources();
    }

    /** Records the Vulkan backend in place of the OpenGL one for the batch assembly path. */
    public void install() {
        BatchAssembler.setBatchFactory(VulkanMultiDrawBatch::new);
    }

    /** Restores the OpenGL batch factory when this backend is torn down. */
    private void uninstall() {
        BatchAssembler.setBatchFactory(org.embeddedt.embeddium.impl.gl.device.DirectMultiDrawBatch::new);
    }

    /** Human-readable state for logs and debug overlays; never null. */
    public String describe() {
        StringBuilder builder = new StringBuilder("vulkan");
        builder.append(" [").append(this.context.deviceName()).append(']');
        builder.append(" regions=").append(this.regionResources.size());
        builder.append(" draws=").append(this.lastDrawCommands);
        builder.append(" sections=").append(this.lastSectionsDrawn);
        builder.append(" uploaded=").append(this.lastUploadedBytes).append("B");
        builder.append(" index=").append(this.sharedIndexBuffers.size());
        if (this.frameTarget != null) {
            builder.append(" target=").append(this.targetWidth).append('x').append(this.targetHeight);
        }
        for (String diagnostic : this.diagnostics) {
            builder.append(" | ").append(diagnostic);
        }
        return builder.toString();
    }

    public List<String> diagnostics() {
        return this.diagnostics;
    }

    // ------------------------------------------------------------------ TerrainGpuBackend

    @Override
    public String getName() {
        return "vulkan";
    }

    @Override
    public void render(ChunkRenderMatrices matrices, ChunkRenderListIterable renderLists, TerrainRenderPass pass,
                       CameraTransform occlusionCamera, CameraTransform camera, boolean profile) {
        if (!renderLists.hasPass(pass)) {
            return;
        }
        if (!ensureFrameTarget()) {
            return;
        }
        VulkanTerrainPipeline pipeline = pipelineFor(pass);
        if (pipeline == null) {
            return;
        }
        ChunkPrimitiveType primitiveType = this.configuration.getPrimitiveTypeForPass(pass);
        ensureSharedIndexBuffer(primitiveType);

        refreshTextures();
        if (this.atlasTexture == null || this.lightmapTexture == null) {
            // Without both textures the descriptor set cannot be filled; the pass is skipped and the reason is
            // recorded instead of drawing with an unbound atlas.
            record("pass " + pass.name() + " skipped: the block atlas or the lightmap is not mirrored yet");
            return;
        }

        boolean useBlockFaceCulling = !pass.isSorted();
        this.lastDrawCommands = 0;
        this.lastSectionsDrawn = 0;
        this.frameIndex++;

        try {
            this.frameTarget.beginFrame();
        } catch (RuntimeException | LinkageError error) {
            record("frame begin failed: " + describe(error));
            return;
        }

        boolean submitted = false;
        try {
            beginCommands();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                recordRenderPass(stack, pipeline, matrices, renderLists, pass, occlusionCamera, camera,
                        useBlockFaceCulling, primitiveType);
            }
            submitCommands();
            submitted = true;
        } catch (RuntimeException | LinkageError error) {
            record("render failed: " + describe(error));
        } finally {
            if (!submitted) {
                // endFrame() makes GL wait on the Vulkan->GL semaphore, and GL has no timeout. A submission must
                // therefore consume and signal it even when recording failed: an empty acquire/release round trip
                // hands the untouched images straight back, which is exactly what the game expects.
                try {
                    waitForIdle();
                    beginCommands();
                    this.frameTarget.recordAcquireBarriers(this.commandBuffer);
                    this.frameTarget.recordReleaseBarriers(this.commandBuffer);
                    submitCommands();
                } catch (RuntimeException | LinkageError error) {
                    record("frame recovery failed: " + describe(error));
                }
            }
            try {
                this.frameTarget.endFrame();
            } catch (RuntimeException | LinkageError error) {
                record("frame end failed: " + describe(error));
            }
        }
    }

    @Override
    public void uploadMeshes(Collection<ChunkJobResult.Success<? extends ChunkTaskOutput>> results, Runnable graphUpdateTrigger) {
        Reference2ReferenceMap<RenderRegion, List<ChunkTaskOutput>> queues = new Reference2ReferenceOpenHashMap<>();
        for (ChunkJobResult.Success<? extends ChunkTaskOutput> holder : results) {
            ChunkTaskOutput output = holder.output();
            List<ChunkTaskOutput> queue = queues.get(output.render.getRegion());
            if (queue == null) {
                queue = new ArrayList<>();
                queues.put(output.render.getRegion(), queue);
            }
            queue.add(output);
        }

        for (Reference2ReferenceMap.Entry<RenderRegion, List<ChunkTaskOutput>> entry : queues.reference2ReferenceEntrySet()) {
            uploadRegion(entry.getKey(), entry.getValue(), graphUpdateTrigger);
        }
    }

    @Override
    public void updateRegions() {
        // The region manager owns region lifetime (creating regions for loaded chunks and dropping empty ones).
        // It is reused unchanged; the Vulkan arenas are released for the regions it has dropped.
        this.regionManager.update();
        if (this.regionResources.isEmpty()) {
            return;
        }
        Set<RenderRegion> live = Collections.newSetFromMap(new IdentityHashMap<RenderRegion, Boolean>());
        live.addAll(this.regionManager.getLoadedRegions());
        Iterator<Reference2ReferenceMap.Entry<RenderRegion, RegionResources>> iterator =
                this.regionResources.reference2ReferenceEntrySet().iterator();
        while (iterator.hasNext()) {
            Reference2ReferenceMap.Entry<RenderRegion, RegionResources> entry = iterator.next();
            if (!live.contains(entry.getKey())) {
                entry.getValue().delete();
                iterator.remove();
            }
        }
    }

    @Override
    public void updateTimings() {
        // Vulkan GPU timestamps are not collected yet; the debug overlay falls back to "?? ms" like an
        // unsupported timer query does on the OpenGL side.
    }

    @Override
    public Object2LongMap<TerrainRenderPass> snapshotTimings() {
        return new Object2LongOpenHashMap<>();
    }

    @Override
    public void collectMemoryStats(RenderSectionManager.DeviceMemoryStats destination) {
        for (RegionResources resources : this.regionResources.values()) {
            destination.deviceUsed += resources.vertexArena.deviceUsedMemoryL();
            destination.deviceAllocated += resources.vertexArena.deviceAllocatedMemoryL();
            if (resources.indexArena != null) {
                destination.indexUsed += resources.indexArena.deviceUsedMemoryL();
                destination.indexAllocated += resources.indexArena.deviceAllocatedMemoryL();
            }
            destination.bufferCount += resources.bufferCount();
        }
    }

    @Override
    public String getTransferQueueDebugString() {
        // The debug overlay shows this line, so it carries the backend's own state: without it a terrain pass that
        // silently skips (missing textures, no frame target, a failed pipeline) would look like a rendering bug.
        return describe();
    }

    @Override
    public void delete() {
        uninstall();
        for (RegionResources resources : this.regionResources.values()) {
            resources.delete();
        }
        this.regionResources.clear();
        for (VulkanTerrainPipeline pipeline : this.pipelines.values()) {
            pipeline.delete();
        }
        this.pipelines.clear();
        for (VulkanBuffer buffer : this.sharedIndexBuffers.values()) {
            buffer.delete();
        }
        this.sharedIndexBuffers.clear();
        this.sharedIndexPrimitives.clear();
        if (this.uniforms != null) {
            this.uniforms.delete();
            this.uniforms = null;
        }
        if (this.atlasTexture != null) {
            this.atlasTexture.delete();
            this.atlasTexture = null;
        }
        if (this.lightmapTexture != null) {
            this.lightmapTexture.delete();
            this.lightmapTexture = null;
        }
        destroyFramebuffer();
        if (this.frameTarget != null) {
            this.frameTarget.delete();
            this.frameTarget = null;
        }
        destroyCommandResources();
    }

    // ------------------------------------------------------------------ upload path

    private void uploadRegion(RenderRegion region, List<ChunkTaskOutput> outputs, Runnable graphUpdateTrigger) {
        List<PendingMesh> pending = new ArrayList<>();
        boolean needIndexBuffer = false;

        for (ChunkTaskOutput output : outputs) {
            if (output instanceof ChunkBuildOutput) {
                ChunkBuildOutput build = (ChunkBuildOutput) output;
                region.removeMeshes(build.render.getSectionIndex());
                for (Reference2ReferenceMap.Entry<TerrainRenderPass, BuiltSectionMeshParts> meshEntry
                        : build.meshes.reference2ReferenceEntrySet()) {
                    BuiltSectionMeshParts mesh = meshEntry.getValue();
                    if (mesh == null) {
                        continue;
                    }
                    needIndexBuffer |= mesh.indexBuffer() != null;
                    pending.add(new PendingMesh(build.render.getSectionIndex(), meshEntry.getKey(), mesh, null));
                }
            } else if (output instanceof ChunkSortOutput) {
                ChunkSortOutput sort = (ChunkSortOutput) output;
                needIndexBuffer = true;
                for (Reference2ReferenceMap.Entry<TerrainRenderPass, ChunkSortOutput.SortedMesh> meshEntry
                        : sort.meshes.reference2ReferenceEntrySet()) {
                    SectionRenderDataStorage storage = region.getStorage(meshEntry.getKey());
                    if (storage != null) {
                        storage.removeIndexBuffer(sort.render.getSectionIndex());
                    }
                    pending.add(new PendingMesh(sort.render.getSectionIndex(), meshEntry.getKey(), null,
                            meshEntry.getValue()));
                }
            } else {
                throw new IllegalStateException("Unexpected result type: " + output.getClass().getName());
            }
        }

        if (pending.isEmpty()) {
            return;
        }

        RegionResources resources = resourcesFor(region);
        long uploaded = 0;

        for (PendingMesh mesh : pending) {
            if (mesh.parts != null) {
                ByteBuffer vertices = mesh.parts.vertexBuffer().getDirectBuffer();
                int vertexBytes = mesh.parts.vertexBuffer().getLength();
                VulkanArena.Segment vertexSegment = resources.vertexArena.allocate(vertexBytes);
                resources.vertexArena.write(vertexSegment.byteOffset(), MemoryUtil.memAddress(vertices), vertexBytes);
                uploaded += vertexBytes;

                VulkanArena.Segment indexSegment = null;
                if (mesh.parts.indexBuffer() != null) {
                    ByteBuffer indices = mesh.parts.indexBuffer().getDirectBuffer();
                    int indexBytes = mesh.parts.indexBuffer().getLength();
                    indexSegment = indexArena(resources).allocate(indexBytes);
                    indexArena(resources).write(indexSegment.byteOffset(), MemoryUtil.memAddress(indices), indexBytes);
                    uploaded += indexBytes;
                }

                SectionRenderDataStorage storage = region.createStorage(mesh.pass, this.configuration);
                storage.setMeshes(mesh.sectionIndex, vertexSegment, indexSegment, mesh.parts.ranges());
            } else if (mesh.sorted != null) {
                ByteBuffer indices = mesh.sorted.indexData().getDirectBuffer();
                int indexBytes = mesh.sorted.indexData().getLength();
                VulkanArena.Segment indexSegment = indexArena(resources).allocate(indexBytes);
                indexArena(resources).write(indexSegment.byteOffset(), MemoryUtil.memAddress(indices), indexBytes);
                uploaded += indexBytes;

                SectionRenderDataStorage storage = region.createStorage(mesh.pass, this.configuration);
                storage.replaceIndexBuffer(mesh.sectionIndex, indexSegment);
            }
        }

        int previousCookie = region.getPassSetUpdateCount();
        region.removeEmptyStorages();
        if (region.getPassSetUpdateCount() != previousCookie) {
            graphUpdateTrigger.run();
        }

        this.lastUploadedBytes = uploaded;
    }

    private RegionResources resourcesFor(RenderRegion region) {
        RegionResources resources = this.regionResources.get(region);
        if (resources == null) {
            resources = new RegionResources(this.context, this.commonVertexStride);
            this.regionResources.put(region, resources);
        }
        return resources;
    }

    private VulkanArena indexArena(RegionResources resources) {
        if (resources.indexArena == null) {
            resources.indexArena = new VulkanArena(this.context, 64 * 1024, VK_BUFFER_USAGE_INDEX_BUFFER_BIT, 4);
        }
        return resources.indexArena;
    }

    // ------------------------------------------------------------------ draw path

    private void recordRenderPass(MemoryStack stack,
                                  VulkanTerrainPipeline pipeline,
                                  ChunkRenderMatrices matrices,
                                  ChunkRenderListIterable renderLists,
                                  TerrainRenderPass pass,
                                  CameraTransform occlusionCamera,
                                  CameraTransform camera,
                                  boolean useBlockFaceCulling,
                                  ChunkPrimitiveType primitiveType) {
        ensureUniformCapacity(this.regionResources.size());
        // The descriptor set points at the atlas, the lightmap and the uniform buffer for this pass. It is rewritten
        // once per pass per frame, which is a few descriptor writes and removes any staleness after a re-upload.
        pipeline.bindTextures(this.atlasTexture, this.lightmapTexture, this.uniforms.buffer(), this.uniforms.size());

        this.frameTarget.recordAcquireBarriers(this.commandBuffer);

        long renderPass = pipeline.renderPass();
        VkRenderPassBeginInfo begin = VkRenderPassBeginInfo.calloc(stack).sType$Default()
                .renderPass(renderPass)
                .framebuffer(ensureFramebuffer(renderPass));
        begin.renderArea().offset().set(0, 0);
        begin.renderArea().extent().set(this.targetWidth, this.targetHeight);
        vkCmdBeginRenderPass(this.commandBuffer, begin, VK_SUBPASS_CONTENTS_INLINE);

        setViewportAndScissor(stack);
        pipeline.bindPipeline(this.commandBuffer);

        Iterator<ChunkRenderList> iterator = renderLists.iterator(pass.isReverseOrder());
        SectionRenderDataStorage.BatchCacheParams cacheParams =
                new SectionRenderDataStorage.BatchCacheParams(useBlockFaceCulling);
        boolean indexBound = false;

        while (iterator.hasNext()) {
            ChunkRenderList renderList = iterator.next();
            RenderRegion region = renderList.getRegion();
            RegionResources resources = this.regionResources.get(region);
            SectionRenderDataStorage storage = region.getStorage(pass);

            if (resources == null || storage == null) {
                continue;
            }

            CachedBatch cached = storage.getCachedMultiDrawBatch(cacheParams);
            if (cached == null || !cached.isValidFor(renderList.getSectionsWithGeometry(),
                    renderList.getSectionsWithGeometryCount(), occlusionCamera.intX, occlusionCamera.intY,
                    occlusionCamera.intZ)) {
                cached = BatchAssembler.createCachedBatch(region, storage, renderList, occlusionCamera, pass,
                        useBlockFaceCulling, null, (commands, ignored, ignoredPass) -> null);
                storage.storeCachedMultiDrawBatch(cacheParams, cached);
            }

            if (!(cached.getBatch() instanceof VulkanMultiDrawBatch) || cached.getBatch().isEmpty()) {
                continue;
            }

            VulkanMultiDrawBatch batch = (VulkanMultiDrawBatch) cached.getBatch();

            long uniformOffset = writeUniforms(region, pass, camera);
            pipeline.bindDescriptors(this.commandBuffer, uniformOffset);
            pipeline.pushMatrices(this.commandBuffer, vulkanProjection(matrices.projection()), matrices.modelView());

            bindVertexBuffer(stack, resources);
            if (!indexBound) {
                bindIndexBuffer(stack, this.sharedIndexBuffers.get(primitiveType));
                indexBound = true;
            }

            if (this.lastDrawCommands == 0 && command0Logged < 2) {
                command0Logged++;
                record("firstDraw region=" + region.getId() + " pass=" + pass.name()
                        + " stride=" + this.commonVertexStride
                        + " vertexArenaBytes=" + resources.vertexArena.deviceAllocatedMemoryL()
                        + " baseVertex=" + batch.baseVertex(0) + " elements=" + batch.elementCount(0)
                        + " indexBytes=" + batch.indexByteOffset(0)
                        + " batchSize=" + batch.size()
                        + " sharedIndexPrimitives=" + this.sharedIndexPrimitives.get(primitiveType));
            }
            for (int command = 0; command < batch.size(); command++) {
                long byteOffset = batch.indexByteOffset(command);
                int firstIndex;
                if (byteOffset == 0) {
                    firstIndex = 0;
                } else if (resources.indexArena != null) {
                    bindIndexBuffer(stack, resources.indexArena.buffer());
                    firstIndex = (int) (byteOffset / 4);
                } else {
                    continue;
                }
                vkCmdDrawIndexed(this.commandBuffer, batch.elementCount(command), 1, firstIndex,
                        batch.baseVertex(command), 0);
                this.lastDrawCommands++;
            }

            this.lastSectionsDrawn += renderList.getSectionsWithGeometryCount();

            if (resources.indexArena != null) {
                // The next region may use the shared index buffer again.
                bindIndexBuffer(stack, this.sharedIndexBuffers.get(primitiveType));
            }
        }

        vkCmdEndRenderPass(this.commandBuffer);
        this.frameTarget.recordReleaseBarriers(this.commandBuffer);
    }

    /**
     * {@code M * P} where {@code M} maps clip z from [-1, 1] to [0, 1] and leaves x, y and w alone. The Y axis is
     * handled by the negative-height viewport instead, so the OpenGL matrices and culling stay untouched.
     */
    private Matrix4fc vulkanProjection(Matrix4fc projection) {
        this.vulkanProjection.set(projection);
        // z: OpenGL clip depth [-1, 1] to Vulkan [0, 1]  ->  row 2 becomes 0.5 * (row 2 + row 3)
        float c0z = 0.5f * (projection.m02() + projection.m03());
        float c1z = 0.5f * (projection.m12() + projection.m13());
        float c2z = 0.5f * (projection.m22() + projection.m23());
        float c3z = 0.5f * (projection.m32() + projection.m33());
        // y: OpenGL clip +Y is up, Vulkan framebuffer +Y is down  ->  negate row 1
        this.vulkanProjection.m01(-projection.m01());
        this.vulkanProjection.m11(-projection.m11());
        this.vulkanProjection.m21(-projection.m21());
        this.vulkanProjection.m31(-projection.m31());
        this.vulkanProjection.m02(c0z);
        this.vulkanProjection.m12(c1z);
        this.vulkanProjection.m22(c2z);
        this.vulkanProjection.m32(c3z);
        return this.vulkanProjection;
    }

    /**
     * The framebuffer is created here rather than in the frame target because compatibility is defined by the
     * render pass the pipeline built, and one framebuffer serves every pass that is compatible with it.
     */
    private long ensureFramebuffer(long renderPass) {
        if (this.framebuffer != 0 && this.framebufferRenderPass == renderPass) {
            return this.framebuffer;
        }
        destroyFramebuffer();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer attachments = stack.mallocLong(2);
            attachments.put(0, this.frameTarget.colorView());
            attachments.put(1, this.frameTarget.depthView());
            org.lwjgl.vulkan.VkFramebufferCreateInfo create = org.lwjgl.vulkan.VkFramebufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .renderPass(renderPass)
                    .pAttachments(attachments)
                    .width(this.targetWidth)
                    .height(this.targetHeight)
                    .layers(1);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateFramebuffer(this.context.device(), create, null, handle), "vkCreateFramebuffer");
            this.framebuffer = handle.get(0);
            this.framebufferRenderPass = renderPass;
            return this.framebuffer;
        }
    }

    private void destroyFramebuffer() {
        if (this.framebuffer != 0) {
            vkDestroyFramebuffer(this.context.device(), this.framebuffer, null);
            this.framebuffer = 0;
            this.framebufferRenderPass = 0;
        }
    }

    private void bindVertexBuffer(MemoryStack stack, RegionResources resources) {
        LongBuffer buffers = stack.mallocLong(1);
        buffers.put(0, resources.vertexArena.buffer().buffer());
        LongBuffer offsets = stack.mallocLong(1);
        offsets.put(0, 0L);
        vkCmdBindVertexBuffers(this.commandBuffer, 0, buffers, offsets);
    }

    private void bindIndexBuffer(MemoryStack stack, VulkanBuffer buffer) {
        vkCmdBindIndexBuffer(this.commandBuffer, buffer.buffer(), 0L, VK_INDEX_TYPE_UINT32);
    }

    private void setViewportAndScissor(MemoryStack stack) {
        org.lwjgl.vulkan.VkViewport.Buffer viewport = org.lwjgl.vulkan.VkViewport.calloc(1, stack);
        // Vulkan's framebuffer Y axis points down and OpenGL's points up. The flip is applied to the projection
        // matrix rather than through a negative viewport height, so no driver extension behaviour is involved.
        viewport.get(0).x(0.0f).y(0.0f).width(this.targetWidth).height(this.targetHeight)
                .minDepth(0.0f).maxDepth(1.0f);
        vkCmdSetViewport(this.commandBuffer, 0, viewport);
        org.lwjgl.vulkan.VkRect2D.Buffer scissor = org.lwjgl.vulkan.VkRect2D.calloc(1, stack);
        scissor.get(0).offset().set(0, 0);
        scissor.get(0).extent().set(this.targetWidth, this.targetHeight);
        vkCmdSetScissor(this.commandBuffer, 0, scissor);
    }

    /** Writes one dynamic uniform block and returns its byte offset. */
    private long writeUniforms(RenderRegion region, TerrainRenderPass pass, CameraTransform camera) {
        long offset = this.uniformWriteCursor;
        this.uniformWriteCursor += this.uniformStride;
        if (this.uniformWriteCursor > this.uniforms.size()) {
            // A frame with more region draws than the buffer has blocks needs a bigger buffer: wrapping would let a
            // later draw overwrite a block that an earlier recorded draw still reads. Grow against the cursor, not
            // against the region count, or the buffer stops growing while the cursor keeps advancing.
            this.uniforms.ensureCapacity(this.uniformWriteCursor);
            this.uniformBlocks = (int) (this.uniforms.size() / this.uniformStride);
        }

        ByteBuffer block = this.uniforms.mapped();
        int base = (int) offset;

        block.putFloat(base + 0, (region.getOriginX() - camera.intX) - camera.fracX);
        block.putFloat(base + 4, (region.getOriginY() - camera.intY) - camera.fracY);
        block.putFloat(base + 8, (region.getOriginZ() - camera.intZ) - camera.fracZ);
        block.putFloat(base + 12, 0.0f);

        float[] fogColor = this.fogService != null ? this.fogService.getFogColor() : null;
        block.putFloat(base + 16, fogColor != null && fogColor.length > 0 ? fogColor[0] : 0.0f);
        block.putFloat(base + 20, fogColor != null && fogColor.length > 1 ? fogColor[1] : 0.0f);
        block.putFloat(base + 24, fogColor != null && fogColor.length > 2 ? fogColor[2] : 0.0f);
        block.putFloat(base + 28, fogColor != null && fogColor.length > 3 ? fogColor[3] : 0.0f);

        block.putFloat(base + 32, this.fogService != null ? this.fogService.getFogDensity() : 0.0f);
        block.putFloat(base + 36, this.fogService != null ? this.fogService.getFogStart() : 0.0f);
        block.putFloat(base + 40, this.fogService != null ? this.fogService.getFogEnd() : 0.0f);
        // Additive atlas LOD shift. The per-material bias already travels in the vertex, so parity needs 0 here.
        block.putFloat(base + 44, 0.0f);
        block.putInt(base + 48, fogMode());
        block.putInt(base + 52, this.fogService != null ? this.fogService.getFogShapeIndex() : FogService.FOG_SHAPE_SPHERICAL);
        block.putInt(base + 56, pass.supportsFragmentDiscard() ? 1 : 0);
        // Chunk fade is a separate feature that the Vulkan path does not port yet; 0 keeps the GL default look.
        block.putInt(base + 60, 0);
        block.putFloat(base + 64, 0.0f);
        block.putFloat(base + 68, 0.0f);
        block.putFloat(base + 72, 0.0f);
        block.putFloat(base + 76, 0.0f);

        return offset;
    }

    /**
     * The shader's fog mode selector: 0 = none, 1 = exp2, 2 = exp, 3 = smooth/linear. Derived from the GL fog
     * state, which is what the OpenGL backend's {@code GLStateManagerFogService} reads as well.
     */
    private static int fogMode() {
        if (!org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_FOG)) {
            return 0;
        }
        int mode = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_FOG_MODE);
        if (mode == org.lwjgl.opengl.GL11.GL_EXP2) {
            return 1;
        }
        if (mode == org.lwjgl.opengl.GL11.GL_EXP) {
            return 2;
        }
        return 3;
    }

    private static float[] regionTranslation(RenderRegion region, CameraTransform camera) {
        return new float[] {
                (region.getOriginX() - camera.intX) - camera.fracX,
                (region.getOriginY() - camera.intY) - camera.fracY,
                (region.getOriginZ() - camera.intZ) - camera.fracZ,
        };
    }

    private VulkanTerrainPipeline pipelineFor(TerrainRenderPass pass) {
        VulkanTerrainPipeline pipeline = this.pipelines.get(pass);
        if (pipeline != null) {
            return pipeline;
        }
        try {
            pipeline = VulkanTerrainPipeline.create(this.context, pass, !pass.isSorted());
            this.pipelines.put(pass, pipeline);
            record("pipeline for " + pass.name() + " created [" + pipeline.limitsSummary() + "]");
            if (pipeline.limitsWarning() != null) {
                record(pipeline.limitsWarning());
            }
            return pipeline;
        } catch (RuntimeException | LinkageError error) {
            record("pipeline for " + pass.name() + " failed: " + describe(error));
            this.pipelines.put(pass, null);
            return null;
        }
    }

    // ------------------------------------------------------------------ frame target

    private boolean ensureFrameTarget() {
        int width = currentDrawableWidth();
        int height = currentDrawableHeight();
        if (width <= 0 || height <= 0) {
            record("drawable size unavailable");
            return false;
        }
        if (this.frameTarget != null && this.frameTarget.matches(width, height)) {
            return true;
        }
        destroyFramebuffer();
        if (this.frameTarget != null) {
            this.frameTarget.delete();
            this.frameTarget = null;
        }
        try {
            this.frameTarget = VulkanFrameTarget.create(this.context.instance(), this.context.physicalDevice(),
                    this.context.device(), this.context.queue(), this.context.queueFamilyIndex(), width, height);
            this.targetWidth = width;
            this.targetHeight = height;
            return true;
        } catch (RuntimeException | LinkageError error) {
            record("frame target " + width + "x" + height + " failed: " + describe(error));
            return false;
        }
    }

    private static int currentDrawableWidth() {
        int[] viewport = currentViewport();
        return Math.max(0, viewport[2]);
    }

    private static int currentDrawableHeight() {
        int[] viewport = currentViewport();
        return Math.max(0, viewport[3]);
    }

    private static int[] currentViewport() {
        IntBuffer viewport = org.lwjgl.BufferUtils.createIntBuffer(4);
        org.lwjgl.opengl.GL11C.glGetIntegerv(org.lwjgl.opengl.GL11C.GL_VIEWPORT, viewport);
        return new int[] { viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3) };
    }

    private void refreshTextures() {
        int atlasId = boundTextureId(0);
        int lightmapId = boundTextureId(1);

        if (atlasId != 0 && (this.atlasTexture == null || atlasId != this.atlasGlId || this.atlasRefreshCountdown <= 0)) {
            try {
                if (this.atlasTexture == null) {
                    int[] size = textureSize(atlasId);
                    this.atlasTexture = VulkanGlTexture.create(this.context, size[0], size[1]);
                }
                this.atlasTexture.uploadFromGlTexture(atlasId, org.lwjgl.opengl.GL11C.GL_TEXTURE_2D);
                int[] atlasSize = textureSize(atlasId);
                record("block atlas: gl texture " + atlasId + " " + atlasSize[0] + "x" + atlasSize[1]);
                this.atlasGlId = atlasId;
                this.atlasRefreshCountdown = 400;
            } catch (RuntimeException | LinkageError error) {
                record("block atlas upload failed: " + describe(error));
                this.atlasRefreshCountdown = 400;
            }
        }
        this.atlasRefreshCountdown--;

        if (lightmapId != 0 && (this.lightmapTexture == null || lightmapId != this.lightmapGlId || this.lightmapRefreshCountdown <= 0)) {
            try {
                if (this.lightmapTexture == null) {
                    int[] size = textureSize(lightmapId);
                    this.lightmapTexture = VulkanGlTexture.create(this.context, size[0], size[1]);
                }
                this.lightmapTexture.uploadFromGlTexture(lightmapId, org.lwjgl.opengl.GL11C.GL_TEXTURE_2D);
                int[] lightmapSize = textureSize(lightmapId);
                record("lightmap: gl texture " + lightmapId + " " + lightmapSize[0] + "x" + lightmapSize[1]);
                this.lightmapGlId = lightmapId;
                this.lightmapRefreshCountdown = 20;
            } catch (RuntimeException | LinkageError error) {
                record("lightmap upload failed: " + describe(error));
                this.lightmapRefreshCountdown = 20;
            }
        }
        this.lightmapRefreshCountdown--;
    }

    private static int boundTextureId(int unit) {
        int previous = org.lwjgl.opengl.GL11C.glGetInteger(org.lwjgl.opengl.GL13C.GL_ACTIVE_TEXTURE);
        org.lwjgl.opengl.GL13C.glActiveTexture(org.lwjgl.opengl.GL13C.GL_TEXTURE0 + unit);
        int texture = org.lwjgl.opengl.GL11C.glGetInteger(org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_2D);
        org.lwjgl.opengl.GL13C.glActiveTexture(previous);
        return texture;
    }

    private static int[] textureSize(int texture) {
        int width = org.lwjgl.opengl.GL45C.glGetTextureLevelParameteri(texture, 0, org.lwjgl.opengl.GL11C.GL_TEXTURE_WIDTH);
        int height = org.lwjgl.opengl.GL45C.glGetTextureLevelParameteri(texture, 0, org.lwjgl.opengl.GL11C.GL_TEXTURE_HEIGHT);
        return new int[] { Math.max(1, width), Math.max(1, height) };
    }

    // ------------------------------------------------------------------ command resources

    private void createCommandResources() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack).sType$Default()
                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                    .queueFamilyIndex(this.context.queueFamilyIndex());
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateCommandPool(this.context.device(), poolInfo, null, handle), "vkCreateCommandPool");
            this.commandPool = handle.get(0);

            VkCommandBufferAllocateInfo allocate = VkCommandBufferAllocateInfo.calloc(stack).sType$Default()
                    .commandPool(this.commandPool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1);
            org.lwjgl.PointerBuffer buffers = stack.mallocPointer(1);
            check(vkAllocateCommandBuffers(this.context.device(), allocate, buffers), "vkAllocateCommandBuffers");
            this.commandBuffer = new VkCommandBuffer(buffers.get(0), this.context.device());

            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack).sType$Default();
            check(vkCreateFence(this.context.device(), fenceInfo, null, handle), "vkCreateFence");
            this.fence = handle.get(0);
        }
    }

    private void destroyCommandResources() {
        if (this.fence != 0) {
            vkDestroyFence(this.context.device(), this.fence, null);
            this.fence = 0;
        }
        if (this.commandPool != 0) {
            vkDestroyCommandPool(this.context.device(), this.commandPool, null);
            this.commandPool = 0;
            this.commandBuffer = null;
        }
    }

    private void beginCommands() {
        vkResetCommandBuffer(this.commandBuffer, VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferBeginInfo begin = VkCommandBufferBeginInfo.calloc(stack).sType$Default()
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            check(vkBeginCommandBuffer(this.commandBuffer, begin), "vkBeginCommandBuffer");
        }
        this.uniformWriteCursor = 0;
    }

    private void submitCommands() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            check(vkEndCommandBuffer(this.commandBuffer), "vkEndCommandBuffer");
            vkResetFences(this.context.device(), this.fence);
            VkSubmitInfo submit = VkSubmitInfo.calloc(stack).sType$Default();
            LongBuffer waitSemaphores = stack.mallocLong(1);
            waitSemaphores.put(0, this.frameTarget.waitSemaphore());
            IntBuffer waitStages = stack.mallocInt(1);
            waitStages.put(0, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT);
            LongBuffer signalSemaphores = stack.mallocLong(1);
            signalSemaphores.put(0, this.frameTarget.doneSemaphore());
            org.lwjgl.PointerBuffer commandBuffers = stack.mallocPointer(1);
            commandBuffers.put(0, this.commandBuffer.address());
            // LWJGL derives the semaphore counts from the buffers, so only the pointers are set here.
            submit.pWaitSemaphores(waitSemaphores).pWaitDstStageMask(waitStages)
                    .pCommandBuffers(commandBuffers)
                    .pSignalSemaphores(signalSemaphores);
            check(vkQueueSubmit(this.context.queue(), submit, this.fence), "vkQueueSubmit");
        }
    }

    private void waitForIdle() {
        if (this.fence != 0) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                LongBuffer fences = stack.mallocLong(1);
                fences.put(0, this.fence);
                vkWaitForFences(this.context.device(), fences, true, Long.MAX_VALUE);
            }
        }
        vkQueueWaitIdle(this.context.queue());
    }

    // ------------------------------------------------------------------ shared resources

    /**
     * The shared index buffer holds the repeating primitive pattern the OpenGL backend also uses for unsorted
     * passes. The pattern is generated by the pass's own {@link ChunkPrimitiveType}, so the Vulkan and OpenGL
     * paths cannot drift apart on element order or count.
     */
    private void ensureSharedIndexBuffer(ChunkPrimitiveType primitiveType) {
        int maxVertices = 0;
        for (RegionResources resources : this.regionResources.values()) {
            long bytes = resources.vertexArena.deviceAllocatedMemoryL();
            maxVertices = Math.max(maxVertices, (int) (bytes / this.commonVertexStride));
        }
        int primitives = (maxVertices / primitiveType.getVerticesPerPrimitive()) + 1;
        Integer existing = this.sharedIndexPrimitives.get(primitiveType);
        if (this.sharedIndexBuffers.containsKey(primitiveType) && existing != null && existing >= primitives) {
            return;
        }
        int capacity = Math.max(primitives, 1024);
        int bytes = primitiveType.getIndexBufferSize(capacity);
        ByteBuffer data = MemoryUtil.memAlloc(bytes);
        try {
            primitiveType.generateSimpleIndexBuffer(data, capacity);
            VulkanBuffer previous = this.sharedIndexBuffers.remove(primitiveType);
            if (previous != null) {
                previous.delete();
            }
            VulkanBuffer buffer = new VulkanBuffer(this.context, bytes, VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
            buffer.write(0, MemoryUtil.memAddress(data), bytes);
            this.sharedIndexBuffers.put(primitiveType, buffer);
            this.sharedIndexPrimitives.put(primitiveType, capacity);
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    private void ensureUniformCapacity(int regions) {
        int blocks = Math.max(16, regions + 1);
        long required = blocks * this.uniformStride;
        if (this.uniforms != null && this.uniforms.size() >= required) {
            return;
        }
        if (this.uniforms == null) {
            this.uniforms = new VulkanUniformBuffer(this.context, required);
        } else {
            this.uniforms.ensureCapacity(required);
        }
        this.uniformBlocks = blocks;
    }

    private int alignmentFromDevice() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkPhysicalDeviceProperties properties = org.lwjgl.vulkan.VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(this.context.physicalDevice(), properties);
            long alignment = properties.limits().minUniformBufferOffsetAlignment();
            return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, alignment));
        }
    }

    private static long align(long value, int alignment) {
        long mask = alignment - 1;
        return (value + mask) & ~mask;
    }

    private void record(String diagnostic) {
        this.diagnostics.add(diagnostic);
    }

    private static String describe(Throwable error) {
        return error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
    }

    private static void check(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(operation + " returned " + result);
        }
    }

    /** Per-region device memory. */
    private static final class RegionResources {
        final VulkanArena vertexArena;
        VulkanArena indexArena;

        RegionResources(VulkanContext context, int vertexStride) {
            this.vertexArena = new VulkanArena(context, 512 * 1024, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, vertexStride);
        }

        int bufferCount() {
            return 1 + (this.indexArena != null ? 1 : 0);
        }

        void delete() {
            this.vertexArena.delete();
            if (this.indexArena != null) {
                this.indexArena.delete();
                this.indexArena = null;
            }
        }
    }

    private static final class PendingMesh {
        final int sectionIndex;
        final TerrainRenderPass pass;
        final BuiltSectionMeshParts parts;
        final ChunkSortOutput.SortedMesh sorted;

        PendingMesh(int sectionIndex, TerrainRenderPass pass, BuiltSectionMeshParts parts, ChunkSortOutput.SortedMesh sorted) {
            this.sectionIndex = sectionIndex;
            this.pass = pass;
            this.parts = parts;
            this.sorted = sorted;
        }
    }
}
