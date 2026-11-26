package org.soutalab.celtium.vulkan.terrain;

import org.embeddedt.embeddium.impl.common.util.NativeBuffer;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * One executor's deferred registration queue: sections the upload budget
 * refused this frame, waiting for a later frame head to admit them (T3
 * full-sharing design, section 4.2 — the payload ownership half of entry
 * condition (3)).
 * <p>
 * The queue owns its payloads. {@link #offer} copies the section's bytes
 * into queue-owned {@link NativeBuffer}s at the moment of deferral
 * (copy-on-defer), so the caller's contract — "once register has been
 * called, the built buffers may be freed" — holds for every outcome: the
 * embeddium caller frees its build output unconditionally after the upload
 * call, and a queue that merely held the borrowed buffers read freed
 * memory on drain (the live use-after-free this class removes). The copy
 * costs only on deferral, per section, and is bounded by the section size.
 * <p>
 * Entries carry their key and the generation they were offered under, and
 * the queue's discipline is fixed: a fresh offer of a key replaces the
 * queued entry (the newest build wins), {@link #cancel} removes a key
 * outright (the host calls it for every engine on unregister, so an
 * unregistered section can never resurrect through a drain), and a head
 * entry whose key has meanwhile become registered is dropped by
 * {@link #dropStaleHead} as a counted stale-drop instead of reaching the
 * store's duplicate-registration exception.
 */
public final class DeferredSectionQueue {

    /** One queued section: its owned-copy build plus the buffers that own its bytes. */
    public static final class Entry {
        public final TerrainSectionStore.Key key;
        public final long generation;
        private final AsyncSectionMesher.BuiltSectionA built;
        private final NativeBuffer ownedVertices;
        private final NativeBuffer ownedSorted;
        private boolean freed;

        private Entry(TerrainSectionStore.Key key, long generation,
                      AsyncSectionMesher.BuiltSectionA built,
                      NativeBuffer ownedVertices, NativeBuffer ownedSorted) {
            this.key = key;
            this.generation = generation;
            this.built = built;
            this.ownedVertices = ownedVertices;
            this.ownedSorted = ownedSorted;
        }

        /** The owned-copy build to upload on drain; invalid after {@link #freeCopies()}. */
        public AsyncSectionMesher.BuiltSectionA built() {
            return this.built;
        }

        /** Bytes the upload budget charges for this entry. */
        public long slabBytes() {
            return this.built.slabBytes();
        }

        /** Frees the owned buffers. Idempotent; the entry is unusable afterwards. */
        public void freeCopies() {
            if (this.freed) {
                return;
            }
            this.freed = true;
            this.ownedVertices.free();
            if (this.ownedSorted != null) {
                this.ownedSorted.free();
            }
        }
    }

    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private final Map<TerrainSectionStore.Key, Entry> byKey = new HashMap<>();
    private long staleDrops;

    /**
     * Queues one section for later admission, copying its payload into
     * queue-owned memory first. An existing entry for the same key is
     * replaced (its copies freed): the newest build wins.
     *
     * @return the queued entry
     */
    public Entry offer(AsyncSectionMesher.BuiltSectionA built, long generation) {
        TerrainSectionStore.Key key = new TerrainSectionStore.Key(
                built.sectionX, built.sectionY, built.sectionZ, built.passOrdinal);
        ByteBuffer vertexView = built.vertexData.duplicate();
        vertexView.clear();
        vertexView.limit(built.vertexBytes);
        NativeBuffer ownedVertices = NativeBuffer.copy(vertexView);
        NativeBuffer ownedSorted = null;
        AsyncSectionMesher.BuiltSectionA copy;
        try {
            if (built.sortedIndices != null) {
                ownedSorted = NativeBuffer.copy(built.sortedIndices.getDirectBuffer());
            }
            copy = AsyncSectionMesher.BuiltSectionA.rebuild(
                    built, ownedVertices.getDirectBuffer(), ownedSorted);
        } catch (RuntimeException | Error error) {
            ownedVertices.free();
            if (ownedSorted != null) {
                ownedSorted.free();
            }
            throw error;
        }
        Entry entry = new Entry(key, generation, copy, ownedVertices, ownedSorted);
        Entry replaced = this.byKey.put(key, entry);
        if (replaced != null) {
            this.entries.remove(replaced);
            replaced.freeCopies();
        }
        this.entries.addLast(entry);
        return entry;
    }

    /** The head entry, or {@code null} when the queue is empty. */
    public Entry peekFirst() {
        return this.entries.peekFirst();
    }

    /** Removes and returns the head entry (ownership of its copies passes to the caller), or null. */
    public Entry pollFirst() {
        Entry head = this.entries.pollFirst();
        if (head != null) {
            this.byKey.remove(head.key, head);
        }
        return head;
    }

    /**
     * Removes one key's entry, freeing its copies: the invalidation half of
     * the queue discipline, driven by unregister (through the host, for
     * every engine) and by a fresh offer superseding a queued build.
     *
     * @return whether an entry was queued for the key
     */
    public boolean cancel(TerrainSectionStore.Key key) {
        Entry entry = this.byKey.remove(key);
        if (entry == null) {
            return false;
        }
        this.entries.remove(entry);
        entry.freeCopies();
        return true;
    }

    /** Whether an entry is queued for the key. */
    public boolean contains(TerrainSectionStore.Key key) {
        return this.byKey.containsKey(key);
    }

    /**
     * The drain's stale check: when the head entry's key is already
     * registered in the store — a newer build of the section was admitted
     * directly while this one waited — the entry is removed, its copies
     * freed and the drop counted, and the caller continues its drain loop.
     * A stale head is a normal outcome of the replace discipline, never an
     * exception.
     *
     * @return whether the head was dropped as stale
     */
    public boolean dropStaleHead(TerrainSectionStore store) {
        Entry head = this.entries.peekFirst();
        if (head == null || store.get(head.key) == null) {
            return false;
        }
        this.entries.pollFirst();
        this.byKey.remove(head.key, head);
        head.freeCopies();
        this.staleDrops++;
        return true;
    }

    /** How many head entries {@link #dropStaleHead} has dropped on this queue. */
    public long staleDropCount() {
        return this.staleDrops;
    }

    public int size() {
        return this.entries.size();
    }

    public boolean isEmpty() {
        return this.entries.isEmpty();
    }

    /** Drops every entry, freeing all owned copies (executor delete, epoch end). */
    public void clear() {
        for (Entry entry : this.entries) {
            entry.freeCopies();
        }
        this.entries.clear();
        this.byKey.clear();
    }
}
