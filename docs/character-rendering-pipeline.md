---
title: Character Rendering Pipeline
description: Agent-oriented process for turning character concept art into 2D isometric sprites and animations.
agent_notes: Treat docs/art-style.md and docs/characters.md as immutable sources of truth. Do not edit them while implementing this pipeline.
---

# Character Rendering Pipeline

This document defines how a coding agent should turn Llamanation character
concepts into production-ready animated sprites. It is both a production guide
and an integration contract between art-generation tooling and the Canvas
renderer.

The central constraint is that Llamanation only *looks* three-dimensional. The
runtime is strictly 2D, uses a fixed isometric view, and permits camera panning
and zooming but never camera rotation. A 3D package such as Blender may be used
to author and render assets offline; no 3D models, rigs, cameras, materials, or
lights are shipped as part of the game runtime.

## Sources of truth

Read these files before changing the pipeline or producing assets:

1. [`docs/art-style.md`](art-style.md) defines the fixed isometric presentation,
   practical viewing distance, historical inspiration, and light-hearted tone.
2. [`docs/characters.md`](characters.md) defines the units and their gameplay
   identities.
3. [`art/concept-art/`](../art/concept-art/) contains visual-development
   references for the current character lineup.
4. [`PLAN.md`](../PLAN.md) defines the Canvas 2D renderer and authoritative
   simulation boundaries.

The first two documents explicitly prohibit agent edits. If a requested asset
conflicts with them, report the conflict rather than changing those documents.
Concept art establishes appearance and mood, but gameplay and art-style
documents take precedence when references disagree.

## Non-negotiable runtime contract

- Runtime characters are transparent 2D sprite frames.
- The camera projection and viewing angle are fixed for the entire game.
- Units may face different directions; the camera does not rotate around them.
- Lighting direction is consistent across units, buildings, terrain, and props.
- Unit world position is represented by a ground or foot anchor, never the
  center of the sprite image.
- Animation is presentation only. It does not apply damage, spend resources,
  complete construction, or otherwise mutate authoritative simulation state.
- Selection rings, health bars, command indicators, and other UI feedback are
  renderer layers and are not baked into character sprites.
- Team-identifying color must remain readable at the normal gameplay zoom.

## Decisions required before final rendering

Do not produce a full set of production sprites until a render profile has been
chosen and checked into the repository. The art-style document intentionally
does not specify exact numeric values, so an agent must not silently invent
them during a batch render.

The render profile must define at least:

- Projection type and tile width-to-height ratio
- Tile width and height in logical pixels at 100% zoom
- Orthographic camera yaw and pitch
- World-axis and screen-direction conventions
- Standard unit frame width and height
- High-resolution render scale used before downsampling
- Global light direction, softness, and color
- Supported facing directions and their canonical identifiers
- Ground-anchor convention
- Default, minimum, and maximum gameplay zoom
- Canvas image-smoothing behavior at each supported zoom range
- Team-color strategy
- Shadow strategy

A conventional 2:1 diamond projection is a reasonable prototype candidate, but
it is not a requirement until explicitly adopted. The exact projection used by
the renderer, tile art, and offline rendering stage must be identical.

Keep the profile data-driven and versioned. A future implementation should use
a small machine-readable file under `art/` so both export tooling and renderer
tests can consume the same values. Avoid duplicating camera or frame constants
across Blender scripts, shell scripts, and TypeScript.

## Asset stages and directory conventions

Use these stages to distinguish manually authored inputs from regenerable and
runtime artifacts:

```text
art/
  concept-art/                    # Existing appearance references
  source/characters/<unit-id>/    # Models, rigs, textures, and source scenes
  generated/characters/<unit-id>/ # Regenerable frame renders and QA output

frontend/public/assets/characters/<unit-id>/
  atlas.png                       # Production color frames
  team-mask.png                   # Optional aligned team-color mask
  shadow-atlas.png                # Optional aligned shadow frames
  manifest.json                   # Frames, clips, timing, anchors, and events
```

