package org.taumc.celeritas.core;

import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import org.soutalab.celtium.vulkan.VulkanImmediateFormats;

/** Structural layout resolution at upload time. Never infer a format from a stride or mutable object identity. */
public final class CeltiumVertexFormats {
    private CeltiumVertexFormats() {
    }

    public static int id(VertexFormat format) {
        if (format == null) {
            return 0;
        }
        StringBuilder signature = new StringBuilder();
        int offset = 0;
        for (int i = 0; i < format.getElementCount(); i++) {
            VertexFormatElement element = format.getElement(i);
            if (format.getOffset(i) != offset) {
                return 0;
            }
            // VertexFormatElement.toString() omits the index. A second float UV is NOT the primary texture UV.
            int expectedIndex = element.getUsage() == VertexFormatElement.EnumUsage.UV
                    && element.getType() == VertexFormatElement.EnumType.SHORT ? 1 : 0;
            if (element.getIndex() != expectedIndex) {
                return 0;
            }
            if (i != 0) {
                signature.append(' ');
            }
            signature.append(element);
            offset += element.getSize();
        }
        return offset == format.getSize()
                ? VulkanImmediateFormats.layout(signature.toString(), format.getSize(), format.getElementCount()) : 0;
    }
}
