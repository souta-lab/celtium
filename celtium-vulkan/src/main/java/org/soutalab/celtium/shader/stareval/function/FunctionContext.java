package org.soutalab.celtium.shader.stareval.function;

import org.soutalab.celtium.shader.stareval.expression.Expression;

public interface FunctionContext {
	Expression getVariable(String name);

	boolean hasVariable(String name);
}
