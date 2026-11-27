/*
 * Derived from Iris / Angelica shader pack interpreter (LGPL-3.0).
 * Adapted natively for Celtium Vulkan 1.3: evaluates stareval custom uniforms in topological order
 * and exposes them for single-block std140 packing into FFM MemorySegment.
 */
package org.soutalab.celtium.shader.uniforms.custom;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.soutalab.celtium.shader.ShaderLogger;
import org.soutalab.celtium.shader.model.UniformHolder;
import org.soutalab.celtium.shader.parsing.IrisFunctions;
import org.soutalab.celtium.shader.parsing.IrisOptions;
import org.soutalab.celtium.shader.parsing.VectorType;
import org.soutalab.celtium.shader.stareval.element.ExpressionElement;
import org.soutalab.celtium.shader.stareval.expression.Expression;
import org.soutalab.celtium.shader.stareval.expression.VariableExpression;
import org.soutalab.celtium.shader.stareval.function.FunctionContext;
import org.soutalab.celtium.shader.stareval.function.FunctionReturn;
import org.soutalab.celtium.shader.stareval.function.Type;
import org.soutalab.celtium.shader.stareval.parser.Parser;
import org.soutalab.celtium.shader.stareval.resolver.ExpressionResolver;
import org.soutalab.celtium.shader.uniforms.custom.cached.CachedUniform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class CustomUniforms implements FunctionContext {
    private final Map<String, CachedUniform> variables = new Object2ObjectLinkedOpenHashMap<>();
    private final Map<String, Expression> variablesExpressions = new Object2ObjectLinkedOpenHashMap<>();
    private final Set<String> exportedUniformNames = new ObjectOpenHashSet<>();
    private final CustomUniformFixedInputUniformsHolder inputHolder;
    private final List<CachedUniform> uniformOrder;

    private CustomUniforms(CustomUniformFixedInputUniformsHolder inputHolder, Map<String, Builder.Variable> variableSpecs) {
        this.inputHolder = inputHolder;
        ExpressionResolver resolver = new ExpressionResolver(
                IrisFunctions.functions,
                name -> {
                    Type type = this.inputHolder.getType(name);
                    if (type != null) {
                        return type;
                    }
                    Builder.Variable variable = variableSpecs.get(name);
                    return variable != null ? variable.type : null;
                },
                true);

        for (Builder.Variable variable : variableSpecs.values()) {
            try {
                Expression expression = resolver.resolveExpression(variable.type, variable.expression);
                CachedUniform cachedUniform = CachedUniform.forExpression(variable.name, variable.type, expression, this);
                this.variables.put(cachedUniform.getName(), cachedUniform);
                this.variablesExpressions.put(cachedUniform.getName(), expression);
                if (variable.uniform) {
                    this.exportedUniformNames.add(variable.name);
                }
            } catch (Exception e) {
                ShaderLogger.LOGGER.warn("Failed to resolve custom uniform {}: {}", variable.name, e.getMessage());
            }
        }

        // Topological sort of custom variables based on dependency expressions
        Map<CachedUniform, List<CachedUniform>> requiredBy = new Object2ObjectOpenHashMap<>();
        Object2IntMap<CachedUniform> dependsOnCount = new Object2IntOpenHashMap<>();

        for (CachedUniform input : this.inputHolder.getAll()) {
            requiredBy.put(input, new ObjectArrayList<>());
        }
        for (CachedUniform variable : this.variables.values()) {
            requiredBy.put(variable, new ObjectArrayList<>());
        }

        FunctionReturn functionReturn = new FunctionReturn();
        Set<VariableExpression> requires = new ObjectOpenHashSet<>();
        Set<CachedUniform> brokenUniforms = new ObjectOpenHashSet<>();

        for (Map.Entry<String, Expression> entry : this.variablesExpressions.entrySet()) {
            requires.clear();
            entry.getValue().listVariables(requires);
            if (requires.isEmpty()) {
                continue;
            }
            CachedUniform uniform = this.variables.get(entry.getKey());
            List<CachedUniform> dependencies = new ArrayList<>();
            for (VariableExpression v : requires) {
                Expression evaluated = v.partialEval(this, functionReturn);
                if (evaluated instanceof CachedUniform cu) {
                    dependencies.add(cu);
                } else {
                    brokenUniforms.add(uniform);
                }
            }
            if (!dependencies.isEmpty()) {
                dependsOnCount.put(uniform, dependencies.size());
                for (CachedUniform dep : dependencies) {
                    requiredBy.computeIfAbsent(dep, k -> new ObjectArrayList<>()).add(uniform);
                }
            }
        }

        List<CachedUniform> ordered = new ArrayList<>();
        for (CachedUniform input : this.inputHolder.getAll()) {
            ordered.add(input);
        }
        for (CachedUniform variable : this.variables.values()) {
            if (dependsOnCount.getInt(variable) == 0 && !brokenUniforms.contains(variable)) {
                ordered.add(variable);
            }
        }

        for (int i = 0; i < ordered.size(); i++) {
            CachedUniform current = ordered.get(i);
            List<CachedUniform> dependents = requiredBy.get(current);
            if (dependents == null) {
                continue;
            }
            for (CachedUniform dep : dependents) {
                if (brokenUniforms.contains(dep)) {
                    continue;
                }
                int remaining = dependsOnCount.getInt(dep) - 1;
                dependsOnCount.put(dep, remaining);
                if (remaining == 0) {
                    ordered.add(dep);
                }
            }
        }

        this.uniformOrder = Collections.unmodifiableList(ordered);
    }

    public void updateAll() {
        for (CachedUniform uniform : uniformOrder) {
            uniform.update();
        }
    }

    public List<CachedUniform> getExportedUniforms() {
        List<CachedUniform> result = new ArrayList<>();
        for (CachedUniform uniform : uniformOrder) {
            if (exportedUniformNames.contains(uniform.getName())) {
                result.add(uniform);
            }
        }
        return result;
    }

    public CachedUniform getVariable(String name) {
        return variables.get(name);
    }

    @Override
    public boolean hasVariable(String name) {
        return variables.containsKey(name) || inputHolder.get(name) != null;
    }

    @Override
    public VariableExpression getVariableExpression(String name) {
        CachedUniform v = variables.get(name);
        if (v != null) {
            return v;
        }
        return inputHolder.get(name);
    }

    public static class Builder {
        private final Map<String, Variable> variables = new Object2ObjectLinkedOpenHashMap<>();

        public void addVariable(String type, String name, String expression, boolean isUniform) {
            if (variables.containsKey(name)) {
                ShaderLogger.LOGGER.warn("Duplicate custom uniform/variable: {}", name);
                return;
            }
            Type parsedType = parseType(type);
            if (parsedType == null) {
                ShaderLogger.LOGGER.warn("Unknown custom uniform type '{}' for '{}'", type, name);
                return;
            }
            try {
                ExpressionElement element = Parser.parse(expression, IrisOptions.options);
                variables.put(name, new Variable(parsedType, name, element, isUniform));
            } catch (Exception e) {
                ShaderLogger.LOGGER.warn("Failed to parse custom uniform {} = {}: {}", name, expression, e.getMessage());
            }
        }

        private static Type parseType(String type) {
            return switch (type) {
                case "bool" -> Type.Boolean;
                case "int" -> Type.Int;
                case "float" -> Type.Float;
                case "vec2" -> VectorType.VEC2;
                case "vec3" -> VectorType.VEC3;
                case "vec4" -> VectorType.VEC4;
                default -> null;
            };
        }

        public int variableCount() {
            return variables.size();
        }

        @SafeVarargs
        public final CustomUniforms build(Consumer<UniformHolder>... inputSuppliers) {
            CustomUniformFixedInputUniformsHolder.Builder holderBuilder = new CustomUniformFixedInputUniformsHolder.Builder();
            for (Consumer<UniformHolder> supplier : inputSuppliers) {
                if (supplier != null) {
                    supplier.accept(holderBuilder);
                }
            }
            return new CustomUniforms(holderBuilder.build(), variables);
        }

        record Variable(Type type, String name, ExpressionElement expression, boolean uniform) {
        }
    }
}
