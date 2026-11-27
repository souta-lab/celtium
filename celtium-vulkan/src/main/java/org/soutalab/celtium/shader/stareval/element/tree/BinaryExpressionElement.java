package org.soutalab.celtium.shader.stareval.element.tree;

import org.soutalab.celtium.shader.stareval.element.ExpressionElement;
import org.soutalab.celtium.shader.stareval.parser.BinaryOp;

public record BinaryExpressionElement(BinaryOp op, ExpressionElement left,
									  ExpressionElement right) implements ExpressionElement {


	@Override
	public String toString() {
		return "BinaryExpr{ {" + this.left + "} " + this.op + " {" + this.right + "} }";
	}
}
