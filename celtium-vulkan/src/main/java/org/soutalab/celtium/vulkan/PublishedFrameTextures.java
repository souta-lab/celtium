package org.soutalab.celtium.vulkan;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the publication bookkeeping the backend used to keep in its own fields: which GL texture name a framebuffer's
 * colour attachment is, which image the sampler may read for it, and the counters that say what happened.
 * <p>
 * This is the state half of refactoring plan R1b (docs/history/refactoring-plan.md section 6). The <em>decision</em> of which
 * image a name stands for stays in the backend until R5 moves the target registry, because it reads the frame target
 * and the off-target pairs; what moves here is the ownership of the census itself - one map from framebuffer identity
 * to its colour texture, one map from GL name to the published texture, and the counters - so that the two are no
 * longer fields of a fourteen-thousand-line class. Section 5.4 of the plan asks for exactly this split: identity,
 * image, generation and the read/write alias are one owner, and nothing else may invent a second census.
 * <p>
 * Kept free of external library imports so standalone CPU contract harnesses ({@code offtarget_rendered_name_check.py})
 * can compile this file directly with {@code javac} and no classpath.
 */
final class PublishedFrameTextures {

    /** Framebuffer identities and their {colour texture name, width, height} attachments. */
    private long[] fbIdentities = new long[16];
    private long[][] fbAttachments = new long[16][];
    private int fbCount;
    private long lastFbIdentity = Long.MIN_VALUE;
    private long[] lastFbAttachment;

    /** Direct-indexed table mapping GL texture name -> framebuffer identity (Long.MIN_VALUE when absent). */
    private volatile long[] identityByTexId = createIdentityTable(4096);

    /** Colour attachment slots a framebuffer record carries; matches the GL stub's COLOR_ATTACHMENT_SLOTS. */
    static final int MAX_COLOR_SLOTS = 8;

    /**
     * Colour attachment tuples beyond slot 0, per framebuffer identity: index i of an entry is the GL texture name
     * attached at GL_COLOR_ATTACHMENT0+i. Slot 0 of an entry mirrors the {@link #fbAttachments} record (that record
     * stays the authority for slot 0); this table exists so a multi-target framebuffer's full tuple - the thing an
     * MRT window sizes itself by - has exactly one owner, like the rest of the census.
     */
    private long[] fbSlotIdentities = new long[16];
    private int[][] fbSlotTextures = new int[16][];
    private int fbSlotCount;

    /** Direct-indexed tables mapping a slot >= 1 GL texture name -> its framebuffer identity and slot index. */
    private volatile long[] slotIdentityByTexId = createIdentityTable(4096);
    private volatile int[] slotIndexByTexId = createSlotIndexTable(4096);

    private static int[] createSlotIndexTable(int length) {
        int[] table = new int[length];
        Arrays.fill(table, -1);
        return table;
    }

    private void setSlotForTexture(int glTextureName, long identity, int slot) {
        if (glTextureName <= 0) {
            return;
        }
        long[] identities = this.slotIdentityByTexId;
        int[] slots = this.slotIndexByTexId;
        if (glTextureName >= identities.length) {
            int nextLen = Math.max(identities.length * 2, glTextureName + 1);
            identities = Arrays.copyOf(identities, nextLen);
            Arrays.fill(identities, this.slotIdentityByTexId.length, nextLen, Long.MIN_VALUE);
            slots = Arrays.copyOf(slots, nextLen);
            Arrays.fill(slots, this.slotIndexByTexId.length, nextLen, -1);
            this.slotIdentityByTexId = identities;
            this.slotIndexByTexId = slots;
        }
        identities[glTextureName] = identity;
        slots[glTextureName] = slot;
    }

    private void clearSlotForTexture(int glTextureName) {
        if (glTextureName <= 0) {
            return;
        }
        long[] identities = this.slotIdentityByTexId;
        if (glTextureName < identities.length) {
            identities[glTextureName] = Long.MIN_VALUE;
            this.slotIndexByTexId[glTextureName] = -1;
        }
    }

    /** {@return the identity a slot >= 1 texture name belongs to}, or {@code Long.MIN_VALUE} when it fills no slot. */
    long slotIdentityForColorTexture(int glTextureName) {
        if (glTextureName <= 0) {
            return Long.MIN_VALUE;
        }
        long[] table = this.slotIdentityByTexId;
        return glTextureName < table.length ? table[glTextureName] : Long.MIN_VALUE;
    }

