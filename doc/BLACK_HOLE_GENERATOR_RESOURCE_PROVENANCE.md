# Black Hole Generator resource provenance

The following files were backported from the MIT-licensed upstream
AdvancedRocketry repository:

- `src/main/resources/assets/advancedrocketry/models/blackholegenerator.obj`
- `src/main/resources/assets/advancedrocketry/textures/models/blackholegenerator.png`

Source:

- repository: `Advanced-Rocketry/AdvancedRocketry`
- branch/baseline: `1.12`
- commit: `c5cd5af62fc07cd4e0d24f06a16033f181c47c04`
- original paths:
  - `src/main/resources/assets/advancedrocketry/models/blackholegenerator.obj`
  - `src/main/resources/assets/advancedrocketry/textures/models/blackholegenerator.png`

Local changes:

- removed the OBJ's unused `mtllib black_hole_generator.mtl` and
  `usemtl None` declarations because the referenced material file is not
  distributed and the 1.7.10 loader binds the texture explicitly from the
  TESR;
- rendering code was rewritten for the Minecraft 1.7.10 Forge model and
  Tessellator APIs;
- animation timing now uses deterministic world time and restores modified GL
  state after each render.

The upstream repository's MIT license remains documented by this repository's
existing `LICENSE-MIT`.
