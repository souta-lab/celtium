package org.soutalab.celtium.vulkan.compat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The registry of celtium's frame-start extensions - the new architecture's generalised connection
 * surface, on the renderer side. Where the old backend grew a field and a call site per feature (the
 * dynamic light gather lived inside the terrain renderer's uniform write, discovered by whoever read
 * that far), a feature now registers one {@link FrameExtension} here and the frame pipeline calls
 * {@link #gatherAll(FrameExtensionContext)} once, before the graph is compiled.
 * <p>
 * Two surfaces meet under this name and must not be confused:
 * <ul>
 *   <li><b>This registry</b> is for celtium's own frame data - contributions that enter as plain data
 *   at frame start, through the context's sinks. It deliberately offers no per-draw, per-pass or
 *   per-query hook.</li>
 *   <li><b>Ledger II</b> (item 378, {@code scripts/check_addon_surface.py}) is the surface
 *   <em>addons</em> run on: the vanilla instruction points - {@code World.getCombinedLight} above all
 *   - which celtium serves from inside via the {@code CeltiumLightCache} funnel and never replaces.
 *   Celeritas Dynamic Lights works through those points, not through this registry, and the registry's
 *   frame-start gather does not bypass them: chunk-build light still comes from the world's own light
 *   queries when sections are meshed, exactly as vanilla asks them. Keeping the two apart is the
 *   invariant that lets both work at once - the native gather for shading-time sources, the funnel
 *   for everything an addon injects into.</li>
 * </ul>
 * Registration is startup-time and single-threaded; gathering is render-thread, once per frame, in
 * registration order.
 */
public final class RenderExtensionRegistry {

    private final Map<String, FrameExtension> extensions = new LinkedHashMap<>();

    /**
     * Registers one extension.
     *
     * @throws IllegalArgumentException if the id is already taken - two contributors under one id
     * would make the frame's data depend on registration order in a way nothing reports
     */
    public void register(FrameExtension extension) {
        if (extension == null) {
            throw new IllegalArgumentException("extension must not be null");
        }
        String id = extension.id();
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("extension id must not be empty");
        }
        if (this.extensions.putIfAbsent(id, extension) != null) {
            throw new IllegalArgumentException("duplicate frame extension id: " + id);
        }
    }

    /** {@return the extension registered under {@code id}, or null}. */
    public FrameExtension find(String id) {
        return this.extensions.get(id);
    }

    /** {@return the registered extensions, in registration order}. */
    public List<FrameExtension> extensions() {
        return Collections.unmodifiableList(new ArrayList<>(this.extensions.values()));
    }

    /** Calls every extension's gather once, in registration order. */
    public void gatherAll(FrameExtensionContext context) {
        for (FrameExtension extension : this.extensions.values()) {
            extension.gather(context);
        }
    }
}
