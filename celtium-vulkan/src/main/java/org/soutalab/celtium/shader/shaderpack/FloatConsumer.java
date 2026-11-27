package org.soutalab.celtium.shader.shaderpack;

/**
 * A consumer of a primitive float directive value. The fastutil version of this interface is
 * not present in the fastutil release on this module's classpath, so the shaderpack parsing
 * surface carries its own instead of depending on a newer fastutil.
 */
@FunctionalInterface
public interface FloatConsumer {
	void accept(float value);
}