    /** {@return the slot a GL texture name fills (>= 1)}, or -1 when it fills no slot beyond slot 0's own record. */
    int slotIndexForColorTexture(int glTextureName) {
        if (glTextureName <= 0) {
            return -1;
        }
        int[] table = this.slotIndexByTexId;
        return glTextureName < table.length ? table[glTextureName] : -1;
    }

    private int indexOfSlots(long framebufferIdentity) {
        for (int i = 0; i < this.fbSlotCount; i++) {
            if (this.fbSlotIdentities[i] == framebufferIdentity) {
                return i;
            }
        }
        return -1;
    }

    private void removeSlotsAt(int index) {
        int[] removed = this.fbSlotTextures[index];
        for (int s = 1; s < MAX_COLOR_SLOTS; s++) {
            if (removed[s] != 0 && slotIdentityForColorTexture(removed[s]) == this.fbSlotIdentities[index]) {
                clearSlotForTexture(removed[s]);
            }
        }
        int last = --this.fbSlotCount;
        if (index != last) {
            this.fbSlotIdentities[index] = this.fbSlotIdentities[last];
            this.fbSlotTextures[index] = this.fbSlotTextures[last];
        }
        this.fbSlotTextures[last] = null;
    }

    /**
     * Records a framebuffer's colour attachment tuple (index = slot, 0 marks a detached slot). Only slots >= 1 are
     * stored; slot 0 stays owned by {@link #bindColorTexture}. A tuple with nothing beyond slot 0 clears the
     * identity's record - a single-target framebuffer has no tuple.
     */
    synchronized void bindColorAttachments(long framebufferIdentity, int[] colorTextures) {
        boolean hasExtra = false;
        if (colorTextures != null) {
            for (int s = 1; s < colorTextures.length && s < MAX_COLOR_SLOTS; s++) {
                if (colorTextures[s] != 0) {
                    hasExtra = true;
                    break;
                }
            }
        }
        int index = indexOfSlots(framebufferIdentity);
        if (!hasExtra) {
            if (index >= 0) {
                removeSlotsAt(index);
            }
            return;
        }
        int[] tuple = new int[MAX_COLOR_SLOTS];
        for (int s = 0; s < MAX_COLOR_SLOTS && s < colorTextures.length; s++) {
            tuple[s] = colorTextures[s];
        }
        if (index < 0) {
            if (this.fbSlotCount == this.fbSlotIdentities.length) {
                int nextCap = this.fbSlotIdentities.length * 2;
                this.fbSlotIdentities = Arrays.copyOf(this.fbSlotIdentities, nextCap);
                this.fbSlotTextures = Arrays.copyOf(this.fbSlotTextures, nextCap);
            }
            index = this.fbSlotCount++;
            this.fbSlotIdentities[index] = framebufferIdentity;
        } else {
            int[] old = this.fbSlotTextures[index];
            for (int s = 1; s < MAX_COLOR_SLOTS; s++) {
                if (old[s] != 0 && old[s] != tuple[s]
                        && slotIdentityForColorTexture(old[s]) == framebufferIdentity) {
                    clearSlotForTexture(old[s]);
                }
            }
        }
        this.fbSlotTextures[index] = tuple;
        for (int s = 1; s < MAX_COLOR_SLOTS; s++) {
            if (tuple[s] != 0) {
                setSlotForTexture(tuple[s], framebufferIdentity, s);
            }
        }
    }

    /** {@return the colour texture name recorded at a slot of an identity, or 0 when that slot is empty}. */
    synchronized int colorTextureAt(long identity, int slot) {
        if (slot == 0) {
            return colorTextureName(identity);
        }
        if (slot < 0 || slot >= MAX_COLOR_SLOTS) {
            return 0;
        }
        int index = indexOfSlots(identity);
        return index < 0 ? 0 : this.fbSlotTextures[index][slot];
    }

