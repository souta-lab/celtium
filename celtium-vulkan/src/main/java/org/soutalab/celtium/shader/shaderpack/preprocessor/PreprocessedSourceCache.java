/*
 * Built-in shader pack engine for Celtium (LGPL-3.0).
 * Replaces Angelica GLSM ShaderDiskCache with a lightweight in-memory cache.
 */
package org.soutalab.celtium.shader.shaderpack.preprocessor;

import org.jetbrains.annotations.Nullable;
import org.soutalab.celtium.shader.shaderpack.StringPair;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class PreprocessedSourceCache {
    private static final int MAX_ENTRIES = 2048;
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private PreprocessedSourceCache() {
    }

    static final class Key {
        private final MessageDigest digest;
        private String finished;

        private Key(String namespace) {
            try {
                this.digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 unavailable", e);
            }
            str(namespace);
        }

        Key str(String value) {
            byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
            digest.update(bytes);
            digest.update((byte) 0);
            finished = null;
            return this;
        }

        Key bytes(byte[] value) {
            if (value != null) {
                digest.update(value);
            }
            digest.update((byte) 0);
            finished = null;
            return this;
        }

        Key intVal(int value) {
            digest.update((byte) (value >>> 24));
            digest.update((byte) (value >>> 16));
            digest.update((byte) (value >>> 8));
            digest.update((byte) value);
            finished = null;
            return this;
        }

        String finish() {
            if (finished == null) {
                finished = Base64.getEncoder().encodeToString(digest.digest());
            }
            return finished;
        }
    }

    static Key key(String kind, String source, Iterable<StringPair> defines) {
        return withDefines(new Key("celtium-preprocess").str(kind).str(source), defines);
    }

    static Key key(String kind, byte[] contentHash, Iterable<StringPair> defines) {
        return withDefines(new Key("celtium-preprocess").str(kind).bytes(contentHash), defines);
    }

    static void variant(@Nullable Key key, int value) {
        if (key != null) {
            key.intVal(value);
        }
    }

    private static Key withDefines(Key key, Iterable<StringPair> defines) {
        for (StringPair define : defines) {
            key.str(define.getKey()).str(define.getValue());
        }
        return key;
    }

    @Nullable
    static String get(@Nullable Key key) {
        return key == null ? null : CACHE.get(key.finish());
    }

    static void put(@Nullable Key key, String preprocessed) {
        if (key != null && preprocessed != null) {
            if (CACHE.size() >= MAX_ENTRIES) {
                CACHE.clear();
            }
            CACHE.put(key.finish(), preprocessed);
        }
    }

    static void clear() {
        CACHE.clear();
    }
}
