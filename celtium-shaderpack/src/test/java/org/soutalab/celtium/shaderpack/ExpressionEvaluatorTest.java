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
