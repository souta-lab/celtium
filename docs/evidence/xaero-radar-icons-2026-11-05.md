# Xaero radar entity icons missing: static reading (2026-11-05, no leg)

Reported: E2E-E 1.87.3 leg, Xaero Minimap 25.3.13 radar shows no entity icons (e2e-cleanroom07 evidence, remaining (1)).

## Observed in the mod's bytecode (javap of `xaerominimap-forge-1.12.2-25.3.13.jar`)

- `RadarIconCreator` renders each entity model into an `ImprovedFramebuffer` and calls
  `ImprovedFramebuffer.setFramebufferTexture(int)` three times.
- `setFramebufferTexture(t)`: if `t` differs from the field `field_147617_g` and is non-zero, it calls
  `framebufferTexture2D(type, GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, t, 0)` - the same framebuffer object
  gets a different colour texture (the icon atlas texture, then its own one back).

## Observed in Celtium's code

- `CeltiumGllessHint.reportRawFramebuffer` and `celtium$adoptRawFramebufferColorTexture` derive the render-target
  identity from the framebuffer's GL name only (`RenderTargetRegistry.rawFramebufferIdentity(framebuffer)`), and the
  off-target image is one per identity.
- `PublishedFrameTextures.bindColorTexture` re-points that identity to the new texture and removes the previous
  texture's `identitiesByTexture` entry.

## Hypothesis (not measured)

GL keeps one storage per texture: draws while the atlas texture is attached land in the atlas, and re-attaching the
framebuffer's own texture does not touch the atlas. Celtium keeps one image per framebuffer, so (a) the draw for the
next attachment overwrites the icon in the same image, and (b) after the swap back the atlas texture has no identity,
so the radar's draws sampling it read the atlas's own (empty) mirror: no icons.

## Fix design and how to check it

Identity per colour texture for raw framebuffers that have one (the framebuffer name only when nothing is attached),
used by the bind report, the adoption path and the raw delete/orphan paths alike, so that the image lives with the
texture. It changes routing that the working minimap tiles depend on, so it is not made without a leg: E2E-E with
`CELTIUM_E2E_PACK=e2ee`, radar icons visible near mobs, the minimap tiles and the world map unchanged against the
current jar, validation 0.

## Implemented behind a switch (unverified)

`-Dceltium.vulkan.rawFboTextureIdentity=true` (default off, so the shipped routing is unchanged): the raw bind report
and the adoption path key the image by the attached colour texture (`RenderTargetRegistry.rawTargetIdentity`); a raw
framebuffer's deletion keeps the image, and the texture's deletion forgets it. Acceptance as written above, with the
switch on, before it can become the default.

## First legs on world-xaero (2026-11-06, light: 5 mods, qmagnet save, AMD, guiScale 3, HUD kept)

`l7xaero-off` / `l7xaero-on` (renderer `c3102913…`, switch off/on) both PASSED, validation 0; the GL reference
`l13xaero-ref` (Celeritas `567dcf8a…`, same fixture and pose) PASSED. Minimap crop, left to right: off, on, GL
([image](2026-11-06-xaero-radar-off-on-gl.png)):

- The markers on the grey band: GL draws small dark dots; **on** draws the same; off draws larger brown blobs. The
  switch moves this part toward the reference.
- Around the player arrow GL draws a red dot and thin green marks (nearby entities' icons). **Neither off nor on draws
  them** - this is the user's "radar entity icons missing", and the texture identity alone does not restore it.

Next: which draw produces those icons (the radar's per-entity icon quads sample the icon atlas the creator renders
into), and whether that atlas texture has a published image under the switch - `CELTIUM_RAW_FBO_ADOPTED` printed
nothing in either Celtium leg. The switch stays off by default.