    /**
     * {@return how many colour attachments an identity draws into}: the leading run of non-zero slots from slot 0.
     * A gap ends the count - a framebuffer with slot 2 attached but slot 1 empty is not a three-target framebuffer
     * this renderer can build (GL would call it incomplete), so the window sizes itself by the run it can honour.
     */
    synchronized int colorAttachmentCount(long identity) {
        if (colorTextureName(identity) == 0) {
            return 0;
        }
        int index = indexOfSlots(identity);
        if (index < 0) {
            return 1;
        }
        int[] tuple = this.fbSlotTextures[index];
        int count = 1;
        while (count < MAX_COLOR_SLOTS && tuple[count] != 0) {
            count++;
        }
        return count;
    }

    /**
     * {@return the attachment slot a GL texture name fills for its framebuffer} (0 for the slot-0 record), or -1
     * when the name is no framebuffer's colour attachment. The layout a published wrapper declares depends on which
     * slot's image it wraps, so the publication bridge asks here rather than assuming slot 0.
     */
    int attachmentSlotForTexture(int glTextureName) {
        if (identityForColorTexture(glTextureName) != Long.MIN_VALUE) {
            return 0;
        }
        return slotIndexForColorTexture(glTextureName);
    }

    /** Direct-indexed table mapping GL texture name -> published VulkanGlTexture, paired with dense iteration arrays. */
    private volatile VulkanGlTexture[] publishedByTexId = new VulkanGlTexture[4096];
    private int[] publishedKeys = new int[16];
    private VulkanGlTexture[] publishedValues = new VulkanGlTexture[16];
    private int publishedSize;

    private static long[] createIdentityTable(int length) {
        long[] table = new long[length];
        Arrays.fill(table, Long.MIN_VALUE);
        return table;
    }

    private void setIdentityForTexture(int glTextureName, long identity) {
        if (glTextureName <= 0) {
            return;
        }
        long[] table = this.identityByTexId;
        if (glTextureName >= table.length) {
            int nextLen = Math.max(table.length * 2, glTextureName + 1);
            long[] grown = Arrays.copyOf(table, nextLen);
            Arrays.fill(grown, table.length, nextLen, Long.MIN_VALUE);
            table = grown;
            this.identityByTexId = table;
        }
        table[glTextureName] = identity;
    }

    /** Bumped on every change to published textures, so a reader can cache the name-to-image resolution. */
    private long publishedVersion;

    /** How many framebuffer binds carried a colour texture, published or not. */
    private int colorTextureReports;

    /** How many colour textures were published to an image. */
    private int colorTexturesPublished;

    /** Publication outcomes, so a missing image can be told from a never-published name. */
    private int created;
    private int unpublished;
    private int skippedForAttach;
    private int stalePruned;
    private int republished;

    /** Records a framebuffer's colour attachment. A zero name or an empty extent clears the entry. */
    synchronized void bindColorTexture(long framebufferIdentity, int colorTexture, int width, int height) {
        if (colorTexture == 0 || width <= 0 || height <= 0) {
            for (int i = 0; i < this.fbCount; i++) {
                if (this.fbIdentities[i] == framebufferIdentity) {
                    long[] removed = this.fbAttachments[i];
                    int last = --this.fbCount;
                    if (i != last) {
                        this.fbIdentities[i] = this.fbIdentities[last];
                        this.fbAttachments[i] = this.fbAttachments[last];
                    }
                    this.fbAttachments[last] = null;
                    if (this.lastFbIdentity == framebufferIdentity) {
                        this.lastFbIdentity = Long.MIN_VALUE;
                        this.lastFbAttachment = null;
                    }
                    int oldTex = (int) removed[0];
                    if (identityForColorTexture(oldTex) == framebufferIdentity) {
                        setIdentityForTexture(oldTex, Long.MIN_VALUE);
                    }
                    break;
                }
            }
            return;
        }
        for (int i = 0; i < this.fbCount; i++) {
            if (this.fbIdentities[i] == framebufferIdentity) {
                long[] existing = this.fbAttachments[i];
                int oldTex = (int) existing[0];
                if (oldTex != colorTexture && identityForColorTexture(oldTex) == framebufferIdentity) {
                    setIdentityForTexture(oldTex, Long.MIN_VALUE);
                }
                existing[0] = colorTexture;
                existing[1] = width;
                existing[2] = height;
                this.lastFbIdentity = framebufferIdentity;
                this.lastFbAttachment = existing;
                setIdentityForTexture(colorTexture, framebufferIdentity);
                this.colorTextureReports++;
                return;
            }
        }
        if (this.fbCount == this.fbIdentities.length) {
            int nextCap = this.fbIdentities.length * 2;
            this.fbIdentities = Arrays.copyOf(this.fbIdentities, nextCap);
            this.fbAttachments = Arrays.copyOf(this.fbAttachments, nextCap);
        }
        long[] entry = new long[] { colorTexture, width, height };
        this.fbIdentities[this.fbCount] = framebufferIdentity;
        this.fbAttachments[this.fbCount] = entry;
        this.fbCount++;
        this.lastFbIdentity = framebufferIdentity;
        this.lastFbAttachment = entry;
        setIdentityForTexture(colorTexture, framebufferIdentity);
        this.colorTextureReports++;
    }

