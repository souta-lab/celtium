/*
 * Built-in shader pack engine for Celtium (LGPL-3.0).
 */
package org.soutalab.celtium.shader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Shared logger and debug flags for the built-in shader pack interpreter and compiler.
 */
public final class ShaderLogger {
    public static final Logger LOGGER = LogManager.getLogger("CeltiumShader");
    public static final boolean ENABLE_DEBUG = Boolean.getBoolean("celtium.shader.debug");

    private ShaderLogger() {
    }
}
