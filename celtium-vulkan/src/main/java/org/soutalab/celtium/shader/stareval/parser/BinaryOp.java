package org.soutalab.celtium.shader.stareval.parser;


public record BinaryOp(String name, int priority) {


	@Override
	public String toString() {
		return this.name + "{" + this.priority + "}";
	}


}
