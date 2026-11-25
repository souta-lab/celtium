package org.soutalab.celtium.vulkan.graph;

/** One pass's declared use of one resource (design section 2.1: passes declare, the graph compiles). */
public final class ResourceAccess {

    private final GraphResource resource;
    private final AccessType type;

    public ResourceAccess(GraphResource resource, AccessType type) {
        if (resource == null || type == null) {
            throw new IllegalArgumentException("resource and type are required");
        }
        if (resource.kind() == GraphResource.Kind.BUFFER && type.isImageOnly()) {
            throw new IllegalArgumentException(type + " is an image access, resource "
                    + resource.name() + " is a buffer");
        }
        if (resource.kind() == GraphResource.Kind.IMAGE && type == AccessType.INDIRECT_READ) {
            throw new IllegalArgumentException("INDIRECT_READ is a buffer access, resource "
                    + resource.name() + " is an image");
        }
        this.resource = resource;
        this.type = type;
    }

    public GraphResource resource() {
        return this.resource;
    }

    public AccessType type() {
        return this.type;
    }
}
