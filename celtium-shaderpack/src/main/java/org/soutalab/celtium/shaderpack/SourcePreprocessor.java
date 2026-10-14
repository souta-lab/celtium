package org.soutalab.celtium.shaderpack;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Line-oriented GLSL preprocessor for OptiFine/Iris-style packs.
 *
 * <p>Implements, with hard bounds and origin-tracked output:</p>
 * <ul>
 *   <li>{@code #include "x"} / {@code #include <x>} / {@code #include "/x"} -
 *       relative to the including file's directory or to {@code shaders/} for
 *       absolute form; quoted includes fall back to root resolution, matching
 *       OptiFine. Cycles are diagnosed with the full chain, depth and total
 *       expansion counts are capped;</li>
 *   <li>{@code #define}/{@code #undef} (object-like and function-like macros are
 *       recorded; function-like macros are <em>not</em> expanded inside text -
 *       that is the S2 compiler's job);</li>
 *   <li>{@code #if/#ifdef/#ifndef/#elif/#else/#endif} evaluated by
 *       {@link ExpressionEvaluator}. Only live lines reach the output;</li>
 *   <li>{@code #version}, {@code #extension}, {@code #pragma}, {@code #line}
 *       pass through; {@code #error} on a live branch is an error diagnostic.</li>
 * </ul>
 *
 * <p>Two modes: {@link Mode#GLSL} treats unknown {@code #word} lines as
 * warnings, {@link Mode#PROPERTIES} treats them as comments (packs write
 * {@code #something} comments into .properties files).</p>
 */
final class SourcePreprocessor {

    enum Mode { GLSL, PROPERTIES }

    /** One line of expanded output with its origin. */
    record ExpandedLine(PackPath file, int line, String text) {}

    /** Result of expanding one entry file. */
    static final class ExpandedSource {
        final List<ExpandedLine> lines = new ArrayList<>();
        /** Every identifier consulted by any conditional expression. */
        final Set<String> conditionIdentifiers = new TreeSet<>();
        /** Every file that contributed lines, in first-use order. */
        final Set<PackPath> filesTouched = new java.util.LinkedHashSet<>();
        /** Final object-like macro table (name to raw replacement), sorted. */
        Map<String, String> finalDefines = Map.of();
        boolean outputTruncated;
    }

    private final PackSource pack;
    private final PackLimits limits;
    private final List<Diagnostic> diagnostics;
    private final Mode mode;

    private long emittedChars;
    private int expansions;
    private boolean outputLimitHit;
    private boolean expansionLimitHit;

    SourcePreprocessor(PackSource pack, PackLimits limits, List<Diagnostic> diagnostics, Mode mode) {
        this.pack = pack;
        this.limits = limits;
        this.diagnostics = diagnostics;
        this.mode = mode;
    }

    /**
     * Expands {@code entry} under {@code seeds} (builtin defines such as
     * MC_VERSION plus the effective option set).
     */
    ExpandedSource expand(PackPath entry, Map<String, String> seeds) throws PackException {
        MacroState macros = new MacroState();
        seeds.forEach((name, value) -> macros.define(name, value, null));
        ExpandedSource out = new ExpandedSource();
        process(entry, out, macros, new ArrayDeque<>(), new GlslComments());
        TreeMap<String, String> defines = new TreeMap<>();
        macros.snapshot().forEach((name, macro) -> defines.put(name, macro.value()));
        out.finalDefines = Map.copyOf(defines);
        out.outputTruncated = outputLimitHit;
        return out;
    }

    // ------------------------------------------------------------------

    private void process(PackPath file, ExpandedSource out, MacroState macros,
                         Deque<PackPath> includeStack, GlslComments comments)
            throws PackException {
        if (includeStack.contains(file)) {
            StringBuilder chain = new StringBuilder();
            includeStack.forEach(p -> chain.append(p).append(" -> "));
            diagnostics.add(Diagnostic.error(DiagnosticCode.INCLUDE_CYCLE,
                    location(includeStack), "include cycle: " + chain + file));
            return;
        }
        if (includeStack.size() >= limits.maxIncludeDepth()) {
            diagnostics.add(Diagnostic.error(DiagnosticCode.INCLUDE_DEPTH_EXCEEDED,
                    location(includeStack), "include depth exceeds " + limits.maxIncludeDepth()));
            return;
        }

        byte[] raw;
        try {
            raw = pack.read(file);
        } catch (PackException e) {
            diagnostics.add(Diagnostic.error(DiagnosticCode.INCLUDE_NOT_FOUND,
                    location(includeStack), "cannot read " + file + " (" + e.getMessage() + ")"));
            return;
        }

        DecodeResult decoded = decode(raw, file);
        includeStack.addLast(file);
        out.filesTouched.add(file);
        try {
            Deque<CondFrame> conditions = new ArrayDeque<>();
            String[] lines = decoded.text().split("\n", -1);
            // a trailing newline ends the last real line, it does not start one
            int lineCount = lines.length;
            if (lineCount > 0 && lines[lineCount - 1].isEmpty() && decoded.text().endsWith("\n")) {
                lineCount--;
            }
            for (int i = 0; i < lineCount; i++) {
                String line = lines[i];
                if (line.endsWith("\r")) {
                    line = line.substring(0, line.length() - 1);
                }
                SourceLocation at = new SourceLocation(file.toString(), i + 1);
                if (line.length() > limits.maxLineLength()) {
                    diagnostics.add(Diagnostic.error(DiagnosticCode.LINE_LIMIT_EXCEEDED, at,
                            "line longer than " + limits.maxLineLength() + " chars"));
                    continue;
                }
                handleLine(line, at, file, out, macros, includeStack, conditions, comments);
            }
            while (!conditions.isEmpty()) {
                conditions.pop();
                diagnostics.add(Diagnostic.error(DiagnosticCode.UNBALANCED_CONDITIONAL,
                        new SourceLocation(file.toString(), lineCount),
                        "file ends inside an unclosed conditional"));
            }
        } finally {
            includeStack.removeLast();
        }
    }

    private void handleLine(String line, SourceLocation at, PackPath file, ExpandedSource out,
                            MacroState macros, Deque<PackPath> includeStack, Deque<CondFrame> conditions,
                            GlslComments comments)
            throws PackException {
        // Only the non-comment part of the line can carry a #directive; a
        // '#define' inside a /* */ doc block is documentation, not a directive.
        String code = comments.split(line).code();
        String trimmed = code.stripLeading();
        if (!trimmed.startsWith("#")) {
            if (isLive(conditions)) {
                emit(out, file, at.line(), line);
            }
            return;
        }

        // Directive word, e.g. 'include', 'if', 'define'.
        int cursor = 1;
        while (cursor < trimmed.length() && Character.isWhitespace(trimmed.charAt(cursor))) {
            cursor++;
        }
        int wordStart = cursor;
        while (cursor < trimmed.length() && (Character.isLetterOrDigit(trimmed.charAt(cursor)) || trimmed.charAt(cursor) == '_')) {
            cursor++;
        }
        String directive = trimmed.substring(wordStart, cursor);
        String argument = trimmed.substring(cursor).strip();

        switch (directive) {
            case "if" -> {
                boolean parentLive = isLive(conditions);
                boolean cond = parentLive && evalCondition(argument, at, out, macros);
                push(conditions, parentLive, cond);
            }
            case "ifdef" -> {
                boolean parentLive = isLive(conditions);
                String name = firstIdentifier(argument);
                boolean cond = parentLive && !name.isEmpty() && macros.isDefined(name);
                recordIdentifier(out, name);
                push(conditions, parentLive, cond);
            }
            case "ifndef" -> {
                boolean parentLive = isLive(conditions);
                String name = firstIdentifier(argument);
                boolean cond = parentLive && !name.isEmpty() && !macros.isDefined(name);
                recordIdentifier(out, name);
                push(conditions, parentLive, cond);
            }
            case "elif" -> {
                CondFrame frame = conditions.peek();
                if (frame == null || frame.sawElse) {
                    diagnostics.add(Diagnostic.error(DiagnosticCode.UNBALANCED_CONDITIONAL, at,
                            "#elif without a matching #if"));
                    return;
                }
                if (!frame.parentLive || frame.taken) {
                    frame.live = false;
                } else {
                    boolean cond = evalCondition(argument, at, out, macros);
                    frame.live = cond;
                    frame.taken |= cond;
                }
            }
            case "else" -> {
                CondFrame frame = conditions.peek();
                if (frame == null || frame.sawElse) {
                    diagnostics.add(Diagnostic.error(DiagnosticCode.UNBALANCED_CONDITIONAL, at,
                            "#else without a matching #if"));
                    return;
                }
                frame.sawElse = true;
                frame.live = frame.parentLive && !frame.taken;
                frame.taken = true;
            }
            case "endif" -> {
                if (conditions.isEmpty()) {
                    diagnostics.add(Diagnostic.error(DiagnosticCode.UNBALANCED_CONDITIONAL, at,
                            "#endif without a matching #if"));
                } else {
                    conditions.pop();
                }
            }
            case "include" -> {
                if (isLive(conditions)) {
                    handleInclude(argument, at, file, out, macros, includeStack, comments);
                }
            }
            case "define" -> {
                if (isLive(conditions)) {
                    handleDefine(argument, at, macros);
                }
            }
            case "undef" -> {
                if (isLive(conditions)) {
                    macros.undef(firstIdentifier(argument));
                }
            }
            case "error" -> {
                if (isLive(conditions)) {
                    diagnostics.add(Diagnostic.error(DiagnosticCode.PACK_ERROR_DIRECTIVE, at,
                            "pack #error: " + argument));
                }
            }
            case "version", "extension", "pragma", "line" -> {
                if (isLive(conditions)) {
                    emit(out, file, at.line(), line);
                }
            }
            case "" -> {
                // a bare '#' line: nothing
            }
            default -> {
                if (mode == Mode.GLSL) {
                    diagnostics.add(Diagnostic.warning(DiagnosticCode.UNKNOWN_DIRECTIVE, at,
                            "unhandled directive #" + directive));
                    if (isLive(conditions)) {
                        emit(out, file, at.line(), line);
                    }
                }
                // PROPERTIES mode: unknown '#word' is a comment, drop it.
            }
        }
    }

    private void handleInclude(String argument, SourceLocation at, PackPath includer,
                               ExpandedSource out, MacroState macros, Deque<PackPath> includeStack,
                               GlslComments comments)
            throws PackException {
        if (++expansions > limits.maxIncludeExpansions()) {
            if (!expansionLimitHit) {
                expansionLimitHit = true;
                diagnostics.add(Diagnostic.error(DiagnosticCode.EXPANSION_LIMIT_EXCEEDED, at,
                        "more than " + limits.maxIncludeExpansions() + " includes expanded"));
            }
            return;
        }
        if (argument.length() < 2) {
            diagnostics.add(Diagnostic.error(DiagnosticCode.MALFORMED_ENTRY_PATH, at,
                    "malformed #include " + argument));
            return;
        }
        char first = argument.charAt(0);
        char last = argument.charAt(argument.length() - 1);
        boolean quoted = first == '"' && last == '"';
        boolean angled = first == '<' && last == '>';
        if (!quoted && !angled) {
            diagnostics.add(Diagnostic.error(DiagnosticCode.MALFORMED_ENTRY_PATH, at,
                    "malformed #include " + argument));
            return;
        }
        String target = argument.substring(1, argument.length() - 1);
        try {
            PackPath resolved = angled || target.startsWith("/")
                    ? PackPath.absolute("/shaders" + (target.startsWith("/") ? target : "/" + target))
                    : PackPath.resolve(includer, target);
            if (!resolved.isUnder("/shaders/")) {
                diagnostics.add(Diagnostic.error(DiagnosticCode.INCLUDE_ESCAPE_REJECTED, at,
                        "include leaves shaders/: " + resolved));
                return;
            }
            if (!pack.exists(resolved) && quoted && !target.startsWith("/")) {
                // OptiFine fallback: a quoted include that misses relative to the
                // including file is retried from the shaders/ root.
                PackPath rootRelative = PackPath.absolute("/shaders/" + target);
                if (pack.exists(rootRelative)) {
                    resolved = rootRelative;
                }
            }
            if (!pack.exists(resolved)) {
                diagnostics.add(Diagnostic.error(DiagnosticCode.INCLUDE_NOT_FOUND, at,
                        "include not found: " + target + " (resolved " + resolved + ")"));
                return;
            }
            if (isBinaryName(resolved.name())) {
                diagnostics.add(Diagnostic.error(DiagnosticCode.BINARY_INCLUDE, at,
                        "include of non-text resource " + resolved));
                return;
            }
            process(resolved, out, macros, includeStack, comments);
        } catch (PackException e) {
            diagnostics.add(Diagnostic.error(DiagnosticCode.INCLUDE_ESCAPE_REJECTED, at,
                    "include rejected: " + e.getMessage()));
        }
    }

    private void handleDefine(String argument, SourceLocation at, MacroState macros) {
        // name[ (params)] [value]
        int i = 0;
        while (i < argument.length() && (Character.isLetterOrDigit(argument.charAt(i)) || argument.charAt(i) == '_')) {
            i++;
        }
        if (i == 0) {
            diagnostics.add(Diagnostic.warning(DiagnosticCode.MALFORMED_ENTRY_PATH, at,
                    "malformed #define " + argument));
            return;
        }
        String name = argument.substring(0, i);
        String rest = argument.substring(i);
        List<String> params = null;
        // Function-like only when '(' immediately follows the name.
        if (rest.startsWith("(")) {
            int close = rest.indexOf(')');
            if (close > 0) {
                params = List.of(rest.substring(1, close).split(","));
                rest = rest.substring(close + 1);
            }
        }
        macros.define(name, GlslComments.stripInline(rest).strip(), params);
    }

    // ------------------------------------------------------------------

    private boolean evalCondition(String expression, SourceLocation at, ExpandedSource out, MacroState macros) {
        ExpressionEvaluator evaluator = ExpressionEvaluator.parse(macros, expression);
        try {
            double value = evaluator.evaluate();
            out.conditionIdentifiers.addAll(evaluator.referencedIdentifiers());
            return value != 0.0;
        } catch (ExpressionEvaluator.EvalFailure e) {
            out.conditionIdentifiers.addAll(evaluator.referencedIdentifiers());
            diagnostics.add(Diagnostic.error(DiagnosticCode.EXPRESSION_ERROR, at,
                    "cannot evaluate '" + expression + "': " + e.getMessage()));
            return false;
        }
    }

    private void recordIdentifier(ExpandedSource out, String name) {
        if (!name.isEmpty()) {
            out.conditionIdentifiers.add(name);
        }
    }

    private static void push(Deque<CondFrame> conditions, boolean parentLive, boolean cond) {
        CondFrame frame = new CondFrame();
        frame.parentLive = parentLive;
        frame.taken = cond;
        frame.live = cond;
        conditions.push(frame);
    }

    private static boolean isLive(Deque<CondFrame> conditions) {
        return conditions.isEmpty() || conditions.peek().live;
    }

    private void emit(ExpandedSource out, PackPath file, int line, String text) {
        if (outputLimitHit) {
            return;
        }
        emittedChars += text.length() + 1;
        if (emittedChars > limits.maxExpandedBytesPerProgram()) {
            outputLimitHit = true;
            diagnostics.add(Diagnostic.warning(DiagnosticCode.OUTPUT_LIMIT_EXCEEDED,
                    new SourceLocation(file.toString(), line),
                    "expanded source exceeds " + limits.maxExpandedBytesPerProgram() + " chars; truncated"));
            return;
        }
        out.lines.add(new ExpandedLine(file, line, text));
    }

    private static SourceLocation location(Deque<PackPath> includeStack) {
        PackPath top = includeStack.peekLast();
        return top == null ? SourceLocation.file("/") : SourceLocation.file(top.toString());
    }

    private static String firstIdentifier(String text) {
        int i = 0;
        while (i < text.length()
                && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_' || text.charAt(i) == '$')) {
            i++;
        }
        return text.substring(0, i);
    }

    private static boolean isBinaryName(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        String ext = name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
        return switch (ext) {
            case "png", "jpg", "jpeg", "bin", "nbt", "dat", "zip", "class" -> true;
            default -> false;
        };
    }

    private record DecodeResult(String text, boolean bom, boolean malformed) {}

    private DecodeResult decode(byte[] raw, PackPath file) {
        String text;
        int offset = 0;
        if (raw.length >= 3 && raw[0] == (byte) 0xEF && raw[1] == (byte) 0xBB && raw[2] == (byte) 0xBF) {
            diagnostics.add(Diagnostic.warning(DiagnosticCode.BOM_PRESENT, SourceLocation.file(file.toString()),
                            "UTF-8 BOM stripped"));
            offset = 3;
        }
        byte[] body = offset == 0 ? raw : java.util.Arrays.copyOfRange(raw, offset, raw.length);
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(body)).toString();
        } catch (CharacterCodingException e) {
            diagnostics.add(Diagnostic.warning(DiagnosticCode.NON_UTF8_SOURCE,
                    SourceLocation.file(file.toString()), "not valid UTF-8, decoded with replacement"));
            text = new String(body, StandardCharsets.UTF_8);
        }
        return new DecodeResult(text, offset > 0, false);
    }

    private static final class CondFrame {
        boolean parentLive;
        boolean taken;
        boolean live;
        boolean sawElse;
    }
}
