# Candidate verification worlds

Exhibit worlds that could exercise broad content for the migration, recorded with their provenance and with the
conditions under which they may be used. Nothing here is adopted or downloaded yet; this file exists so the candidate
and its caveats are not lost or silently assumed.

## Rules for using any of these

- **Isolated instance.** A candidate world runs in its own Prism Launcher instance and its own run directory. It never
  replaces or contaminates the deterministic fixture the parity numbers come from (`validation/client`), because those
  numbers are only comparable while the scene and the capture point are fixed.
- **Provenance and licence checked before shipping anything.** Downloads are recorded with their source URL, file size
  and checksum, and a world is only redistributed or referenced in a release if its licence allows it.
- **Version honesty.** A world authored for a different Minecraft version is reported as such until it has actually been
  opened, saved and driven through the checklist on Cleanroom 1.12.2.
- **What it is for.** Coverage evidence: does every block, entity, item, particle and biome still render, and does
  nothing crash, hang or produce a validation message. It is not a parity instrument - parity comes from the fixture.

## QMAGNET's Test Map (candidate, not adopted)

| Field | Value |
|---|---|
| Source | shared by the user as reference information on 2026-10-09 |
| Distribution | https://www.curseforge.com/minecraft/worlds/qmagnets-test-map-for-resource-packs-and-map/files/2467609 |
| Author's description | exhibits blocks, mobs, items, particles, biomes and a day/night toggle |
| Declared version | 1.12 / 1.12.1 |
| Our runtime | Minecraft 1.12.2 on Cleanroom 0.6.13-alpha - **not yet verified by us** |
| Status | candidate; not downloaded, not opened, no checksum recorded |

Why it is interesting: the migration's stage 4 and 5 checklists are about content breadth - every block model, every
entity renderer, items and the hand, particles, the sky and weather, and the day/night lightmap path. A world that
deliberately places all of them is exactly the instrument those checklists need, and it is the kind of third-party
content that also tests the Forge API swap against something we did not write.

What has to happen before it is used: obtain it through the normal launcher flow, record size and checksum here, open
it in an isolated instance, confirm the world actually loads and saves on 1.12.2/Cleanroom, then run the checklist while
watching the backend's counters (`immediateOffered`, `immediateTaken`, `immediateRefusal`), the validation output, and
the frame itself. Any crash, hang, missing content or validation message is recorded here with the exact repro.
