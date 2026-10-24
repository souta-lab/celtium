#version 450

// Celtium - the shadow map's fragment stage. Depth alone is the output, so this stage writes nothing; it exists because
// a graphics pipeline needs a fragment module once a fragment shader is declared anywhere in the layout.
void main() {
}
