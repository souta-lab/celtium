# Native boundary: which GL entry points the game still calls (2026-10-28)

Leg `nb-amd` (AMD RX 6400, QMAGNET world, held to tick 620, validation off, PASSED), renderer "sha256": "b92962a2e69f0a0503f4a3be982d3087ecaf2236b363d552b4386e6d5e24666c",
`-Dceltium.glless.census=true`. Every count is a call of an LWJGL native that `CeltiumGllessNativeTransformer`
rewrote: none reaches an OpenGL driver (there is no GL context); each lands in `CeltiumGllessHint` and from there in
the native state mirror or a native draw route. Counts cover the whole run (load, menu, world), not one frame.

```
CELTIUM_TRANSFORM_CENSUS names=86 [nglLightfv=6397088 glTranslatef=5747875 glPushMatrix=1970227 glPopMatrix=1970227 glRotatef=1892315 glBindTexture=1288159 glScalef=1248117 glEnable=1072250 glDisable=1072246 nglTexGenfv=827235 nglLightModelfv=799636 glMultiTexCoord2f=746600 glTexParameteri=716971 nglMultMatrixf=606691 glLoadIdentity=487413 glMatrixMode=450576 glActiveTexture=376272 glColor4f=339852 glDepthMask=312762 nglGetFloatv=147591 glNormal3f=147090 glBlendFuncSeparate=126841 glBindBuffer=92016 glShadeModel=73603 nglTexSubImage2D=64670 glClear=55232 glGetError=46056 glBlendFunc=45967 glAlphaFunc=36805 glColorMaterial=36804 glEnableClientState=36802 glDisableClientState=36802 nglGetIntegerv=27678 glBindFramebuffer=27655 glViewport=27620 glOrtho=27616 glColorMask=20464 nglVertexPointer=18402 glClientActiveTexture=18400 glTranslated=10234]
```

Reading: the top of the list is fixed-function *state* (lights, matrices, texture binds, enables) issued by vanilla's
entity/TESR/item code around each model. Those draws already end in native producers (model replay, immediate
groups), but the producer is reached through the GL state call sequence rather than from the scene's own data — the
Stage 3 work in [plan.md](<../plan.md>) (TESR/entity/item native producers). Condition 2 is therefore **not met**: no
call reaches GL, but the GL call sequence is still the interface the game draws through.
