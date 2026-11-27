package org.soutalab.celtium.shader.parsing;

import org.soutalab.celtium.shader.stareval.Util;
import org.soutalab.celtium.shader.stareval.expression.Expression;
import org.soutalab.celtium.shader.stareval.function.AbstractTypedFunction;
import org.soutalab.celtium.shader.stareval.function.FunctionContext;
import org.soutalab.celtium.shader.stareval.function.FunctionReturn;
import org.soutalab.celtium.shader.stareval.function.Type;

import java.util.Arrays;

public class VectorConstructor extends AbstractTypedFunction {

	public VectorConstructor(Type inner, int size) {
		super(
			new VectorType.ArrayVector(inner, size),
			Util.make(new Type[size], params -> Arrays.fill(params, inner))
		);
	}

	@Override
	public VectorType.ArrayVector getReturnType() {
		return (VectorType.ArrayVector) super.getReturnType();
	}

	@Override
	public void evaluateTo(Expression[] params, FunctionContext context, FunctionReturn
		functionReturn) {
		VectorType.ArrayVector vectorType = this.getReturnType();
		vectorType.map(params, context, functionReturn, (i, p, ctx, fr) -> p[i].evaluateTo(ctx, fr));
	}
}
