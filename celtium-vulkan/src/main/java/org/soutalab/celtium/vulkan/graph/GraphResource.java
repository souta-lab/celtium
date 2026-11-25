package org.soutalab.celtium.vulkan.graph;

/**
 * A resource the frame graph reasons about: an image or buffer, either <em>imported</em> (owned
 * outside the graph - the swapchain image, the terrain slab, the staging ring) or
 * <em>transient</em> (owned by the graph, allocated from the transient pool and eligible for the
 * aliasing the compiler computes from lifetimes, design section 2.3).
 *
 * <p>The graph never sees a Vulkan handle. An imported image carries the layout it is in when
 * the frame starts (the swapchain image starts a frame in {@link ImageLayout#PRESENT_SRC} in
 * steady state, {@link ImageLayout#UNDEFINED} on its first frame); the executor binds the real
 * handle to the resource when it records the pass that uses it.
 */
public final class GraphResource {

    public enum Kind { IMAGE, BUFFER }

    private final String name;
    private final Kind kind;
    private final boolean transientResource;
    private final int width;
    private final int height;
    private final int format;
    private final long sizeBytes;
    private ImageLayout initialLayout = ImageLayout.UNDEFINED;

    private GraphResource(String name, Kind kind, boolean transientResource,
                          int width, int height, int format, long sizeBytes) {
        this.name = name;
        this.kind = kind;
        this.transientResource = transientResource;
        this.width = width;
        this.height = height;
        this.format = format;
        this.sizeBytes = sizeBytes;
    }

    public static GraphResource importedImage(String name, int width, int height, int format) {
        return new GraphResource(name, Kind.IMAGE, false, width, height, format, 0);
    }

    public static GraphResource transientImage(String name, int width, int height, int format) {
        return new GraphResource(name, Kind.IMAGE, true, width, height, format, 0);
    }

    public static GraphResource importedBuffer(String name, long sizeBytes) {
        return new GraphResource(name, Kind.BUFFER, false, 0, 0, 0, sizeBytes);
    }

    public static GraphResource transientBuffer(String name, long sizeBytes) {
        return new GraphResource(name, Kind.BUFFER, true, 0, 0, 0, sizeBytes);
    }

    /** The layout an imported image is in at frame start. Transients always start UNDEFINED. */
    public GraphResource withInitialLayout(ImageLayout layout) {
        this.initialLayout = layout;
        return this;
    }

    public String name() {
        return this.name;
    }

    public Kind kind() {
        return this.kind;
    }

    public boolean isTransient() {
        return this.transientResource;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    /** VkFormat value for images; 0 for buffers. */
    public int format() {
        return this.format;
    }

    public long sizeBytes() {
        return this.sizeBytes;
    }

    public ImageLayout initialLayout() {
        return this.initialLayout;
    }

    /**
     * Whether two transients may share memory: same kind and, for images, the same shape and
     * format; for buffers, the same size. Conservative on purpose - a missed alias costs memory,
     * a wrong one costs corruption.
     */
    boolean aliasCompatible(GraphResource other) {
        if (this.kind != other.kind) {
            return false;
        }
        if (this.kind == Kind.IMAGE) {
            return this.width == other.width && this.height == other.height
                    && this.format == other.format;
        }
        return this.sizeBytes == other.sizeBytes;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
