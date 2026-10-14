package org.soutalab.celtium.shaderpack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The {@code #define}/{@code #undef} table for one preprocessing run.
 * Insertion-ordered so an end-of-run snapshot is deterministic.
 */
final class MacroState {

    record Macro(String name, String value, List<String> parameters) {
        boolean functionLike() {
            return parameters != null;
        }
    }

    private final LinkedHashMap<String, Macro> table = new LinkedHashMap<>();

    void define(String name, String value, List<String> parameters) {
        table.put(name, new Macro(name, value == null ? "" : value, parameters));
    }

    void undef(String name) {
        table.remove(name);
    }

    boolean isDefined(String name) {
        return table.containsKey(name);
    }

    Macro get(String name) {
        return table.get(name);
    }

    /** Deterministic copy of the table (sorted by name), for manifest snapshots. */
    Map<String, Macro> snapshot() {
        return Collections.unmodifiableMap(new TreeMap<>(table));
    }

    MacroState copy() {
        MacroState clone = new MacroState();
        clone.table.putAll(table);
        return clone;
    }

    int size() {
        return table.size();
    }
}
