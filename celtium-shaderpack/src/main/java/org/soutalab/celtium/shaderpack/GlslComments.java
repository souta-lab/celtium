package org.soutalab.celtium.shaderpack;

/**
 * Tracks {@code /* ... *&#47;} block comment state while splitting each line
 * into a "code" part and a "comment" part. Needed because real packs write
 * documentation blocks containing literal {@code #define} examples - a naive
 * scanner would treat comment text as directives (observed in BSL settings).
 *
 * <p>State is per file: C forbids comments crossing a translation-unit edge,
 * and an unterminated block comment in an include is a pack bug.</p>
 */
final class GlslComments {

    private boolean inBlockComment;

    /** True while inside a {@code /* ... *&#47;} span (checked before the line is split). */
    boolean inBlock() {
        return inBlockComment;
    }

    record Split(String code, String comment) {}

    /**
     * Splits one physical line. {@link #code()} is the text outside comments,
     * {@link #comment()} the {@code //} tail if present (for {@code //[...]}
     * allowed-value annotations).
     */
    Split split(String line) {
        StringBuilder code = new StringBuilder(line.length());
        StringBuilder comment = new StringBuilder();
        int i = 0;
        int n = line.length();
        while (i < n) {
            char c = line.charAt(i);
            if (inBlockComment) {
                if (c == '*' && i + 1 < n && line.charAt(i + 1) == '/') {
                    inBlockComment = false;
                    i += 2;
                } else {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < n) {
                char next = line.charAt(i + 1);
                if (next == '*') {
                    inBlockComment = true;
                    i += 2;
                    continue;
                }
                if (next == '/') {
                    comment.append(line, i + 2, n);
                    return new Split(code.toString(), comment.toString());
                }
            }
            code.append(c);
            i++;
        }
        return new Split(code.toString(), comment.toString());
    }

    /** Single-line variant for directive values: strips inline comments. */
    static String stripInline(String text) {
        int lineComment = indexOfLineComment(text);
        String noLine = lineComment < 0 ? text : text.substring(0, lineComment);
        // strip /* ... */ spans
        StringBuilder out = new StringBuilder(noLine.length());
        int i = 0;
        while (i < noLine.length()) {
            if (noLine.startsWith("/*", i)) {
                int close = noLine.indexOf("*/", i + 2);
                i = close < 0 ? noLine.length() : close + 2;
            } else {
                out.append(noLine.charAt(i++));
            }
        }
        return out.toString();
    }

    private static int indexOfLineComment(String text) {
        boolean block = false;
        for (int i = 0; i + 1 < text.length(); i++) {
            if (!block && text.charAt(i) == '/' && text.charAt(i + 1) == '*') {
                block = true;
                i++;
            } else if (block && text.charAt(i) == '*' && text.charAt(i + 1) == '/') {
                block = false;
                i++;
            } else if (!block && text.charAt(i) == '/' && text.charAt(i + 1) == '/') {
                return i;
            }
        }
        return -1;
    }
}