    /** Drops every framebuffer's colour-attachment record that names this GL texture (it was deleted or reused). */
    synchronized void forgetColorTexture(int glTextureName) {
        long slotIdentity = slotIdentityForColorTexture(glTextureName);
        if (slotIdentity != Long.MIN_VALUE) {
            int slot = slotIndexForColorTexture(glTextureName);
            clearSlotForTexture(glTextureName);
            int index = indexOfSlots(slotIdentity);
            if (index >= 0 && slot > 0 && slot < MAX_COLOR_SLOTS) {
                int[] tuple = this.fbSlotTextures[index];
                tuple[slot] = 0;
                boolean hasExtra = false;
                for (int s = 1; s < MAX_COLOR_SLOTS; s++) {
                    if (tuple[s] != 0) {
                        hasExtra = true;
                        break;
                    }
                }
                if (!hasExtra) {
                    removeSlotsAt(index);
                }
            }
        }
        long identity = identityForColorTexture(glTextureName);
        if (identity != Long.MIN_VALUE) {
            setIdentityForTexture(glTextureName, Long.MIN_VALUE);
            for (int i = 0; i < this.fbCount; i++) {
                if (this.fbIdentities[i] == identity && this.fbAttachments[i][0] == glTextureName) {
                    int last = --this.fbCount;
                    if (i != last) {
                        this.fbIdentities[i] = this.fbIdentities[last];
                        this.fbAttachments[i] = this.fbAttachments[last];
                    }
                    this.fbAttachments[last] = null;
                    if (this.lastFbIdentity == identity) {
                        this.lastFbIdentity = Long.MIN_VALUE;
                        this.lastFbAttachment = null;
                    }
                    break;
                }
            }
        }
    }

    /** {@return the colour texture recorded for a framebuffer identity as {texture, width, height}, or null}. */
    synchronized long[] colorTexture(long identity) {
        if (identity == this.lastFbIdentity && this.lastFbAttachment != null) {
            return this.lastFbAttachment;
        }
        for (int i = 0; i < this.fbCount; i++) {
            if (this.fbIdentities[i] == identity) {
                long[] found = this.fbAttachments[i];
                this.lastFbIdentity = identity;
                this.lastFbAttachment = found;
                return found;
            }
        }
        return null;
    }

    /** {@return the framebuffer identity whose colour attachment carries this GL texture name}, or {@code MIN_VALUE}. */
    long identityForColorTexture(int glTextureName) {
        if (glTextureName <= 0) {
            return Long.MIN_VALUE;
        }
        long[] table = this.identityByTexId;
        return glTextureName < table.length ? table[glTextureName] : Long.MIN_VALUE;
    }

    /** {@return the colour texture name recorded for an identity, or 0 when there is none}. */
    int colorTextureName(long identity) {
        long[] reported = colorTexture(identity);
        return reported == null ? 0 : (int) reported[0];
    }

    /**
     * Depth attachments by framebuffer identity, the depth half of the record {@link #bindColorTexture}
     * keeps for colour. The colour record is what lets a sampled colour name resolve to the image its
     * framebuffer's draws went into; depth names need the same two directions — identity → texture (the
     * window head asks which depth texture the bound framebuffer attaches, so its pair can borrow the
     * shared depth image for that texture) and texture → identity (a sampled depth name asks which
     * framebuffer's depth it is, and a depth copy asks the same for its read framebuffer). A zero name
     * clears the identity's entry.
     */
    // Primitive arrays + direct-indexed table (no external imports so standalone javac contract checks still compile):
    // eliminates Long/Integer boxing on every bindDepthTexture / depthTexture / identityForDepthTexture call.
    private long[] depthFbIdentities = new long[16];
    private int[] depthFbTextures = new int[16];
    private int depthFbCount;
    private long lastDepthFbIdentity = Long.MIN_VALUE;
    private int lastDepthFbTex;