If the eventual asset loader establishes a different runtime directory, update
this document and migrate the convention in one deliberate change. Do not
create multiple competing asset layouts.

Use stable lowercase kebab-case unit identifiers:

- `llama`
- `golden-retriever`
- `corgi`
- `dachshund`
- `beaver`
- `groundhog`

Never overwrite concept art or manually authored model sources with generated
output. Large intermediate renders may be excluded from version control if they
are deterministic and inexpensive to reproduce, but production atlases,
manifests, and the commands needed to rebuild them must be available to the
project.

## Production workflow

### 1. Confirm the visual and gameplay contracts

Before making an asset, an agent must:

1. Read the sources of truth listed above.
2. Identify the unit's gameplay role and required actions.
3. Locate the active render profile.
4. Check existing naming, manifests, and loader expectations.
5. Inspect the worktree and preserve unrelated user changes.

If the render profile is missing or incomplete, a production task is blocked on
an art-direction or renderer decision. A low-cost prototype may still be made,
but it must be labeled as such and must not be presented as the final asset
contract.

### 2. Convert concept art into production references

The images in `art/concept-art/` are high-resolution, single-view mockups. Use
them to guide silhouette, palette, equipment, materials, and personality. Do not
resize them directly into sprites because they do not provide all directions
and may not match the final camera projection.

Prepare a turnaround or equivalent modeling reference that covers front, rear,
side, and three-quarter views. Resolve hidden equipment deliberately instead of
allowing different render directions to contradict one another.

Simplify details for RTS scale:

- Preserve features that identify the species and role.
- Combine small straps, buckles, and tools into larger readable shapes.
- Exaggerate the llama's neck, corgi's ears, dachshund's length, beaver's tail,
  and groundhog's compact digging silhouette.
- Reserve a clear, sufficiently large area for team color.
- Prefer brighter, cleaner color separation over realistic low-contrast detail.

Always review the design at the intended gameplay size and alongside several
other units. A detail that only reads when the image is enlarged is not a useful
gameplay detail.

### 3. Build the offline source asset

A rigged 3D model is the preferred approach for consistent directions and a
growing animation library. Hand-painted 2D animation is also valid when it can
meet the same projection, direction, anchor, and export contracts.

For a 3D source:

- Use stylized geometry and hand-authored textures rather than photorealistic
  materials.
- Use one maintainable quadruped rig per compatible body plan where practical,
  without forcing unrelated animals into a fragile shared rig.
- Rig ears, tail, and important equipment for secondary motion.
- Keep upgradeable equipment as separate objects when layering or swapping it
  will reduce duplicated work.
- Keep the ground plane and foot placement consistent with the shared camera
  scene.
- Reference or link the shared camera and lighting setup instead of recreating
  it independently in every character scene.

Corgi environmental upgrades, llama cargo, and golden-retriever support loads
are good candidates for modular attachments. Prefer separate aligned layers
when they remain visually correct in every direction; otherwise render complete
variants.

### 4. Author animation clips

Every production unit needs these baseline clips:

- `idle`
- `move`
- `death`

Add clips based on gameplay rather than forcing every unit into the same list:

| Unit | Specialized clips |
| --- | --- |
| Llama | `gather`, `construct`, and a cargo-carrying move variant |
| Golden retriever | `assist`, `load`, and `unload` |
| Corgi | `attack` and `upgrade`, with the upgrade shown as an agility course |
| Dachshund | `attack`, `burrow-enter`, and `burrow-exit` |
| Beaver | `construct` and water-engineering work actions; add water movement only if gameplay requires it |
| Groundhog | `dig`, `excavate`, and `construct` |

Add a `hit` reaction only if it remains readable and does not make groups of
units visually noisy. Do not invent gameplay abilities merely to justify an
animation.

Use restrained exaggeration to support the light-hearted tone: ear and fleece
bounce, eager acceleration, a compact wind-up before burrowing, a beaver tail
punctuating work, or a small dirt burst. Favor readable timing and poses over
subtle realism. Keep feet planted during stationary actions and prevent visible
foot sliding during movement cycles.

