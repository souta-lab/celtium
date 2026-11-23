package org.soutalab.celtium.vulkan.gl;

import java.util.HashMap;
import java.util.Map;

/**
 * GLSM stage 3, GAP-ST-8 (census S-12): the owner of the client-array pointer records,
 * the per-VAO attribute records, and the renderbuffer binding/dimensions.
 * <p>
 * A pointer record is the full gl*Pointer tuple (size, type, stride, offset-or-address,
 * plus the buffer name the VBO route bound when the pointer was set) - the draw path and
 * the readable-range decode both read the tuple, so it is stored whole, never split across
 * owners. VAO records are keyed by (vao, attrib index) exactly as the stub keys them.
 */
public final class GlsmClientArrayState {
    /** One gl*Pointer tuple. */
    public static final class Pointer {
        public int size;
        public int type;
        public int stride;
        public long offset;
        public int buffer;
        public boolean enabled;
    }

    private static final GlsmClientArrayState CURRENT = new GlsmClientArrayState();

    public static GlsmClientArrayState current() { return CURRENT; }

    /** Client arrays are per generic client state, keyed by the array enum (GL_VERTEX_ARRAY &c.). */
    private final Map<Integer, Pointer> clientPointers = new HashMap<Integer, Pointer>();
    /** VAO attribute records, keyed by ((long) vao << 32) | attribIndex. */
    private final Map<Long, Pointer> vaoAttribs = new HashMap<Long, Pointer>();
    private int boundRenderbuffer;
    private final Map<Integer, int[]> renderbufferSizes = new HashMap<Integer, int[]>();
    private long version;

    private GlsmClientArrayState() {
    }

    private static Pointer mutable(Map<?, Pointer> map, Object key, boolean create) {
        Pointer pointer = map.get(key);
        if (pointer == null && create) {
            pointer = new Pointer();
            // Map is one of the two typed maps above; the cast is safe by construction.
            @SuppressWarnings("unchecked")
            Map<Object, Pointer> typed = (Map<Object, Pointer>) map;
            typed.put(key, pointer);
        }
        return pointer;
    }

    public Pointer clientPointer(int array, boolean create) {
        return mutable(this.clientPointers, array, create);
    }

    public Pointer vaoAttrib(int vao, int index, boolean create) {
        return mutable(this.vaoAttribs, Long.valueOf(((long) vao << 32) | (index & 0xFFFFFFFFL)), create);
    }

    public void setClientPointer(int array, int size, int type, int stride, long offset, int buffer) {
        Pointer pointer = this.clientPointer(array, true);
        pointer.size = size; pointer.type = type; pointer.stride = stride;
        pointer.offset = offset; pointer.buffer = buffer;
        this.version++;
    }

    public void setVaoAttrib(int vao, int index, int size, int type, int stride, long offset, int buffer) {
        Pointer pointer = this.vaoAttrib(vao, index, true);
        pointer.size = size; pointer.type = type; pointer.stride = stride;
        pointer.offset = offset; pointer.buffer = buffer;
        this.version++;
    }

    public void bindRenderbuffer(int name) { this.boundRenderbuffer = name; this.version++; }

    public int boundRenderbuffer() { return this.boundRenderbuffer; }

    public void renderbufferStorage(int name, int width, int height) {
        this.renderbufferSizes.put(name, new int[] {width, height});
        this.version++;
    }

    /** @return {width, height} for the name, or null when it never got storage. */
    public int[] renderbufferSize(int name) { return this.renderbufferSizes.get(name); }

    public long version() { return this.version; }
}
