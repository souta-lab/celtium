package org.soutalab.celtium.shader.stareval.element.token;

import org.soutalab.celtium.shader.stareval.element.ExpressionElement;
import org.soutalab.celtium.shader.stareval.element.PriorityOperatorElement;
import org.soutalab.celtium.shader.stareval.element.tree.UnaryExpressionElement;
import org.soutalab.celtium.shader.stareval.parser.UnaryOp;

public class UnaryOperatorToken extends Token implements PriorityOperatorElement {
	private final UnaryOp op;

	public UnaryOperatorToken(UnaryOp op) {
		this.op = op;
	}

	@Override
	public String toString() {
		return "UnaryOp{" + this.op + "}";
	}

	@Override
	public int getPriority() {
		return -1;
	}

	@Override
	public UnaryExpressionElement resolveWith(ExpressionElement right) {
		return new UnaryExpressionElement(this.op, right);
	}
}
