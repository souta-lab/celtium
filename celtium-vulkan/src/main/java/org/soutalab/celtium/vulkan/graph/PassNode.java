package org.soutalab.celtium.vulkan.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One declared pass of the frame graph: a name, a kind, the resources it touches and how, and
 * the recorder that emits its commands. Declaration order is the frame's intended order; the
 * compiler may only reorder where resource dependencies allow it (design section 2.1).
 */
public final class PassNode {

    public enum Kind { TRANSFER, COMPUTE, GRAPHICS, PRESENT }

    private final String name;
    private final Kind kind;
    private final List<ResourceAccess> accesses;
    private final PassRecorder recorder;

    private PassNode(Builder builder) {
        this.name = builder.name;
        this.kind = builder.kind;
        this.accesses = Collections.unmodifiableList(new ArrayList<ResourceAccess>(builder.accesses));
        this.recorder = builder.recorder;
    }

    public static Builder builder(String name, Kind kind) {
        return new Builder(name, kind);
    }

    public String name() {
        return this.name;
    }

    public Kind kind() {
        return this.kind;
    }

    public List<ResourceAccess> accesses() {
        return this.accesses;
    }

    /** The recorder, or null for passes the executor only needs barriers around. */
    public PassRecorder recorder() {
        return this.recorder;
    }

    /** This pass's access to a resource, or null when it does not touch it. */
    public ResourceAccess accessTo(GraphResource resource) {
        for (ResourceAccess access : this.accesses) {
            if (access.resource() == resource) {
                return access;
            }
        }
        return null;
    }

    public static final class Builder {
        private final String name;
        private final Kind kind;
        private final List<ResourceAccess> accesses = new ArrayList<ResourceAccess>();
        private PassRecorder recorder;

        private Builder(String name, Kind kind) {
            if (name == null || kind == null) {
                throw new IllegalArgumentException("name and kind are required");
            }
            this.name = name;
            this.kind = kind;
        }

        public Builder access(GraphResource resource, AccessType type) {
            this.accesses.add(new ResourceAccess(resource, type));
            return this;
        }

        public Builder recorder(PassRecorder recorder) {
            this.recorder = recorder;
            return this;
        }

        public PassNode build() {
            return new PassNode(this);
        }
    }
}
