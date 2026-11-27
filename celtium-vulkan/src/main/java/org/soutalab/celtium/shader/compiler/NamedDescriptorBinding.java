/*
 * Built-in shader pack engine for Celtium (LGPL-3.0).
 */
package org.soutalab.celtium.shader.compiler;

import org.soutalab.celtium.core.shader.DescriptorBindingPlan;

import java.util.Objects;

/**
 * One descriptor binding of a compiled pack program, with the GLSL uniform name it was declared
 * under ({@code colortex0}, {@code shadowtex1}, {@code depthtex2}, a custom texture name, ...).
 * The core contract's {@link DescriptorBindingPlan} deliberately carries only (binding, kind);
 * execution needs the name to resolve a binding to the plan's render targets, so the compiler
 * retains the names here, keyed by program in {@link CompiledShaderPack#namedBindings()}.
 */
public record NamedDescriptorBinding(String name, int binding, DescriptorBindingPlan.Kind kind) {

    public NamedDescriptorBinding {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        if (binding < 0) {
            throw new IllegalArgumentException("binding must not be negative: " + binding);
        }
    }
}
