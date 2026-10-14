package org.soutalab.celtium.shaderpack;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lexical discovery of user-facing shader options.
 *
 * <p>OptiFine convention: an option is a {@code #define NAME} (boolean) or
 * {@code #define NAME value} / {@code const type NAME = value} (valued), its
 * pack-as-shipped default is the literal written in the source, a commented-out
 * {@code //#define NAME} marks a boolean option currently off, and a trailing
 * {@code //[v1 v2 ...]} or {@code // [v1 v2 ...]} comment lists allowed
 * values.</p>
 *
 * <p>This scan is purely lexical: it does not evaluate conditionals, because
 * option defaults are exactly what the file text says. Declarations nested
 * inside a conditional block are recorded as {@code conditional} - present in
 * the census but not used as defaults. Text inside {@code /*} block comments is
 * skipped (packs document example {@code #define}s there), and internal
 * pipeline macros (VSH/FSH/OVERWORLD/...) are excluded from defaults.</p>
 */
final class OptionDiscovery {

    private static final Pattern DEFINE =
            Pattern.compile("^\\s*#\\s*define\\s+([A-Za-z_$][A-Za-z0-9_$]*)(\\(.*?\\))?(?:\\s+(\\S.*?))?\\s*$");
    private static final Pattern DISABLED_DEFINE =
            Pattern.compile("^\\s*//\\s*#\\s*define\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*(\\S.*)?$");
    private static final Pattern CONST_DECL =
            Pattern.compile("^\\s*const\\s+(?:int|float|bool)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*([^;]+?);?\\s*$");
    private static final Pattern COND_OPEN = Pattern.compile("^\\s*#\\s*(?:if|ifdef|ifndef)\\b");
    private static final Pattern COND_CLOSE = Pattern.compile("^\\s*#\\s*endif\\b");
    private static final Pattern VALUE_LIST = Pattern.compile("^\\s*\\[(.*)\\]\\s*$");

    /**
     * Internal pipeline macros, never user options: stage markers
     * (VSH/FSH/...), dimension markers (OVERWORLD/NETHER/END) and the builtin
     * condition symbols that arrive through LoadOptions seeds instead.
     */
    static final Set<String> INTERNAL_NAMES = Set.of(
            "VSH", "FSH", "GSH", "CSH", "TCS", "TES", "VERTEX", "FRAGMENT",
            "OVERWORLD", "NETHER", "END", "THE_END", "WORLD",
            "MC_VERSION", "MC_OS", "MC_GL_VERSION", "MC_GLSL_VERSION", "MC_VENDOR", "MC_RENDERER");

    /** One option declaration site. */
    record OptionDecl(String name, String value, boolean enabledByDefault,
                      boolean conditional, List<String> allowedValues, SourceLocation at) {}

    /**
     * Scans every shader-source file in the pack.
     *
     * @return name to declaration, sorted by name
     */
    static Map<String, OptionDecl> scan(PackSource pack, List<Diagnostic> diagnostics) {
        Map<String, OptionDecl> found = new LinkedHashMap<>();
        for (PackPath file : pack.files()) {
            String name = file.name();
            if (!isSourceName(name)) {
                continue;
            }
            byte[] raw;
            try {
                raw = pack.read(file);
            } catch (PackException e) {
                continue;
            }
            // Lexical scan only: decoding errors were/will be reported by the
            // real preprocessing pass; here malformed bytes just degrade to junk lines.
            String[] lines = new String(raw, StandardCharsets.UTF_8).split("\n", -1);
            int depth = 0;
            GlslComments comments = new GlslComments();
            for (int i = 0; i < lines.length; i++) {
                String rawLine = lines[i];
                boolean wasInBlock = comments.inBlock();
                GlslComments.Split split = comments.split(rawLine);
                String code = split.code();
                SourceLocation at = new SourceLocation(file.toString(), i + 1);
                if (COND_OPEN.matcher(code).find()) {
                    depth++;
                    continue;
                }
                if (COND_CLOSE.matcher(code).find()) {
                    depth = Math.max(0, depth - 1);
                    continue;
                }
                List<String> allowed = allowedValues(split.comment());
                // A '//#define NAME' is a disabled boolean toggle - but only
                // when the line is a real // comment, i.e. not text inside a
                // /* */ block (doc blocks carry literal example #define lines).
                String stripped = rawLine.stripLeading();
                boolean lineIsComment = !wasInBlock && stripped.startsWith("//") && code.strip().isEmpty();
                if (lineIsComment) {
                    Matcher disabled = DISABLED_DEFINE.matcher(stripped);
                    if (disabled.matches()) {
                        record(found, new OptionDecl(disabled.group(1), null, false, depth > 0,
                                allowed, at));
                    }
                    continue;
                }
                Matcher define = DEFINE.matcher(code);
                if (define.matches()) {
                    String declName = define.group(1);
                    boolean functionLike = define.group(2) != null; // '(' right after the name
                    if (!functionLike && !INTERNAL_NAMES.contains(declName)) {
                        String value = define.group(3);
                        record(found, new OptionDecl(declName,
                                value == null || value.isBlank() ? "1" : value.strip(), true, depth > 0,
                                allowed, at));
                    }
                    continue;
                }
                Matcher constDecl = CONST_DECL.matcher(code);
                if (constDecl.matches()) {
                    String declName = constDecl.group(1);
                    if (!INTERNAL_NAMES.contains(declName)) {
                        record(found, new OptionDecl(declName, constDecl.group(2).strip(), true, depth > 0,
                                allowed, at));
                    }
                }
            }
        }
        return new TreeMap<>(found);
    }

    private static List<String> allowedValues(String comment) {
        if (comment == null) {
            return List.of();
        }
        Matcher m = VALUE_LIST.matcher(comment);
        if (!m.matches()) {
            return List.of();
        }
        String body = m.group(1).strip();
        if (body.isEmpty() || body.contains("...")) {
            // '...' inside the bracket means "ranges omitted" - the list is not
            // exhaustive, so do not report it as an enumeration.
            return body.isEmpty() ? List.of() : List.of(body.split("\\s+"));
        }
        return List.of(body.split("\\s+"));
    }

    private static void record(Map<String, OptionDecl> found, OptionDecl decl) {
        // First declaration wins: settings files are written once and the
        // ordering is pack author intent. Duplicates are still in the census
        // through their sources, but the default is the first seen.
        found.putIfAbsent(decl.name(), decl);
    }

    private static boolean isSourceName(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        return switch (name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT)) {
            case "glsl", "vsh", "fsh", "gsh", "tcs", "tes", "csh" -> true;
            default -> false;
        };
    }
}
