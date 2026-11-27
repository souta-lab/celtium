package org.soutalab.celtium.shader.shaderpack;

import org.soutalab.celtium.shader.ShaderLogger;

import java.util.Optional;

public enum ParticleRenderingSettings {
	BEFORE,
	MIXED,
	AFTER;

	public static Optional<ParticleRenderingSettings> fromString(String name) {
		try {
			return Optional.of(ParticleRenderingSettings.valueOf(name));
		} catch (IllegalArgumentException e) {
			ShaderLogger.LOGGER.warn("Invalid particle rendering settings! " + name);
			return Optional.empty();
		}
	}
}
