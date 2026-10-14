package org.soutalab.celtium.shaderpack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal {@code key=value} parser for {@code .properties} files, applied to
 * the already-preprocessed line stream (so {@code #if MC_VERSION} blocks have
 * been resolved). Handles line continuation by trailing backslash, which real
 * packs use for long {@code uniform.*} expressions, and java.util.Properties
 * separators ({@code =}, {@code :}, whitespace).
 */
final class PackProperties {

    /** One surviving line-pair, with the location of the line it started on. */
    record Entry(String key, String value, SourceLocation location) {}

    private final List<Entry> entries;

    private PackProperties(List<Entry> entries) {
        this.entries = entries;
    }

    List<Entry> entries() {
        return entries;
    }

    /** Last-wins view, java.util.Properties semantics. Sorted keys for determinism. */
    Map<String, String> asMap() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Entry e : entries) {
            out.put(e.key(), e.value());
        }
        return out;
    }

    static PackProperties parse(PackPath file, List<SourcePreprocessor.ExpandedLine> lines,
                                List<Diagnostic> diagnostics) {
        List<Entry> entries = new ArrayList<>();
        StringBuilder pending = null;
        SourceLocation pendingAt = null;

        for (SourcePreprocessor.ExpandedLine el : lines) {
            String line = el.text();
            if (pending == null) {
                String stripped = stripLeadingWs(line);
                if (stripped.isEmpty() || stripped.startsWith("#") || stripped.startsWith("!")) {
                    continue; // comment
                }
                pending = new StringBuilder(stripped);
                pendingAt = new SourceLocation(el.file().toString(), el.line());
            } else {
                pending.append(stripLeadingWs(line));
            }
            if (continues(pending)) {
                pending.setLength(pending.length() - 1); // drop the trailing backslash
                continue;
            }
            emitEntry(file, pending.toString(), pendingAt, entries, diagnostics);
            pending = null;
        }
        if (pending != null) {
            emitEntry(file, pending.toString(), pendingAt, entries, diagnostics);
        }
        return new PackProperties(entries);
    }

    private static boolean continues(StringBuilder sb) {
        int trailing = 0;
        for (int i = sb.length() - 1; i >= 0 && sb.charAt(i) == '\\'; i--) {
            trailing++;
        }
        return (trailing & 1) == 1;
    }

    private static void emitEntry(PackPath file, String logical, SourceLocation at,
                                  List<Entry> entries, List<Diagnostic> diagnostics) {
        // java.util.Properties split: first unescaped '=', ':' or whitespace ends the key.
        int i = 0;
        while (i < logical.length()) {
            char c = logical.charAt(i);
            if (c == '=' || c == ':' || Character.isWhitespace(c)) {
                break;
            }
            if (c == '\\') {
                i++; // escaped char inside the key
            }
            i++;
        }
        String rawKey = logical.substring(0, i);
        while (i < logical.length() && Character.isWhitespace(logical.charAt(i))) {
            i++;
        }
        if (i < logical.length() && (logical.charAt(i) == '=' || logical.charAt(i) == ':')) {
            i++;
            while (i < logical.length() && Character.isWhitespace(logical.charAt(i))) {
                i++;
            }
        }
        String rawValue = logical.substring(i);
        if (rawKey.isEmpty()) {
            diagnostics.add(Diagnostic.error(DiagnosticCode.MALFORMED_PROPERTY_LINE, at,
                    "property line with no key"));
            return;
        }
        entries.add(new Entry(unescape(rawKey), unescape(rawValue.trim()), at));
    }

    /** Standard properties escapes: {@code \\t \\n \\r \\f \\\\ \\uXXXX}. Unknown escapes keep the char. */
    private static String unescape(String s) {
        if (s.indexOf('\\') < 0) {
            return s;
        }
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) {
                out.append(c);
                continue;
            }
            char n = s.charAt(++i);
            switch (n) {
                case 't' -> out.append('\t');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 'f' -> out.append('\f');
                case 'u' -> {
                    if (i + 4 < s.length()) {
                        try {
                            out.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException e) {
                            out.append('u');
                        }
                    } else {
                        out.append('u');
                    }
                }
                default -> out.append(n);
            }
        }
        return out.toString();
    }

    private static String stripLeadingWs(String s) {
        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return s.substring(i);
    }
}
