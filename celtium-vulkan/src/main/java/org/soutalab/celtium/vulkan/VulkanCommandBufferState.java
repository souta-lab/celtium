package org.soutalab.celtium.vulkan;

/** Render-thread lifecycle of ONE command-buffer handle, not a device/context-wide recording flag. */
public final class VulkanCommandBufferState {
    public enum State { INITIAL, RECORDING, RENDER_PASS, EXECUTABLE, PENDING }
    private final long handle;
    private State state = State.INITIAL;
    private long target;

    public VulkanCommandBufferState(long handle) { this.handle = handle; }
    public long handle() { return handle; }
    public State state() { return state; }
    public boolean recording() { return state == State.RECORDING || state == State.RENDER_PASS; }
    public boolean inRenderPass() { return state == State.RENDER_PASS; }
    public void reset() {
        if (state == State.PENDING) throw new IllegalStateException("reset of pending command buffer " + handle);
        state = State.INITIAL;
    }
    public void begin() { require(State.INITIAL); state = State.RECORDING; }
    public void beginPass(long target) { require(State.RECORDING); this.target = target; state = State.RENDER_PASS; }
    public void requireDraw(long target) {
        require(State.RENDER_PASS);
        if (this.target != target) throw new IllegalStateException("draw for wrong target on command buffer " + handle);
    }
    public void endPass() { require(State.RENDER_PASS); state = State.RECORDING; }
    public void end() { require(State.RECORDING); state = State.EXECUTABLE; }
    public void submitted() { require(State.EXECUTABLE); state = State.PENDING; }
    public void completed() { require(State.PENDING); state = State.EXECUTABLE; }
    private void require(State expected) {
        if (state != expected) throw new IllegalStateException("command buffer " + handle + " is " + state + ", expected " + expected);
    }
}
