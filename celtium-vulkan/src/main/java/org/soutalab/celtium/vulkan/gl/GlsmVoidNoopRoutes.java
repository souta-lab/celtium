package org.soutalab.celtium.vulkan.gl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * GLSM stage 3, GAP-INT-2 (state-holding subset only): the name-by-name routing table
 * for the transformer's VOID_NOOPS set - the void GL calls a direct LWJGL call site
 * currently drops before they can reach any state record.
 * <p>
 * The subset rule from the superset proof: a name belongs here when a later glGet or a
 * later draw reads the state it sets (stencil family, polygon mode, sample coverage,
 * sampler/buffer-base bindings, depth func/mask, and their peers - the same names the
 * function table records when they arrive through SEM routes). Each name maps to the
 * GLSM owner that must receive it. Names not in this table are the pure no-op remainder:
 * they stay segment-B by specification ((c) inside GAP-INT-2) and need no route.
 * <p>
 * This table is data for the transformer/merge phase; routing a name means the
 * transformer's VOID_NOOPS membership for it is removed and the call is delivered to
 * the named owner instead.
 */
public final class GlsmVoidNoopRoutes {
    /** The GLSM owner a state-bearing void call must be delivered to. */
    public enum Route {
        RASTER,     // NativeRasterState / its stage-3 extensions
        ENABLE,     // GlsmEnableState
        BINDING,    // GlsmBufferState / NativeTextureBindings / GlsmFramebufferAttachments
        CLEAR,      // GlsmClearState
        IMMEDIATE,  // GlsmImmediateState
        VIEWPORT    // GlsmViewportState
    }

    private static final Map<String, Route> ROUTES;

    static {
        Map<String, Route> routes = new HashMap<String, Route>();
        // Stencil family: read back by glGet GL_STENCIL_* and consumed by the pipeline.
        routes.put("glStencilFunc", Route.RASTER);
        routes.put("glStencilFuncSeparate", Route.RASTER);
        routes.put("glStencilOp", Route.RASTER);
        routes.put("glStencilOpSeparate", Route.RASTER);
        routes.put("glStencilMask", Route.RASTER);
        routes.put("glStencilMaskSeparate", Route.RASTER);
        // Polygon / coverage / depth: pipeline state a later draw consumes.
        routes.put("glPolygonMode", Route.RASTER);
        routes.put("glSampleCoverage", Route.RASTER);
        routes.put("glDepthFunc", Route.RASTER);
        routes.put("glDepthMask", Route.RASTER);
        routes.put("glColorMask", Route.RASTER);
        routes.put("glCullFace", Route.RASTER);
        routes.put("glFrontFace", Route.RASTER);
        routes.put("glBlendFunc", Route.RASTER);
        routes.put("glBlendFuncSeparate", Route.RASTER);
        routes.put("glBlendEquation", Route.RASTER);
        routes.put("glBlendEquationSeparate", Route.RASTER);
        routes.put("glBlendColor", Route.RASTER);
        routes.put("glPolygonOffset", Route.RASTER);
        routes.put("glScissor", Route.VIEWPORT);
        routes.put("glViewport", Route.VIEWPORT);
        // Bindings a later draw or query reads.
        routes.put("glBindSampler", Route.BINDING);
        routes.put("glBindBufferBase", Route.BINDING);
        routes.put("glBindBufferRange", Route.BINDING);
        routes.put("glBindBuffersBase", Route.BINDING);
        routes.put("glBindBuffersRange", Route.BINDING);
        routes.put("glBindImageTexture", Route.BINDING);
        routes.put("glBindRenderbuffer", Route.BINDING);
        // Clear values: glGet GL_COLOR_CLEAR_VALUE / GL_DEPTH_CLEAR_VALUE read them.
        routes.put("glClearColor", Route.CLEAR);
        routes.put("glClearDepth", Route.CLEAR);
        routes.put("glClearStencil", Route.CLEAR);
        // Immediate current values.
        routes.put("glLineWidth", Route.IMMEDIATE);
        routes.put("glPointSize", Route.IMMEDIATE);
        ROUTES = Collections.unmodifiableMap(routes);
    }

    private GlsmVoidNoopRoutes() {
    }

    /** @return true when {@code name} carries state a later read consumes. */
    public static boolean isStateBearing(String name) { return ROUTES.containsKey(name); }

    /** @return the receiving owner for {@code name}, or null for the pure no-op remainder. */
    public static Route routeOf(String name) { return ROUTES.get(name); }

    public static Map<String, Route> routes() { return ROUTES; }
}
