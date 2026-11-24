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
 * owners.
 * <p>
 * M-5 (superset proof §7): the VAO attribute records themselves - the stub's
 * {@code VaoAttrib} type, its per-VAO record map and the bound VAO's cached array - are
 * owned here too, with the stub's recording semantics unchanged. The earlier
 * (vao, attrib index) {@code Pointer} map this class carried for the same facts had no
 * callers and is removed: two divergent records of one VAO's attributes is exactly the
 * split ownership GLSM exists to end. The records are per VAO name, created whole
 * (sixteen attributes at the GL defaults) on first use, and the bound VAO's array is
 * cached here; the stub keeps only the bound VAO's name (S-10, proof M-8) and asks
 * {@link #boundAttribs(int)} for the array.
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
        /** The legacy vertex pointer's normalised/integer spellings (the stub record carries both). */
        public boolean normalized;
        public boolean integer;
    }

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
     * Moved verbatim from CeltiumGlStub (M-5): field set, defaults and package-private
     * access are unchanged, so every existing reader/writer in this package sees the
     * same record it saw in the stub.
     */
    public static final class VaoAttrib {
        boolean enabled;
        boolean pointerSet;
        boolean normalized;
        boolean integer;
        /** GL's default attribute: size 4, float, tightly packed, null pointer, no buffer. */
        int size = 4;
        int type = 0x1406; // GL_FLOAT
        int stride;
        long offset;
        int buffer;
    }

    private static final GlsmClientArrayState CURRENT = new GlsmClientArrayState();

    public static GlsmClientArrayState current() { return CURRENT; }

    /** Client arrays are per generic client state, keyed by the array enum (GL_VERTEX_ARRAY &c.). */
    private final Map<Integer, Pointer> clientPointers = new HashMap<Integer, Pointer>();
    /** Per-VAO generic attribute records, keyed by the VAO name; {@link #currentAttribs} is the bound VAO's array. */
    private final it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<VaoAttrib[]> vaoAttribRecords =
            new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<VaoAttrib[]>();
    /** The bound VAO's attribute records; null until first use, when {@link #boundAttribs(int)} binds it. */
    private VaoAttrib[] currentAttribs;
    /** The VAO name {@link #currentAttribs} belongs to; meaningful only while it is non-null. */
    private int currentAttribsVao;
    private int boundRenderbuffer;
    private final Map<Integer, int[]> renderbufferSizes = new HashMap<Integer, int[]>();
    private long version;

    private GlsmClientArrayState() {
    }

    private static Pointer mutable(Map<?, Pointer> map, Object key, boolean create) {
        Pointer pointer = map.get(key);
        if (pointer == null && create) {
            pointer = new Pointer();
            // Map is the typed map above; the cast is safe by construction.
            @SuppressWarnings("unchecked")
            Map<Object, Pointer> typed = (Map<Object, Pointer>) map;
            typed.put(key, pointer);
        }
        return pointer;
    }

    public Pointer clientPointer(int array, boolean create) {
        return mutable(this.clientPointers, array, create);
    }

    public void setClientPointer(int array, int size, int type, int stride, long offset, int buffer) {
        Pointer pointer = this.clientPointer(array, true);
        pointer.size = size; pointer.type = type; pointer.stride = stride;
        pointer.offset = offset; pointer.buffer = buffer;
        this.version++;
    }

    /** {@return the VAO's attribute records}, creating the VAO's array on first use. */
    public VaoAttrib[] attribsFor(int vao) {
        VaoAttrib[] attribs = this.vaoAttribRecords.get(vao);
        if (attribs == null) {
            attribs = new VaoAttrib[MAX_VERTEX_ATTRIBS];
            for (int i = 0; i < attribs.length; i++) {
                attribs[i] = new VaoAttrib();
            }
            this.vaoAttribRecords.put(vao, attribs);
            this.version++;
        }
        return attribs;
    }

    /** Makes the VAO's records the live (bound) array, as a glBindVertexArray swap does. */
    public void bindAttribs(int vao) {
        this.currentAttribs = attribsFor(vao);
        this.currentAttribsVao = vao;
    }

    /**
     * {@return the bound VAO's attribute records}, binding them on first use.
     * The stub passes its bound VAO name (still the stub's own record, proof M-8);
     * a cached array for a different name is never handed back.
     */
    public VaoAttrib[] boundAttribs(int boundVao) {
        if (this.currentAttribs == null || this.currentAttribsVao != boundVao) {
            bindAttribs(boundVao);
        }
        return this.currentAttribs;
    }

    /** Drops a VAO's records (glDeleteVertexArrays); a dropped bound VAO's array is re-read on next use. */
    public void removeAttribs(int vao) {
        this.vaoAttribRecords.remove(vao);
        if (this.currentAttribs != null && this.currentAttribsVao == vao) {
            this.currentAttribs = null;
        }
        this.version++;
    }

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
