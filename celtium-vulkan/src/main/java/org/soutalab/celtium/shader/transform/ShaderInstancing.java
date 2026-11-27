/*
 * Built-in shader pack engine for Celtium (LGPL-3.0).
 */
package org.soutalab.celtium.shader.transform;

public enum ShaderInstancing {
    NONE,
    CUBE,
    PARTICLE,
    WEATHER;

    /**
     * Whether this mode carries a per-instance head block the vertex stage rebuilds its
     * matrices from. In the original Angelica {@code Instancing} enum this was
     * {@code TEMPLATE || CUBE}; TEMPLATE is an FFP-template mode with no shader-pack
     * counterpart, so here only CUBE carries an instance head.
     */
    public boolean hasInstanceHead() {
        return this == CUBE;
    }
}
