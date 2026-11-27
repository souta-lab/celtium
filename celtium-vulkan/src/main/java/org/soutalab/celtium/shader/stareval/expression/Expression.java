package org.soutalab.celtium.shader.stareval.expression;

import org.soutalab.celtium.shader.stareval.function.FunctionContext;
import org.soutalab.celtium.shader.stareval.function.FunctionReturn;

import java.util.Collection;

public interface Expression {
	void evaluateTo(FunctionContext context, FunctionReturn functionReturn);

	default Expression partialEval(FunctionContext context, FunctionReturn functionReturn) {
		return this;
	}

	void listVariables(Collection<? super VariableExpression> variables);
}
