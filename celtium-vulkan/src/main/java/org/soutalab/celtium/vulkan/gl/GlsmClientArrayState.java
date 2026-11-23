package org.soutalab.celtium.vulkan.gl;

import java.util.HashMap;
import java.util.Map;

/**
 * GLSM stage 3, GAP-ST-8 (census S-12): the owner of the client-array pointer records,
 * the per-VAO attribute records, the bound vertex array name, and the renderbuffer
 * binding/dimensions.
 * <p>
 * A pointer record is the full gl*Pointer tuple (size, type, stride, offset-or-address,
 * plus the buffer name the VBO route bound when the pointer was set) - the draw path and
 * the readable-range decode both read the tuple, so it is stored whole, never split across
 * owners. VAO records are keyed by (vao, attrib index) exactly as the stub keys them.
 */
public final class GlsmClientArrayState {
    /** The generic vertex attributes one VAO record carries; GL guarantees at least sixteen. */
    public static final int MAX_VERTEX_ATTRIBS = 16;

    /**
     * One generic vertex attribute's recorded state: its enable, its pointer, and the array buffer the pointer was
     * set against. GL keeps all three per attribute inside the VAO. The stub used to keep a single pointer record
     * for attribute 0 alone - no enables, no buffer, and no record at all for the other attributes - which is why a
     * VAO-carried draw decoded from whichever attribute had been set last and from enables some other draw had left
     * behind: Demonica's full-screen quad (three attributes set once at setup, its VBO unbound again before the
     * draw) was refused on every frame for exactly that (offer-path diagnosis, 2026-11-20).
     * <p>
     * Moved here from CeltiumGlStub (GLSM stage 3, GAP-ST-8 remainder): the per-VAO arrays of these records are
     * owned by {@link GlsmClientArrayState#attribsFor(int)} below; the keyed {@link Pointer} form in this
     * same owner remains for the single-pointer routes that were rewired first.
     */
    public static final class VaoAttrib {
        public boolean enabled;
        public boolean pointerSet;
        public boolean normalized;
        public boolean integer;
        /** GL's default attribute: size 4, float, tightly packed, null pointer, no buffer. */
        public int size = 4;
        public int type = 0x1406; // GL_FLOAT
        public int stride;
        public long offset;
        public int buffer;
    }

    /** One gl*Pointer tuple. */
    public static final class Pointer {
        public int size;
        public int type;
        public int stride;
        public long offset;
        public int buffer;
        public boolean enabled;
        /** The legacy vertex pointer's normalised/integer spellings (the stub record carries both). */
        public boolean normalized;
        public boolean integer;
    }

    private static final GlsmClientArrayState CURRENT = new GlsmClientArrayState();

    public static GlsmClientArrayState current() { return CURRENT; }

    /** Client arrays are per generic client state, keyed by the array enum (GL_VERTEX_ARRAY &c.). */
    private final Map<Integer, Pointer> clientPointers = new HashMap<Integer, Pointer>();
    /** VAO attribute records, keyed by ((long) vao << 32) | attribIndex. */
    private final Map<Long, Pointer> vaoAttribs = new HashMap<Long, Pointer>();
    /** Per-VAO generic attribute record arrays, keyed by the VAO name; created whole on first use. */
    private final Map<Integer, VaoAttrib[]> vaoAttribRecords = new HashMap<Integer, VaoAttrib[]>();
    /** The bound vertex array name; name 0 is the default VAO. Volatile as the stub field it replaces was. */
    private volatile int boundVertexArray;
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

    /**
     * {@return the VAO's generic attribute records}, creating the VAO's array - all
     * {@link #MAX_VERTEX_ATTRIBS} records at their GL defaults - on first use. The array is
     * written in place while the VAO is bound; binding another VAO swaps which array is live,
     * so a caller that sets its attributes once and rebinds its VAO reads its own state back.
     * Name 0 is the default VAO and keeps its own records too.
     */
    public VaoAttrib[] attribsFor(int vao) {
        VaoAttrib[] attribs = this.vaoAttribRecords.get(Integer.valueOf(vao));
        if (attribs == null) {
            attribs = new VaoAttrib[MAX_VERTEX_ATTRIBS];
            for (int i = 0; i < attribs.length; i++) {
                attribs[i] = new VaoAttrib();
            }
            this.vaoAttribRecords.put(Integer.valueOf(vao), attribs);
            this.version++;
        }
        return attribs;
    }

    /** Drops a deleted VAO's attribute records; the name is never reissued, so nothing inherits them. */
    public void forgetVaoAttribs(int vao) {
        if (this.vaoAttribRecords.remove(Integer.valueOf(vao)) != null) {
            this.version++;
        }
    }

    /**
     * Records the vertex array {@code glBindVertexArray} bound. The attribute records are
     * keyed by this name ({@link #attribsFor(int)}), so naming the VAO here is the whole
     * swap: the array the name keys becomes the live one. Deleting the bound VAO rebinds 0
     * through this same entry, exactly as the stub's field write did.
     * <p>
     * Moved here from CeltiumGlStub (GLSM stage 3, GAP-ST-8 remainder): the stub's two bind
     * routes and its delete path write through this method, and the GL_VERTEX_ARRAY_BINDING
     * query reads {@link #boundVertexArray()} through GlsmQueryFacade.
     */
    public void bindVertexArray(int name) { this.boundVertexArray = name; this.version++; }

    public int boundVertexArray() { return this.boundVertexArray; }

    public void bindRenderbuffer(int name) { this.boundRenderbuffer = name; this.version++; }

    public int boundRenderbuffer() { return this.boundRenderbuffer; }

    public void removeRenderbufferSize(int name) { this.renderbufferSizes.remove(name); this.version++; }

    public void renderbufferStorage(int name, int width, int height) {
        this.renderbufferSizes.put(name, new int[] {width, height});
        this.version++;
    }

    /** @return {width, height} for the name, or null when it never got storage. */
    public int[] renderbufferSize(int name) { return this.renderbufferSizes.get(name); }

    public long version() { return this.version; }
}