    /** Direct-indexed reverse table mapping depth GL texture name -> framebuffer identity (Long.MIN_VALUE when absent). */
    private volatile long[] identityByDepthTexId = createIdentityTable(4096);

    private void setIdentityForDepthTexture(int glTextureName, long identity) {
        if (glTextureName <= 0) {
            return;
        }
        long[] table = this.identityByDepthTexId;
        if (glTextureName >= table.length) {
            int nextLen = Math.max(table.length * 2, glTextureName + 1);
            long[] grown = Arrays.copyOf(table, nextLen);
            Arrays.fill(grown, table.length, nextLen, Long.MIN_VALUE);
            table = grown;
            this.identityByDepthTexId = table;
        }
        table[glTextureName] = identity;
    }

    /** Records a framebuffer's depth attachment. A zero name clears the entry. */
    synchronized void bindDepthTexture(long framebufferIdentity, int depthTexture) {
        int existingIndex = -1;
        int previous = 0;
        for (int i = 0; i < this.depthFbCount; i++) {
            if (this.depthFbIdentities[i] == framebufferIdentity) {
                existingIndex = i;
                previous = this.depthFbTextures[i];
                break;
            }
        }
        if (previous == depthTexture) {
            return;
        }
        if (previous != 0 && identityForDepthTexture(previous) == framebufferIdentity) {
            setIdentityForDepthTexture(previous, Long.MIN_VALUE);
        }
        if (depthTexture == 0) {
            if (existingIndex >= 0) {
                int last = --this.depthFbCount;
                if (existingIndex != last) {
                    this.depthFbIdentities[existingIndex] = this.depthFbIdentities[last];
                    this.depthFbTextures[existingIndex] = this.depthFbTextures[last];
                }
                if (this.lastDepthFbIdentity == framebufferIdentity) {
                    this.lastDepthFbIdentity = Long.MIN_VALUE;
                    this.lastDepthFbTex = 0;
                }
            }
            return;
        }
        if (existingIndex >= 0) {
            this.depthFbTextures[existingIndex] = depthTexture;
        } else {
            if (this.depthFbCount == this.depthFbIdentities.length) {
                int nextCap = this.depthFbIdentities.length * 2;
                this.depthFbIdentities = Arrays.copyOf(this.depthFbIdentities, nextCap);
                this.depthFbTextures = Arrays.copyOf(this.depthFbTextures, nextCap);
            }
            this.depthFbIdentities[this.depthFbCount] = framebufferIdentity;
            this.depthFbTextures[this.depthFbCount] = depthTexture;
            this.depthFbCount++;
        }
        this.lastDepthFbIdentity = framebufferIdentity;
        this.lastDepthFbTex = depthTexture;
        setIdentityForDepthTexture(depthTexture, framebufferIdentity);
    }

    /** {@return the depth texture name recorded for an identity, or 0 when there is none}. */
    synchronized int depthTexture(long identity) {
        if (identity == this.lastDepthFbIdentity) {
            return this.lastDepthFbTex;
        }
        for (int i = 0; i < this.depthFbCount; i++) {
            if (this.depthFbIdentities[i] == identity) {
                int found = this.depthFbTextures[i];
                this.lastDepthFbIdentity = identity;
                this.lastDepthFbTex = found;
                return found;
            }
        }
        return 0;
    }

    /** {@return the framebuffer identity whose depth attachment carries this GL texture name}, or {@code MIN_VALUE}. */
    long identityForDepthTexture(int glTextureName) {
        if (glTextureName <= 0) {
            return Long.MIN_VALUE;
        }
        long[] table = this.identityByDepthTexId;
        if (glTextureName >= table.length) {
            return Long.MIN_VALUE;
        }
        long identity = table[glTextureName];
        return identity == 0L ? Long.MIN_VALUE : identity;
    }

