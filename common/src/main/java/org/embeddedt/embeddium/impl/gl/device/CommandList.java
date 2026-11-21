package org.embeddedt.embeddium.impl.gl.device;

/**
 * Minimal Demonica Tier A binary-compatibility shell. Celtium renders terrain exclusively through Vulkan
 * and never creates or submits Celeritas OpenGL command lists.
 */
public interface CommandList extends AutoCloseable {
    void flush();

    @Override
    default void close() {
        this.flush();
    }
}
