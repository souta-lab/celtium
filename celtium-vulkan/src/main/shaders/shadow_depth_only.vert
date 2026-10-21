#version 450
// The shadow map's proof-of-raster pass: one triangle over the whole map at clip depth 0.5.
//
// It used to be compiled at runtime through shaderc, and on a stack whose LWJGL is 3.4.3 the shaderc binding would not
// load at all (the leg environment carried 3.4.1), so the map was never created and the shadow legs silently had no
// shadow map. Built here, the module is an input of the build instead of a runtime dependency.
void main() {
  vec2 p = vec2(float((gl_VertexIndex << 1) & 2), float(gl_VertexIndex & 2));
  gl_Position = vec4(p * 2.0 - 1.0, 0.5, 1.0);
}
