package org.soutalab.celtium.shader.stareval.element.tree;

import org.soutalab.celtium.shader.stareval.element.ExpressionElement;
import org.soutalab.celtium.shader.stareval.parser.UnaryOp;

public record UnaryExpressionElement(UnaryOp op, ExpressionElement inner) implements ExpressionElement {


	@Override
	public String toString() {
		return "UnaryExpr{" + this.op + " {" + this.inner + "} }";
	}
}