    /** Drops every depth record naming this GL texture (it was deleted or its name reused). */
    synchronized void forgetDepthTexture(int glTextureName) {
        long identity = identityForDepthTexture(glTextureName);
        if (identity != Long.MIN_VALUE) {
            bindDepthTexture(identity, 0);
        }
    }

    /** {@return the publication version}, incremented whenever the published set changes. */
    long publishedVersion() {
        return this.publishedVersion;
    }

    /** Records a published texture for a GL name. */
    synchronized void putPublished(int glTextureName, VulkanGlTexture texture) {
        if (glTextureName <= 0) {
            return;
        }
        this.publishedVersion++;
        VulkanGlTexture[] table = this.publishedByTexId;
        if (glTextureName >= table.length) {
            int nextLen = Math.max(table.length * 2, glTextureName + 1);
            table = Arrays.copyOf(table, nextLen);
            this.publishedByTexId = table;
        }
        if (table[glTextureName] != null) {
            table[glTextureName] = texture;
            for (int i = 0; i < this.publishedSize; i++) {
                if (this.publishedKeys[i] == glTextureName) {
                    this.publishedValues[i] = texture;
                    return;
                }
            }
        }
        table[glTextureName] = texture;
        if (this.publishedSize == this.publishedKeys.length) {
            int nextCap = this.publishedKeys.length * 2;
            this.publishedKeys = Arrays.copyOf(this.publishedKeys, nextCap);
            this.publishedValues = Arrays.copyOf(this.publishedValues, nextCap);
        }
        this.publishedKeys[this.publishedSize] = glTextureName;
        this.publishedValues[this.publishedSize] = texture;
        this.publishedSize++;
    }

    /** {@return the published texture for a GL name, or null}. */
    VulkanGlTexture published(int glTextureName) {
        if (glTextureName <= 0) {
            return null;
        }
        VulkanGlTexture[] table = this.publishedByTexId;
        return glTextureName < table.length ? table[glTextureName] : null;
    }

    /** Forgets a published name. */
    synchronized void removePublished(int glTextureName) {
        if (glTextureName <= 0) {
            return;
        }
        VulkanGlTexture[] table = this.publishedByTexId;
        if (glTextureName < table.length && table[glTextureName] != null) {
            table[glTextureName] = null;
            this.publishedVersion++;
            for (int i = 0; i < this.publishedSize; i++) {
                if (this.publishedKeys[i] == glTextureName) {
                    removePublishedAt(i);
                    return;
                }
            }
        }
    }

    int publishedKeyAt(int index) {
        return this.publishedKeys[index];
    }

    VulkanGlTexture publishedValueAt(int index) {
        return this.publishedValues[index];
    }

    synchronized void setPublishedValueAt(int index, VulkanGlTexture texture) {
        int key = this.publishedKeys[index];
        this.publishedValues[index] = texture;
        VulkanGlTexture[] table = this.publishedByTexId;
        if (key > 0 && key < table.length) {
            table[key] = texture;
        }
    }

    synchronized void removePublishedAt(int index) {
        int key = this.publishedKeys[index];
        VulkanGlTexture[] table = this.publishedByTexId;
        if (key > 0 && key < table.length && table[key] != null) {
            table[key] = null;
            this.publishedVersion++;
        }
        int last = --this.publishedSize;
        if (index != last) {
            this.publishedKeys[index] = this.publishedKeys[last];
            this.publishedValues[index] = this.publishedValues[last];
        }
        this.publishedValues[last] = null;
    }

    /** {@return how many names are published right now}. */
    int publishedCount() {
        return this.publishedSize;
    }

    /** {@return how many framebuffer binds carried a colour texture}. */
    int colorTextureReports() {
        return this.colorTextureReports;
    }

    void noteColorTexturePublished() {
        this.colorTexturesPublished++;
    }

    /** {@return how many colour textures were published to an image}. */
    int colorTexturesPublished() {
        return this.colorTexturesPublished;
    }

    void noteCreated() {
        this.created++;
    }

    void noteUnpublished() {
        this.unpublished++;
    }

    void noteSkippedForAttach() {
        this.skippedForAttach++;
    }

    void noteStalePruned() {
        this.stalePruned++;
    }

    void noteRepublished() {
        this.republished++;
    }

    /** {@return whether any name is published}. */
    boolean isEmptyPublished() {
        return this.publishedSize == 0;
    }

