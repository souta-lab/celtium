package org.soutalab.celtium.shader.stareval.parser;


public record UnaryOp(String name) {


	@Override
	public String toString() {
		return this.name;
	}
}
