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
 * integers ({@code ~}, {@code %}) truncate operands. This is a documented
 * superset of the GLSL preprocessor's integer-only rule - this evaluator feeds
 * planning decisions, not codegen.</p>
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

    private final MacroState macros;
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
        this(macros, text, false);
    }

    private ExpressionEvaluator(MacroState macros, String text, boolean dead) {
        this.macros = macros;
        this.text = text;
        this.dead = dead;
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
        double left = equality();
        for (;;) {
            skipWhitespace();
            if (match("&&")) {
                double right = rightSide(this::equality, left == 0.0);
                left = (left != 0.0 && right != 0.0) ? 1.0 : 0.0;
            } else {
                return left;
            }
        }
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
        double left = additive();
        for (;;) {
            skipWhitespace();
            if (match("<=")) {
                left = left <= additive() ? 1.0 : 0.0;
            } else if (match(">=")) {
                left = left >= additive() ? 1.0 : 0.0;
            } else if (match("<")) {
                left = left < additive() ? 1.0 : 0.0;
            } else if (match(">")) {
                left = left > additive() ? 1.0 : 0.0;
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
            return identifier(0);
        }
        throw new EvalFailure("unexpected character '" + c + "'");
    }

    private double number() throws EvalFailure {
        int start = pos;
        if (text.startsWith("0x", pos) || text.startsWith("0X", pos)) {
            pos += 2;
            while (pos < text.length() && isHexDigit(text.charAt(pos))) {
                pos++;
            }
            try {
                return Long.parseLong(text.substring(start + 2, pos), 16);
            } catch (NumberFormatException e) {
                throw new EvalFailure("bad hex literal: " + text.substring(start, pos));
            }
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
            } else if (c == 'f' || c == 'F' || c == 'u' || c == 'U' || c == 'l' || c == 'L') {
                if (c == 'f' || c == 'F') {
                    isFloat = true;
                }
                pos++;
                break;
            } else {
                break;
            }
        }
        String literal = text.substring(start, pos);
        try {
            return isFloat ? Double.parseDouble(literal) : Long.parseLong(literal);
        } catch (NumberFormatException e) {
            throw new EvalFailure("bad numeric literal: " + literal);
        }
    }

    private double identifier(int depth) throws EvalFailure {
        if (depth > MAX_EXPANSION_DEPTH) {
            throw new EvalFailure("macro expansion too deep");
        }
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
        // macro body inside an unreachable operand cannot raise the runtime fault the operand itself must not.
        ExpressionEvaluator inner = new ExpressionEvaluator(macros, value, this.dead);
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
