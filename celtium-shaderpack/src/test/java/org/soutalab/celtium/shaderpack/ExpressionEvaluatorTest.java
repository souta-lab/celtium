package org.soutalab.celtium.shaderpack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audit #343: {@code &&}/{@code ||} keep C's lazy semantics while still parsing - and censusing - the
 * unreachable operand, and divisor/overflow faults surface as {@link ExpressionEvaluator.EvalFailure}
 * or a diagnostic, never as an unchecked {@link ArithmeticException}.
 */
class ExpressionEvaluatorTest {

    @Test
    void reachableModuloByZeroIsDiagnostic() {
        // Was ArithmeticException: / by zero escaping past every caller's EvalFailure catch.
        assertNull(ExpressionEvaluator.evalBoolean("1 % 0", Map.of()));
    }

    @Test
    void reachableDivisionByZeroIsDiagnostic() {
        assertNull(ExpressionEvaluator.evalBoolean("4 / (1 - 1)", Map.of()));
    }

    @Test
    void unreachableRhsDoesNotFault() {
        assertEquals(Boolean.FALSE, ExpressionEvaluator.evalBoolean("0 && (1 % 0)", Map.of()));
        assertEquals(Boolean.TRUE, ExpressionEvaluator.evalBoolean("1 || (1 % 0)", Map.of()));
        // Nested parens and a second level of deadness inside the unreachable operand.
        assertEquals(Boolean.FALSE, ExpressionEvaluator.evalBoolean("0 && ((2 - 2) / (1 - 1))", Map.of()));
    }

    @Test
    void deadnessReachesMacroBodies() {
        // The divisor fault lives inside the expansion; an unreachable operand must not fire it.
        assertEquals(Boolean.FALSE,
                ExpressionEvaluator.evalBoolean("0 && Z", Map.of("Z", "(1 % 0)")));
        assertEquals(Boolean.TRUE,
                ExpressionEvaluator.evalBoolean("1 || Z", Map.of("Z", "(1 % 0)")));
        // Reachable still reports it.
        assertNull(ExpressionEvaluator.evalBoolean("Z", Map.of("Z", "(1 % 0)")));
    }

    @Test
    void unreachableOperandStillFeedsTheCensus() {
        // The option census needs every identifier a pack consults, reachable or not.
        Set<String> names = ExpressionEvaluator.collectIdentifiers("1 || OPTION_A && OPTION_B");
        assertTrue(names.contains("OPTION_A"));
        assertTrue(names.contains("OPTION_B"));
        Set<String> skipped = ExpressionEvaluator.collectIdentifiers("0 && (SKIPPED_OPT == 3)");
        assertTrue(skipped.contains("SKIPPED_OPT"));
    }

    @Test
    void malformedLiteralsAreFailuresNotJvmFaults() {
        // Long overflow and a bare hex prefix both reach EvalFailure, not NumberFormatException.
        assertNull(ExpressionEvaluator.evalBoolean("9999999999999999999999", Map.of()));
        assertNull(ExpressionEvaluator.evalBoolean("0x", Map.of()));
    }

