package org.soutalab.celtium.shaderpack;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Bounded evaluator for preprocessor conditional expressions ({@code #if},
 * {@code #elif}) and for {@code program.<world>/<name>.enabled=<expr>}
 * properties, which use the same boolean/relational grammar.
 *
 * <p>Semantics: identifiers resolve through the macro table (object-like
 * macros expand recursively, bounded). Unknown identifiers evaluate to 0, as
 * in C, and are collected in {@link #unknownIdentifiers()} so the census can
 * report which option names the pack actually consults. {@code defined X} and
 * {@code defined(X)} are supported; a valueless {@code #define X} counts as 1.</p>
 *
 * <p>Numbers are evaluated as {@code double}; bit operators that require
 * integers ({@code ~}, {@code %}, {@code & | ^ << >>}) truncate operands to 64-bit longs, so flag masks stay exact.
 * Integer literals accept the C suffixes {@code u/U/l/L/ul/ull} (normalized, not passed to the parser) and hex
 * literals read unsigned; a literal beyond 64 bits is an {@link EvalFailure}, never a JVM fault. {@code >>} is
 * arithmetic, matching the GLSL preprocessor's signed integers. This is a documented superset of the integer-only
 * rule - this evaluator feeds planning decisions, not codegen.</p>
 */
final class ExpressionEvaluator {

    /** Error detail for a failed evaluation - never carries user input beyond the expression itself. */
    static final class EvalFailure extends Exception {
        EvalFailure(String message) {
            super(message);
        }
    }

    private static final int MAX_EXPANSION_DEPTH = 16;
    /** Parenthesis nesting bound: the grammar descends on '(', so depth is the recursion bound (audit #343). */
    private static final int MAX_PARSE_DEPTH = 128;

    /**
     * State one evaluation shares with every macro body it expands (audit #342): the recursion budget and the
     * in-progress name set are per-*evaluation*, not per-evaluator - a fresh ExpressionEvaluator per macro value is
     * how the old {@code depth} parameter reset to 0 and let A→B→A overflow the JVM stack.
     */
    private static final class EvalContext {
        private final Set<String> expansionStack = new java.util.HashSet<>();
        private int expansionDepth;
    }

    private final MacroState macros;
    private final EvalContext ctx;
    private final Set<String> unknownIdentifiers = new TreeSet<>();
    private final Set<String> referencedIdentifiers = new TreeSet<>();
    private final String text;
    private int pos;
    /**
     * When true the grammar still runs - the cursor, the nesting and the identifier census keep their meaning -
     * but arithmetic whose value cannot affect the outcome is not performed. This is how the lazy half of
     * {@code &&} and {@code ||} is kept lazy without an unchecked {@code 1 % 0} reaching the JVM (audit #343).
     */
    private boolean dead;
    private int parseDepth;

    private ExpressionEvaluator(MacroState macros, String text) {
        this(macros, text, false, new EvalContext());
    }

    private ExpressionEvaluator(MacroState macros, String text, boolean dead, EvalContext ctx) {
        this.macros = macros;
        this.text = text;
        this.dead = dead;
        this.ctx = ctx;
    }

    /**
     * @return the numeric result, or null if the expression is malformed.
     *         Unknown identifiers are collected on the evaluator instance.
     */
    static ExpressionEvaluator parse(MacroState macros, String expression) {
        return new ExpressionEvaluator(macros, expression);
    }

    Set<String> unknownIdentifiers() {
        return unknownIdentifiers;
    }

    /** Every identifier consulted by the expression - feeds the option census. */
    Set<String> referencedIdentifiers() {
        return referencedIdentifiers;
    }

    /** @throws EvalFailure on any malformed or unsupported construct */
    double evaluate() throws EvalFailure {
        double value = or();
        skipWhitespace();
        if (pos != text.length()) {
            throw new EvalFailure("trailing input at offset " + pos);
        }
        return value;
    }

    private double or() throws EvalFailure {
        double left = and();
        for (;;) {
            skipWhitespace();
            if (match("||")) {
                // Lazy: once left is true the right side's value cannot change the result. It is still parsed -
                // the cursor's advance, nested parentheses and the identifier census all stay real - but in dead
                // mode an unreachable `1 % 0` or overflowing literal produces no runtime fault (audit #343).
                double right = rightSide(this::and, left != 0.0);
                left = (left != 0.0 || right != 0.0) ? 1.0 : 0.0;
            } else {
                return left;
            }
        }
    }

    private double and() throws EvalFailure {
        double left = bitwiseOr();
        for (;;) {
            skipWhitespace();
            if (match("&&")) {
                double right = rightSide(this::bitwiseOr, left == 0.0);
                left = (left != 0.0 && right != 0.0) ? 1.0 : 0.0;
            } else {
                return left;
            }
        }
    }

    // C's bitwise precedence chain, between && and equality: | < ^ < & (audit #344). The operands truncate to
    // 64-bit longs so flag masks stay exact integers instead of floating-point approximations.
    private double bitwiseOr() throws EvalFailure {
        double left = bitwiseXor();
        for (;;) {
            skipWhitespace();
            if (matchBitwise('|')) {
                left = (long) left | (long) bitwiseXor();
            } else {
                return left;
            }
        }
    }

    private double bitwiseXor() throws EvalFailure {
        double left = bitwiseAnd();
        for (;;) {
            skipWhitespace();
            if (matchBitwise('^')) {
                left = (long) left ^ (long) bitwiseAnd();
            } else {
                return left;
            }
        }
    }

    private double bitwiseAnd() throws EvalFailure {
        double left = equality();
        for (;;) {
            skipWhitespace();
            if (matchBitwise('&')) {
                left = (long) left & (long) equality();
            } else {
                return left;
            }
        }
    }

    /**
     * A single-character bitwise operator match that must not eat the first half of {@code ||} or {@code &&} -
     * the logical levels above this grammar level see the two-character operator first (audit #344).
     */
    private boolean matchBitwise(char op) {
        if (pos >= text.length() || text.charAt(pos) != op) {
            return false;
        }
        if (pos + 1 < text.length() && text.charAt(pos + 1) == op) {
            return false;
        }
        pos++;
        return true;
    }

    /**
     * Parses one right operand, marking it dead when the result is already decided.
     *
     * <p>The flag is saved and restored around the parse so an {@link EvalFailure} deep inside cannot leak the
     * dead state into the enclosing operand's own arithmetic.
     */
    private double rightSide(Level level, boolean decided) throws EvalFailure {
        boolean saved = this.dead;
        this.dead = this.dead || decided;
        try {
            return level.parse();
        } finally {
            this.dead = saved;
        }
    }

    /** One grammar level so {@link #rightSide} can parse the operand a caller names without duplicating it. */
    private interface Level {
        double parse() throws EvalFailure;
    }

    private double equality() throws EvalFailure {
        double left = relational();
        for (;;) {
            skipWhitespace();
            if (match("==")) {
                left = left == relational() ? 1.0 : 0.0;
            } else if (match("!=")) {
                left = left != relational() ? 1.0 : 0.0;
            } else {
                return left;
            }
        }
    }

    private double relational() throws EvalFailure {
        double left = shift();
        for (;;) {
            skipWhitespace();
            if (match("<=")) {
                left = left <= shift() ? 1.0 : 0.0;
            } else if (match(">=")) {
                left = left >= shift() ? 1.0 : 0.0;
            } else if (match("<")) {
                left = left < shift() ? 1.0 : 0.0;
            } else if (match(">")) {
                left = left > shift() ? 1.0 : 0.0;
            } else {
                return left;
            }
        }
    }

    // Shift sits between relational and additive, as in C (audit #344). `>>` is arithmetic (the GLSL
    // preprocessor's integers are signed); Java masks the count to 0..63, which keeps the operation bounded.
    private double shift() throws EvalFailure {
        double left = additive();
        for (;;) {
            skipWhitespace();
            if (match("<<")) {
                left = (long) left << (long) additive();
            } else if (match(">>")) {
                left = (long) left >> (long) additive();
            } else {
                return left;
            }
        }
    }

    private double additive() throws EvalFailure {
        double left = multiplicative();
        for (;;) {
            skipWhitespace();
            if (match("+")) {
                left = left + multiplicative();
            } else if (match("-")) {
                left = left - multiplicative();
            } else {
                return left;
            }
        }
    }

    private double multiplicative() throws EvalFailure {
        double left = unary();
        for (;;) {
            skipWhitespace();
            if (match("*")) {
                left = left * unary();
            } else if (match("/")) {
                double divisor = unary();
                if (!this.dead) {
                    if (divisor == 0.0) {
                        // A thrown ArithmeticException used to escape the preprocessor: the caller only catches
                        // EvalFailure, so a raw JVM error crashed the pack load with no diagnostic (audit #343).
                        throw new EvalFailure("division by zero");
                    }
                    left = left / divisor;
                }
            } else if (match("%")) {
                double divisor = unary();
                if (!this.dead) {
                    long d = (long) divisor;
                    if (d == 0L) {
                        throw new EvalFailure("modulo by zero");
                    }
                    left = (long) left % d;
                }
            } else {
                return left;
            }
        }
    }

    private double unary() throws EvalFailure {
        skipWhitespace();
        if (match("!")) {
            return unary() == 0.0 ? 1.0 : 0.0;
        }
        if (match("-")) {
            return -unary();
        }
        if (match("+")) {
            return unary();
        }
        if (match("~")) {
            return ~(long) unary();
        }
        return primary();
    }

    private double primary() throws EvalFailure {
        skipWhitespace();
        if (pos >= text.length()) {
            throw new EvalFailure("unexpected end of expression");
        }
        char c = text.charAt(pos);
        if (c == '(') {
            pos++;
            if (++this.parseDepth > MAX_PARSE_DEPTH) {
                throw new EvalFailure("expression nested deeper than " + MAX_PARSE_DEPTH);
            }
            try {
                double inner = or();
                skipWhitespace();
                if (pos >= text.length() || text.charAt(pos) != ')') {
                    throw new EvalFailure("missing ')'");
                }
                pos++;
                return inner;
            } finally {
                this.parseDepth--;
            }
        }
        if (Character.isDigit(c) || (c == '.' && pos + 1 < text.length() && Character.isDigit(text.charAt(pos + 1)))) {
            return number();
        }
        if (isIdentifierStart(c)) {
            return identifier();
        }
        throw new EvalFailure("unexpected character '" + c + "'");
    }

    private double number() throws EvalFailure {
        int start = pos;
        if (text.startsWith("0x", pos) || text.startsWith("0X", pos)) {
            pos += 2;
            int digitsStart = pos;
            while (pos < text.length() && isHexDigit(text.charAt(pos))) {
                pos++;
            }
            String digits = text.substring(digitsStart, pos);
            String suffix = readNumericSuffix();
            if (suffix == null || digits.isEmpty()) {
                throw new EvalFailure("bad hex literal: " + text.substring(start, pos));
            }
            // Hex literals are always parsed unsigned: 0xFFFFFFFF is a valid mask, not a negative int (audit #344).
            return parseInteger(digits, 16, suffix, text.substring(start, pos));
        }
        boolean isFloat = false;
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (Character.isDigit(c)) {
                pos++;
            } else if (c == '.' || c == 'e' || c == 'E') {
                isFloat = true;
                pos++;
                if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')
                        && (text.charAt(pos - 1) == 'e' || text.charAt(pos - 1) == 'E')) {
                    pos++;
                }
            } else {
                break;
            }
        }
        String suffix = readNumericSuffix();
        if (suffix == null) {
            throw new EvalFailure("bad numeric suffix in literal: " + text.substring(start, pos));
        }
        String literal = text.substring(start, pos - suffix.length());
        if ("f".equalsIgnoreCase(suffix)) {
            // The float suffix marks the literal; it is not part of the digits Double.parseDouble would read
            // anyway, but accepting it here keeps 1.5f from failing the parse (audit #344).
            isFloat = true;
            suffix = "";
        }
        if (isFloat && !suffix.isEmpty()) {
            throw new EvalFailure("integer suffix on a float literal: " + text.substring(start, pos));
        }
        if (isFloat) {
            try {
                return Double.parseDouble(literal);
            } catch (NumberFormatException e) {
                throw new EvalFailure("bad numeric literal: " + literal);
            }
        }
        return parseInteger(literal, 10, suffix, text.substring(start, pos));
    }

    /**
     * Consumes the trailing integer/float suffix of a literal ({@code u/U/l/L/ul/ull/f/F} forms) and returns it,
     * or {@code null} for a malformed combination. A lone {@code f}/{@code F} is the float marker; every other form
     * mixes only {@code u}/{@code U} (at most one) and {@code l}/{@code L} (at most two).
     */
    private String readNumericSuffix() {
        int start = pos;
        while (pos < text.length() && "fFuUlL".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        String suffix = text.substring(start, pos);
        if (suffix.isEmpty() || "f".equalsIgnoreCase(suffix)) {
            return suffix;
        }
        int u = 0;
        int l = 0;
        for (int i = 0; i < suffix.length(); i++) {
            char c = suffix.charAt(i);
            if (c == 'u' || c == 'U') {
                u++;
            } else if (c == 'l' || c == 'L') {
                l++;
            } else {
                return null;
            }
        }
        if (u > 1 || l > 2) {
            return null;
        }
        return suffix;
    }

    /**
     * Parses an integer literal's digits and suffix into the evaluator's double value.
     *
     * <p>Width and signedness, stated once: a {@code u}/{@code U}-suffixed or hexadecimal literal is read as an
     * unsigned 64-bit value ({@link Long#parseUnsignedLong}); a plain decimal literal is a signed
     * {@link Long#parseLong}. Either way the result is a 64-bit pattern carried as double - values at the top of the
     * unsigned range wrap to their two's-complement reading, which bit operators then use exactly as C would. A
     * literal too long for 64 bits is an {@link EvalFailure}, never a JVM fault.
     */
    private double parseInteger(String digits, int radix, String suffix, String raw) throws EvalFailure {
        int maxDigits = radix == 16 ? 16 : (suffix.indexOf('u') >= 0 || suffix.indexOf('U') >= 0 ? 20 : 19);
        if (digits.isEmpty() || digits.length() > maxDigits) {
            throw new EvalFailure("integer literal out of 64-bit range: " + raw);
        }
        try {
            long bits = radix == 16 || suffix.indexOf('u') >= 0 || suffix.indexOf('U') >= 0
                    ? Long.parseUnsignedLong(digits, radix)
                    : Long.parseLong(digits, radix);
            return bits;
        } catch (NumberFormatException e) {
            throw new EvalFailure("bad numeric literal: " + raw);
        }
    }

    private double identifier() throws EvalFailure {
        int start = pos;
        while (pos < text.length() && isIdentifierPart(text.charAt(pos))) {
            pos++;
        }
        String name = text.substring(start, pos);
        if (name.equals("defined")) {
            skipWhitespace();
            String target;
            if (pos < text.length() && text.charAt(pos) == '(') {
                pos++;
                skipWhitespace();
                int tStart = pos;
                while (pos < text.length() && isIdentifierPart(text.charAt(pos))) {
                    pos++;
                }
                target = text.substring(tStart, pos);
                skipWhitespace();
                if (pos >= text.length() || text.charAt(pos) != ')') {
                    throw new EvalFailure("defined() missing ')'");
                }
                pos++;
            } else {
                int tStart = pos;
                while (pos < text.length() && isIdentifierPart(text.charAt(pos))) {
                    pos++;
                }
                target = text.substring(tStart, pos);
            }
            if (target.isEmpty()) {
                throw new EvalFailure("defined without an operand");
            }
            referencedIdentifiers.add(target);
            return macros.isDefined(target) ? 1.0 : 0.0;
        }

        referencedIdentifiers.add(name);
        MacroState.Macro macro = macros.get(name);
        if (macro == null) {
            unknownIdentifiers.add(name);
            return 0.0;
        }
        if (macro.functionLike()) {
            throw new EvalFailure("function-like macro " + name + "(...) in conditional is not evaluated");
        }
        String value = macro.value().trim();
        if (value.isEmpty()) {
            // #define NAME with no replacement participates in #if as 1 (compiler -DNAME convention).
            return 1.0;
        }
        // Textual substitution: the replacement is evaluated as an expression itself. The dead flag propagates so a
        // macro body inside an unreachable operand cannot raise the runtime fault the operand itself must not, and
        // the shared context carries the cycle stack and the depth budget across evaluators (audit #342): each
        // expansion is counted and un-counted in finally so a throwing body cannot corrupt an enclosing operand.
        if (!this.ctx.expansionStack.add(name)) {
            throw new EvalFailure("cyclic macro expansion through " + name);
        }
        this.ctx.expansionDepth++;
        try {
            if (this.ctx.expansionDepth > MAX_EXPANSION_DEPTH) {
                throw new EvalFailure("macro expansion too deep");
            }
            ExpressionEvaluator inner = new ExpressionEvaluator(macros, value, this.dead, this.ctx);
            double result;
            try {
                result = inner.evaluate();
            } catch (EvalFailure e) {
                unknownIdentifiers.addAll(inner.unknownIdentifiers);
                referencedIdentifiers.addAll(inner.referencedIdentifiers);
                throw e;
            }
            unknownIdentifiers.addAll(inner.unknownIdentifiers);
            referencedIdentifiers.addAll(inner.referencedIdentifiers);
            return result;
        } finally {
            this.ctx.expansionStack.remove(name);
            this.ctx.expansionDepth--;
        }
    }

    private void skipWhitespace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private boolean match(String token) {
        if (text.startsWith(token, pos)) {
            // For two-char operators make sure we don't split '!=' into '!' '='.
            pos += token.length();
            return true;
        }
        return false;
    }

    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private static boolean isIdentifierPart(char c) {
        return isIdentifierStart(c) || Character.isDigit(c);
    }

    private static boolean isHexDigit(char c) {
        return Character.isDigit(c) || "abcdefABCDEF".indexOf(c) >= 0;
    }

    /** Convenience: evaluates a boolean expression string against a macro map of option defaults. */
    static Boolean evalBoolean(String expression, java.util.Map<String, String> defines) {
        MacroState state = new MacroState();
        defines.forEach((name, value) -> state.define(name, value, null));
        ExpressionEvaluator evaluator = new ExpressionEvaluator(state, expression);
        try {
            return evaluator.evaluate() != 0.0;
        } catch (EvalFailure e) {
            return null;
        }
    }

    /** Every identifier an expression consults, whether or not it resolves. */
    static Set<String> collectIdentifiers(String expression) {
        MacroState empty = new MacroState();
        ExpressionEvaluator evaluator = new ExpressionEvaluator(empty, expression);
        try {
            evaluator.evaluate();
        } catch (EvalFailure ignored) {
            // identifiers gathered up to the failure are still meaningful
        }
        return evaluator.referencedIdentifiers();
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "ExpressionEvaluator[%s @%d]", text, pos);
    }
}
