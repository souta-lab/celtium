package org.soutalab.celtium.shader.model;

import org.soutalab.celtium.shader.model.InternalTextureFormat;
import org.soutalab.celtium.shader.model.PixelFormat;
import org.soutalab.celtium.shader.model.PixelType;
import org.soutalab.celtium.shader.model.TextureType;

public record ImageInformation(String name, String samplerName, TextureType target, PixelFormat format,
							   InternalTextureFormat internalTextureFormat,
							   PixelType type, int width, int height, int depth, boolean clear, boolean isRelative,
							   float relativeWidth, float relativeHeight) {
}