    /**
     * {@return every published name's recorded layout, as {@code name=LAYOUT} pairs}.
     * <p>
     * The layout a descriptor written from a wrapper carries is the wrapper's recorded value, so this is the third
     * value {@code VUID-vkCmdDraw-None-09600} compares - beside the layout the pass declares and the layout the image
     * is actually in. Printed on the pair-layout line so the three can be read together.
     */
    String publishedLayoutsSummary() {
        if (this.publishedSize == 0) {
            return "none";
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < this.publishedSize; i++) {
            VulkanGlTexture value = this.publishedValues[i];
            if (value == null) {
                continue;
            }
            if (text.length() > 0) {
                text.append(',');
            }
            text.append(this.publishedKeys[i]).append('=').append(value.currentLayoutName());
        }
        return text.length() == 0 ? "none" : text.toString();
    }

    int created() {
        return this.created;
    }

    int unpublished() {
        return this.unpublished;
    }

    int skippedForAttach() {
        return this.skippedForAttach;
    }

    int stalePruned() {
        return this.stalePruned;
    }

    int republished() {
        return this.republished;
    }

    /** Published descriptor view for a declared named render target. */
    record NamedTargetTexture(
            String name,
            long image,
            long view,
            long sampler,
            int width,
            int height,
            int vkFormat,
            boolean depth,
            boolean comparisonSampler,
            int layout,
            long generation) {
    }

    private final Map<String, NamedTargetTexture> namedPublished = new ConcurrentHashMap<>();

    void putNamedPublished(NamedTargetTexture texture) {
        if (texture == null || texture.name() == null || texture.name().isBlank()) {
            throw new IllegalArgumentException("named published texture requires a valid name");
        }
        this.publishedVersion++;
        this.namedPublished.put(texture.name(), texture);
    }

    NamedTargetTexture publishedNamed(String name) {
        return name == null ? null : this.namedPublished.get(name);
    }

    void removeNamedPublished(String name) {
        if (name == null) {
            return;
        }
        if (this.namedPublished.remove(name) != null) {
            this.publishedVersion++;
        }
    }

    void clearNamedPublished() {
        if (!this.namedPublished.isEmpty()) {
            this.publishedVersion++;
            this.namedPublished.clear();
        }
    }

    int publishedNamedCount() {
        return this.namedPublished.size();
    }

    java.util.Set<Map.Entry<String, NamedTargetTexture>> publishedNamedEntries() {
        return this.namedPublished.entrySet();
    }

    /** Synchronizes allocated named targets from {@code registry} into this publication table. */
    void syncNamedTargets(RenderTargetRegistry registry) {
        if (registry == null || registry.namedTargetCount() == 0) {
            clearNamedPublished();
            return;
        }
        java.util.Iterator<String> existing = this.namedPublished.keySet().iterator();
        while (existing.hasNext()) {
            String name = existing.next();
            if (registry.namedTargetState(name) == null) {
                existing.remove();
                this.publishedVersion++;
            }
        }
        for (RenderTargetRegistry.NamedTargetSpec spec : registry.namedTargets()) {
            RenderTargetRegistry.NamedTargetState state = registry.namedTargetState(spec.name());
            if (state == null) {
                continue;
            }
            NamedTargetTexture next = new NamedTargetTexture(
                    spec.name(),
                    state.image(),
                    state.view(),
                    state.sampler(),
                    state.width(),
                    state.height(),
                    spec.formatClass().vkFormat(),
                    spec.kind().isDepth(),
                    spec.kind().usesComparisonSampler(),
                    state.layout(),
                    state.generation());
            NamedTargetTexture prev = this.namedPublished.get(spec.name());
            if (!next.equals(prev)) {
                this.namedPublished.put(spec.name(), next);
                this.publishedVersion++;
            }
        }
    }

    /** {@return the publication counters in the order the verification line prints them}. */
    String summary() {
        return "publishedFrameTexturesCreated=" + this.created
                + " publishedFrameTexturesUnpublished=" + this.unpublished
                + " publishedFrameTexturesSkippedForAttach=" + this.skippedForAttach
                + " publishedFrameTexturesStalePruned=" + this.stalePruned
                + " publishedFrameTexturesRepublished=" + this.republished;
    }
}
