package org.soutalab.celtium.shader.uniforms.transforms;

import org.soutalab.celtium.shader.uniforms.FrameUpdateNotifier;
import org.joml.Vector2f;
import org.joml.Vector2ic;

import java.util.function.Supplier;

public class SmoothedVec2f implements Supplier<Vector2f> {
	private final SmoothedFloat x;
	private final SmoothedFloat y;

	public SmoothedVec2f(float halfLifeUp, float halfLifeDown, Supplier<Vector2ic> unsmoothed, FrameUpdateNotifier updateNotifier) {
		x = new SmoothedFloat(halfLifeUp, halfLifeDown, () -> unsmoothed.get().x(), updateNotifier);
		y = new SmoothedFloat(halfLifeUp, halfLifeDown, () -> unsmoothed.get().y(), updateNotifier);
	}

	@Override
	public Vector2f get() {
		return new Vector2f(x.getAsFloat(), y.getAsFloat());
	}
}