    @Test
    void parenDepthIsBounded() {
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            deep.append('(');
        }
        deep.append('1');
        for (int i = 0; i < 300; i++) {
            deep.append(')');
        }
        assertNull(ExpressionEvaluator.evalBoolean(deep.toString(), Map.of()));
    }

    @Test
    void ordinaryExpressionsStillEvaluate() {
        assertEquals(Boolean.TRUE, ExpressionEvaluator.evalBoolean("1 + 2 == 3", Map.of()));
        assertEquals(Boolean.TRUE, ExpressionEvaluator.evalBoolean("0 && 1 || 1", Map.of()));
        assertEquals(Boolean.TRUE, ExpressionEvaluator.evalBoolean("defined(OPT) && OPT == 3",
                Map.of("OPT", "3")));
        // An unreachable malformed-*arithmetic* operand passes; an unreachable *syntax* fault still fails.
        assertNull(ExpressionEvaluator.evalBoolean("0 && (1 % )", Map.of()));
    }

    // ---- audit #342: cyclic object-like macros reach EvalFailure, never StackOverflowError ----

    @Test
    void selfCycleIsDiagnostic() {
        assertNull(ExpressionEvaluator.evalBoolean("A", Map.of("A", "A")));
    }

    @Test
    void twoMacroCycleIsDiagnostic() {
        assertNull(ExpressionEvaluator.evalBoolean("A", Map.of("A", "B", "B", "A")));
    }

    @Test
    void cycleInsideAnOperandIsDiagnostic() {
        assertNull(ExpressionEvaluator.evalBoolean("A || 1", Map.of("A", "B", "B", "A")));
    }

    @Test
    void acyclicChainsEitherSideOfDepthLimit() {
        // A chain one longer than MAX_EXPANSION_DEPTH fails as a diagnostic; one under it evaluates.
        Map<String, String> deep = new java.util.HashMap<>();
        for (int i = 0; i < 20; i++) {
            deep.put("M" + i, "M" + (i + 1));
        }
        deep.put("M20", "1");
        assertNull(ExpressionEvaluator.evalBoolean("M0", deep));

        Map<String, String> shallow = new java.util.HashMap<>();
        for (int i = 0; i < 8; i++) {
            shallow.put("S" + i, "S" + (i + 1));
        }
        shallow.put("S8", "7");
        assertEquals(Boolean.TRUE, ExpressionEvaluator.evalBoolean("S0 == 7", shallow));
    }

    @Test
    void unrelatedExpressionUnaffectedByCycleGuard() {
        assertEquals(Boolean.TRUE, ExpressionEvaluator.evalBoolean("A + B == 3",
                Map.of("A", "1", "B", "2")));
    }

    // ---- audit #344: integer suffixes and bitwise/shift operators ----

    @Test
    void integerSuffixesParse() {
        assertEquals(Boolean.TRUE, ExpressionEvaluator.evalBoolean("1u == 1", Map.of()));
        assertEquals(Boolean.TRUE, ExpressionEvaluator.evalBoolean("2ul == 2", Map.of()));
        assertEquals(Boolean.TRUE, ExpressionEvaluator.evalBoolean("0x10u == 16", Map.of()));
        assertEquals(Boolean.TRUE, ExpressionEvaluator.evalBoolean("0xFF == 255", Map.of()));
        assertEquals(Boolean.TRUE, ExpressionEvaluator.evalBoolean("0xFFFFFFFF == 4294967295", Map.of()));
        assertEquals(Boolean.TRUE, ExpressionEvaluator.evalBoolean("1.5f == 1.5", Map.of()));
    }

    @Test
    void malformedLiteralsStayFailures() {
        assertNull(ExpressionEvaluator.evalBoolean("1uu", Map.of()));
        assertNull(ExpressionEvaluator.evalBoolean("1lll", Map.of()));
        assertNull(ExpressionEvaluator.evalBoolean("1.5u", Map.of()));
        assertNull(ExpressionEvaluator.evalBoolean("0x", Map.of()));
    }

    @Test
    void bitwiseAndShiftOperators() {
        assertEquals(Boolean.TRUE, ExpressionEvaluator.evalBoolean("1 << 4 == 16", Map.of()));
        assertEquals(Boolean.TRUE, ExpressionEvaluator.evalBoolean("16 >> 2 == 4", Map.of()));
        assertEquals(Boolean.TRUE, ExpressionEvaluator.evalBoolean("(0xF0 & 0x0F) == 0", Map.of()));
        assertEquals(Boolean.TRUE, ExpressionEvaluator.evalBoolean("(0xF0 | 0x0F) == 255", Map.of()));
        assertEquals(Boolean.TRUE, ExpressionEvaluator.evalBoolean("(0xFF ^ 0x0F) == 240", Map.of()));
        assertEquals(Boolean.TRUE, ExpressionEvaluator.evalBoolean("~0 == -1", Map.of()));
    }

    @Test
    void bitwisePrecedenceMatchesC() {
        // | binds tighter than &&: 1 && (0 | 1) = 1. == binds tighter than &: 1 & (3 == 1) = 0.
        assertEquals(Boolean.TRUE, ExpressionEvaluator.evalBoolean("1 && 0 | 1", Map.of()));
        assertEquals(Boolean.FALSE, ExpressionEvaluator.evalBoolean("1 & 3 == 1", Map.of()));
        assertEquals(Boolean.TRUE, ExpressionEvaluator.evalBoolean("(1 & 3) == 1", Map.of()));
        // Shifts sit between relational and additive: (1 + 2) << 1 == 6.
        assertEquals(Boolean.TRUE, ExpressionEvaluator.evalBoolean("1 + 2 << 1 == 6", Map.of()));
        // Relational binds tighter than shift the other way is false in C: 1 << 2 + 1 = 8.
        assertEquals(Boolean.TRUE, ExpressionEvaluator.evalBoolean("1 << 2 + 1 == 8", Map.of()));
    }

    @TempDir
    Path tmp;

    @Test
    void preprocessorIfLineUsesLazySemantics() throws Exception {
        // #if goes through SourcePreprocessor.evalCondition: an unreachable arithmetic fault used to surface as an
        // unchecked JVM error; it now stays silent, and a *reachable* one is a structured diagnostic.
        PackSource pack = PackSource.open(
                FixturePack.writeZip(tmp.resolve("p.zip"), Map.of("shaders/x.vsh",
                        "#if 0 && (1 % 0)\n"
                                + "dead\n"
                                + "#elif 1 % 0\n"
                                + "also dead\n"
                                + "#else\n"
                                + "live\n"
                                + "#endif\n")),
                PackLimits.DEFAULT, new ArrayList<>());
        List<Diagnostic> diags = new ArrayList<>();
        SourcePreprocessor.ExpandedSource out = new SourcePreprocessor(pack, PackLimits.DEFAULT, diags,
                SourcePreprocessor.Mode.GLSL).expand(PackPath.absolute("/shaders/x.vsh"), Map.of());
        List<String> texts = new ArrayList<>();
        for (SourcePreprocessor.ExpandedLine line : out.lines) {
            texts.add(line.text());
        }
        assertTrue(texts.contains("live"));
        assertFalse(texts.contains("dead"));
        assertFalse(texts.contains("also dead"));
        // The reachable #elif fault produced a diagnostic rather than a thrown JVM error.
        assertTrue(diags.stream().anyMatch(d -> d.message().contains("modulo by zero")
                || d.message().contains("1 % 0")));
    }
}
