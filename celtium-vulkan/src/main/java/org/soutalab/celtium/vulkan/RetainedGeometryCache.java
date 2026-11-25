package org.soutalab.celtium.vulkan;

import java.util.IdentityHashMap;
import java.util.Map;

/** Device-bound ownership of retained geometry. No per-frame vertex copy or GL buffer lookup. */
final class RetainedGeometryCache implements AutoCloseable {
    private final VulkanContext context;
    private final Map<NativeGeometryBuffer, it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<LeasedResource<VulkanDynamicMesh>>> entries =
            new IdentityHashMap<>();
    private long builds;
    private long acquires;
    private long reuses;
    private long uploadedBytes;
    private final long[] formatsBuilt = new long[VulkanImmediateFormats.COUNT];
    private boolean closed;

    RetainedGeometryCache(VulkanContext context) {
        this.context = context;
    }

    LeasedResource.Lease<VulkanDynamicMesh> acquire(NativeGeometryBuffer source, int mode) {
        if (this.closed) throw new IllegalStateException("retained geometry device cache is closed");
        it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<LeasedResource<VulkanDynamicMesh>> modes = this.entries.get(source);
        LeasedResource<VulkanDynamicMesh> resource = modes == null ? null : modes.get(mode);
        if (resource == null) {
            VulkanDynamicMesh mesh = new VulkanDynamicMesh(this.context);
            try {
                // A retained mesh is built from exactly one mode (the cache key), so a quad source
                // takes the mesh's shared-pattern form and writes no indices of its own; every
                // other mode streams its expansion (Stage 5 slice S5-0).
                boolean built = mode == org.lwjgl.opengl.GL11.GL_QUADS
                        ? mesh.copyVerticesQuads(source.address(), source.stride(), source.vertexCount())
                        : mesh.copyVerticesExpanded(source.address(), source.stride(), source.vertexCount(), mode);
                if (!built) {
                    throw new IllegalArgumentException("retained geometry has no drawable vertices or indices");
                }
            } catch (RuntimeException | Error error) {
                mesh.discard();
                throw error;
            }
            resource = new LeasedResource<>(mesh, VulkanDynamicMesh::delete);
            if (modes == null) {
                modes = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>();
                this.entries.put(source, modes);
                source.onInvalidate(this, () -> invalidate(source));
            }
            modes.put(mode, resource);
            this.builds++;
            this.formatsBuilt[source.formatId()]++;
            this.uploadedBytes += (long) source.stride() * source.vertexCount()
                    + (long) TopologyIndexEmitter.indexCount(mode, source.vertexCount()) * 4L;
        } else {
            this.reuses++;
        }
        this.acquires++;
        return resource.acquire();
    }

    private void invalidate(NativeGeometryBuffer source) {
        it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<LeasedResource<VulkanDynamicMesh>> modes =
                this.entries.remove(source);
        source.detach(this);
        if (modes != null) {
            for (LeasedResource<VulkanDynamicMesh> resource : modes.values()) {
                // Old generations may still be queued, recorded, or in flight. Only their owner reference ends here.
                resource.close();
            }
        }
    }

    String summary() {
        return "retainedBuilds=" + this.builds + " retainedAcquires=" + this.acquires
                + " retainedReuses=" + this.reuses + " retainedUploadBytes=" + this.uploadedBytes
                + " retainedSources=" + this.entries.size()
                + " retainedFormatsBuilt=" + java.util.Arrays.toString(this.formatsBuilt);
    }

    @Override
    public void close() {
        this.closed = true;
        for (NativeGeometryBuffer source : this.entries.keySet().toArray(new NativeGeometryBuffer[0])) {
            invalidate(source);
        }
    }
}
