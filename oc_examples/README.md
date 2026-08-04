# OpenComputers examples

These examples target AdvancedRocketry Continuation for Minecraft 1.7.10 and
OpenComputers `1.12.44-GTNH`.

## Station controller GUI

`station_control.lua` is a standalone touch-friendly OpenOS application for:

- `altitude_controller`
- `orientation_controller`
- `gravity_controller`

No third-party GUI library is required. The program discovers the first
connected component of each type, refreshes the live station state once per
second, and reports AdvancedRocketry soft errors without terminating the GUI.
It invokes callbacks by component address for compatibility with the GTNH
OpenComputers dynamic proxy implementation. If an older controller exposes the
individual altitude or orientation getters but not the combined snapshot
getter, the GUI automatically falls back to those individual methods.

### Requirements

- An OpenOS computer with a GPU and screen capable of at least `50x16`.
- Each AdvancedRocketry controller connected directly to the same OC cable
  network. An Adapter is not required.
- The controllers must be placed on a valid AdvancedRocketry space station.
- Use one connected controller of each type. If multiple controllers of one
  type are present, the first address returned by OpenComputers is used.

### Install and run

Copy `station_control.lua` to the OpenOS computer as
`/home/station_control.lua`, then run:

```sh
lua /home/station_control.lua
```

The application temporarily uses up to an `80x25` resolution. It restores the
previous resolution and colors when it exits.

### Controls

- Touch the three tabs or press `1`, `2`, and `3` to change controller pages.
- Touch adjustment buttons to change the local draft value.
- Touch `APPLY` to send that value to the controller.
- Touch `SYNC FROM CONTROLLER` to discard local draft changes.
- Press `R` to rescan the cable network.
- Press `Q` to quit. OpenOS interrupt also exits and restores the display.

The altitude page supports the controller's full `2100..40100 km` range and
`1.0..10.0` change-rate multiplier. The orientation page controls the same Yaw
/ Y and Pitch / X axes as the block GUI, within `-60..60 rotations/hour`. The
gravity page supports `0.10..1.00 g` and provides `0.10`, `0.16`, `0.38`, and
`1.00` presets.

## Biome Scanner GUI

`biome_scanner.lua` is a standalone OpenOS browser for the connected
`biome_scanner`. It uses the same visual style and colors as
`station_control.lua`, automatically scans the current orbit target, and shows
each entry as `Biome Name [modId]` alongside its numeric biome ID.

The list is paginated for large modpacks. Page size is calculated from the
active screen height, so lists containing 100 or more registered biomes remain
usable at every supported resolution. The program preserves the mod id when a
long row must be shortened.

### Requirements

- An OpenOS computer with a GPU and screen capable of at least `50x16`.
- A complete AdvancedRocketry Biome Scanner connected directly to the same OC
  cable network. An Adapter is not required.
- The scanner must be on a valid space station with an unobstructed observation
  path.
- An AdvancedRocketry build whose `scan()` entries include the `modId` field.
  Older builds still run but display `unknown` as the source.

### Install and run

Copy `biome_scanner.lua` to the OpenOS computer as
`/home/biome_scanner.lua`, then run:

```sh
lua /home/biome_scanner.lua
```

The application temporarily uses up to an `80x25` resolution and restores the
previous resolution and colors when it exits.

### Controls

- Touch `FIRST`, `PREV`, `NEXT`, and `LAST` to change pages.
- Press `A`/left arrow and `D`/right arrow to change pages.
- Press Home or End to jump to the first or last page.
- Touch `SCAN` or press `S` to scan the current orbit target again.
- Press `R` to reconnect to the first Biome Scanner on the cable network and
  scan it.
- Press `Q` to quit. OpenOS interrupt also exits and restores the display.