Frame counts and playback rates may differ by clip. Record them in the manifest
instead of embedding assumptions in renderer code.

### 5. Render directional frames

Keep the shared orthographic camera fixed and rotate the character around the
world's vertical axis. Render the canonical eight directions:

```text
n, ne, e, se, s, sw, w, nw
```

The manifest must map direction identifiers to frame locations explicitly; the
renderer must not depend on an enum's incidental numeric order.

Render all eight directions for characters with asymmetric tools, armor, or
markings. Mirroring may only be used when the visual result is genuinely
equivalent and the manifest records that choice. Do not allow a mirrored frame
to move a tool from one physical side of the character to the other.

Each frame must:

- Use the same camera, light, and ground plane as every other world asset.
- Fit inside the standard frame without cropped ears, feet, tails, tools, or
  effects.
- Preserve a stable ground anchor throughout the clip.
- Have true alpha transparency outside the rendered subject.
- Avoid baked UI, labels, selection graphics, and health indicators.

### 6. Render aligned passes

The normal output is a lit RGBA character pass. Depending on the render profile,
also produce:

- A team-color mask aligned pixel-for-pixel with the color pass
- A separate ground-shadow pass
- Separate equipment or cargo layers when the modular approach was approved

Use the team mask to tint cloth or equipment without recoloring fur, eyes,
metal, or other identity-bearing details. The final team-colored area must be
recognizable at gameplay zoom, including when units overlap.

A separate shadow pass is preferable when it lets the renderer control opacity
and terrain interaction consistently. Whether separate or baked, every shadow
must follow the same global light direction. Do not add a rectangular or opaque
ground tile to character frames.

### 7. Downsample and post-process

Render above the target resolution and downsample through one controlled,
repeatable process. This is where the late-1990s pre-rendered quality should
emerge: modeled volume and lighting with slightly pixelated edges, not modern
photorealism and not full pixel art.

The export process should:

1. Downsample to the target logical resolution.
2. Preserve alpha without colored fringes.
3. Apply consistent sharpening or palette treatment if specified by the render
   profile.
4. Detect cropping and frame-size mismatches.
5. Preserve exact alignment between color, mask, shadow, and equipment passes.

Do not blindly apply nearest-neighbor filtering. It may be too harsh for the
desired style. Select the export filter deliberately, then keep runtime scaling
from accidentally undoing that treatment.

### 8. Pack sprites and write metadata

Pack frames deterministically so the same sources and tool versions produce the
same atlas layout. The manifest is the stable interface; renderer code should
not infer clip boundaries from filenames or atlas dimensions.

A manifest should express the following information, although its final schema
must match the implemented loader:

```json
{
  "schemaVersion": 1,
  "unitId": "corgi",
  "frameSize": { "width": 128, "height": 128 },
  "anchor": { "x": 64, "y": 106 },
  "directions": ["n", "ne", "e", "se", "s", "sw", "w", "nw"],
  "clips": {
    "move": {
      "framesPerDirection": 8,
      "fps": 10,
      "loop": true
    },
    "attack": {
      "framesPerDirection": 8,
      "fps": 10,
      "loop": false,
      "events": { "release": 5 }
    }
  }
}
```

The numbers above illustrate structure and are not project defaults. The render
profile and actual clip determine their values.

Animation event markers synchronize presentation, such as a projectile release
or dirt burst, with the renderer's playback. They must never become the source
of truth for combat damage, burrow state, resource transfer, construction
completion, or other simulation behavior.

### 9. Integrate with the Canvas renderer

The renderer converts world coordinates to screen coordinates using the active
projection. A common diamond projection has this shape:

```text
screenX = (worldX - worldY) * tileWidth / 2
screenY = (worldX + worldY) * tileHeight / 2 - elevation
```

This formula is illustrative until the render profile adopts an exact
projection. Keep projection code centralized and unit tested.

