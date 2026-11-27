package org.soutalab.celtium.shader.shaderpack;

/**
 * An {@link IndirectPointer} resolved against the renderer's actual storage-buffer
 * allocation. The resolution factory is deliberately absent here: it bound an
 * {@code IndirectPointer} through the GL-side storage buffer holder, which was not
 * absorbed. Resolution returns in S5-b against celtium's own buffer allocation,
 * which is where the pack-declared index becomes a real binding.
 */
public record FilledIndirectPointer(int buffer, long offset) {
}
