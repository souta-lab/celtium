package org.embeddedt.embeddium.impl.gl.device;

/**
 * Minimal GL-free Demonica Tier A compatibility shell. Celtium renders exclusively through Vulkan
 * and has no OpenGL RenderDevice; {@link #enterManagedCode()} and {@link #exitManagedCode()} are no-ops.
 */
public interface RenderDevice {
    CommandList NOOP_COMMAND_LIST = () -> { };

    RenderDevice INSTANCE = new RenderDevice() {
        @Override
        public CommandList createCommandList() {
            return NOOP_COMMAND_LIST;
        }

        @Override
        public void makeActive() {
        }

        @Override
        public void makeInactive() {
        }
    };

    CommandList createCommandList();

    static void enterManagedCode() {
    }

    static void exitManagedCode() {
    }

    void makeActive();
    void makeInactive();
}