Place each frame using its ground anchor. Depth-sort ordinary units by projected
ground position, with a stable entity identifier as a tie-breaker to prevent
flicker. Large buildings or terrain features may require back and foreground
layers or explicit occluder data so units can pass behind one portion and in
front of another.

Choose clips from the client-visible simulation state, then advance their
presentation time independently of the server tick. Snapshot interpolation may
move a character smoothly between authoritative positions, but animation must
not predict or modify canonical gameplay outcomes.

Camera pan changes the screen offset. Camera zoom scales the 2D composition but
never changes its angle. Pixel density, device pixel ratio, fractional zoom, and
Canvas image smoothing must be handled deliberately and tested at supported
zoom levels. Only introduce multiple atlas resolutions if measurements show
that one resolution cannot meet quality and memory requirements.

## Automation expectations

Prefer a reproducible, headless export command over a sequence of manual UI
operations. When implementing automation:

- Put project-owned scripts under a clear `art/tools/` or equivalent directory.
- Add a file-level comment explaining how each script fits into this pipeline.
- Keep camera, lighting, frame, and direction values in the shared render
  profile rather than copying constants into scripts.
- Pin or record the Blender and asset-tool versions needed for reproducibility.
- Make a single-unit render cheap to run while developing.
- Make the full batch explicit; do not regenerate every unit as a side effect of
  an unrelated frontend build.
- Fail with actionable messages for missing clips, dimensions, alpha, manifests,
  or external tools.
- Never overwrite manually authored source files.

Generate one representative vertical slice, initially the llama, before
automating all six units. Use it to validate projection, apparent scale, anchor,
direction mapping, team color, shadow behavior, atlas loading, and zoom. Batch
production begins only after that slice is accepted.

AI image generation may help explore concepts or produce temporary references.
Do not generate animation frames independently and treat them as production
sprites: anatomy, equipment, perspective, lighting, and alignment will drift
between frames. Production frames need a shared model, rig, or equally
controlled 2D source.

## Validation and acceptance criteria

An agent should not describe an asset as production-ready until all relevant
checks pass.

### Automated checks

- Every referenced file exists and its filename matches the manifest.
- Every atlas is valid RGBA data with the expected dimensions.
- Every frame has the configured width and height.
- No nonempty pixels cross frame boundaries.
- Color, mask, shadow, and equipment passes have identical layouts.
- Direction and clip identifiers are valid and explicitly mapped.
- Required clips exist for the unit's implemented gameplay behavior.
- Looping and non-looping clips have valid timing.
- Event indices refer to existing frames.
- Projection and frame-selection logic have focused unit tests.
- The build does not rely on uncommitted local tools or absolute filesystem
  paths.

Prefer tests of projection, manifest parsing, frame selection, and state
transitions over brittle full-image snapshots.

### Visual checks

- The unit reads clearly at default and minimum supported zoom.
- Species, role, team, facing direction, and current action are distinguishable.
- The unit matches the ground plane and does not appear to lean or float.
- Feet do not slide and the ground anchor does not jitter.
- Lighting and shadows agree with surrounding world assets.
- Rotation does not cause tools or markings to switch physical sides.
- Alpha edges do not show bright or dark halos.
- Team color remains visible in a cluster and against representative terrain.
- Animation retains the light-hearted tone without obscuring gameplay timing.
- A representative group stays readable at the planned 20-50 units per team.
- The asset remains coherent through the full supported zoom range.

Capture visual-QA scenes at gameplay scale, including multiple units, overlap,
terrain contrast, movement in every direction, and interaction with at least one
large occluder. A full-size isolated sprite is not sufficient QA for an RTS.

## Agent completion report

When an agent creates or changes production character assets, its handoff should
state:

- Unit and clips added or changed
- Source files and runtime files affected
- Render-profile version used
- Reproduction command
- Automated validation performed
- Visual-QA scenarios inspected
- Known placeholders or unresolved art decisions

Keep changes narrow and reversible. Do not mix a new character asset, renderer
architecture rewrite, gameplay-balance change, and unrelated cleanup into one
change unless the task explicitly requires them together.
