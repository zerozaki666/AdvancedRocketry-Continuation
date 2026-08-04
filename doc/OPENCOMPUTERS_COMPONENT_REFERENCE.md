AdvancedRocketry Continuation OpenComputers component reference for Minecraft
1.7.10 and OpenComputers `1.12.44-GTNH`.

All components described here are native components. Connect the block directly
to an OpenComputers cable; an Adapter is not required.

## General usage

Find and proxy a component by its exact component name:

```lua
local component = require("component")

local address = component.list("biome_scanner", true)()
assert(address, "No connected biome_scanner found")

local scanner = component.proxy(address)
```

### Dynamic method invocation on OpenComputers-GTNH

Calling a known callback through its proxy remains supported:

```lua
local status, code, message = scanner.getStatus()
```

However, OpenComputers `1.12.44-GTNH` exposes proxy callbacks dynamically.
Consequently, `type(proxy[methodName]) == "function"` is not a reliable way to
test whether a callback exists: it can report `false` even when the callback is
available and callable.

Programs that store method names in variables, such as reusable GUIs and
wrapper libraries, should invoke them by component address:

```lua
local status, code, message = component.invoke(address, "getStatus")
```

When a program must check availability before calling a dynamic method, inspect
the component's advertised method table instead:

```lua
local methods = component.methods(address)

if methods.getStatus then
  local status, code, message = component.invoke(address, "getStatus")
end
```

This only affects dynamic discovery and dispatch. It does not change any
callback name, argument, return value, or soft-error convention documented
below.

You can list all connected AdvancedRocketry components with:

```lua
local component = require("component")

for address, name in component.list() do
  if name == "altitude_controller"
      or name == "orientation_controller"
      or name == "gravity_controller"
      or name == "atmosphere_detector"
      or name == "biome_scanner"
      or name == "planet_selector"
      or name == "warp_controller"
      or name == "mining_laser"
      or name == "monitoring_station" then
    print(name, address)
  end
end
```

### Return convention

- A successful getter returns its documented value or values.
- A failed getter returns `nil, errorCode, message`.
- A successful mutator returns `true` followed by the effective value or other
  documented result.
- A failed mutator returns `false, errorCode, message`.
- `message` is for people. Programs should branch on the stable `errorCode`.
- Missing arguments or arguments of the wrong Lua type use OpenComputers'
  normal bad-argument error behavior.

Example:

```lua
local ok, valueOrCode, message = component.gravity_controller
  .setGravityMultiplier(0.38)

if not ok then
  error(valueOrCode .. ": " .. message)
end

print("Applied gravity multiplier:", valueOrCode)
```

### Common error codes

| Code | Meaning |
|---|---|
| `invalid_tile` | The component TileEntity was unloaded, replaced, or invalidated. |
| `not_server` | The callback was invoked outside the authoritative server context. |
| `not_on_station` | A station-only component is not on a valid space station. |
| `not_finite` | A numeric argument is NaN or infinite. |
| `out_of_range` | A numeric argument is outside the supported range. |
| `invalid_value` | A value has the correct Lua type but an invalid form, such as a fractional mining coordinate. |
| `in_warp` | The current operation is unavailable while the station is in warp. |
| `already_in_warp` | A warp was requested while the station was already in warp. |
| `invalid_target` | The current or requested AdvancedRocketry target is not usable by this operation. |
| `invalid_destination` | The requested ID is not a valid station destination. |
| `unknown_destination` | The station has not discovered the requested destination. |
| `same_destination` | The requested destination is the current orbit target. |
| `destination_changed` | The destination no longer matches the expected ID supplied to `warp()`. |
| `incomplete_multiblock` | The required multiblock structure is incomplete. |
| `obstructed` | The Biome Scanner observation path is blocked. |
| `no_surface` | The current target has no biome surface, for example a gas giant or black hole. |
| `invalid_side` | An Atmosphere Detector side is outside `0..5`. |
| `unknown_atmosphere` | The requested atmosphere ID is not registered. |
| `no_usable_warp_core` | The station has no usable Warp Core. |
| `invalid_travel_cost` | No finite positive warp cost can be calculated. |
| `insufficient_fuel` | The station does not have enough warp fuel. |
| `missing_artifacts` | The destination's required artifacts are missing. |
| `invalid_mode` | The requested Mining Laser mode is unknown. |
| `busy` | The component cannot be changed while it is running. |
| `fuel_type_not_found` | The requested rocket fuel type is unknown. |
| `rocket_not_found` | The Monitoring Station has no live linked rocket. |
| `mission_not_found` | The Monitoring Station has no linked mission. |

# altitude_controller

## Info

Component name: `altitude_controller`

The Altitude Controller must be placed on a valid AdvancedRocketry space
station. It exposes the same saved target altitude and change-rate multiplier
used by the block GUI.

All altitude values are in kilometres. The target range is `2100..40100 km`
and is quantized to `200 km` steps. The change-rate multiplier range is
`1.0..10.0` and is quantized to `0.5` steps. Setters return the effective value
after quantization.

```lua
local altitude = component.altitude_controller

local ok, applied = altitude.setTargetAltitude(10100)
local okRate, appliedRate = altitude.setChangeRateMultiplier(10)
```

## Methods

### `setTargetAltitude(targetKm)`

Sets the saved station target altitude.

- `targetKm`: number in the closed range `2100..40100`.
- Returns `true, effectiveKm` on success.
- The effective value is rounded to the nearest valid `200 km` step.
- Returns `not_finite` or `out_of_range` without changing the target when the
  input is invalid.

### `getTargetAltitude()`

Returns the saved target altitude in kilometres.

- Returns `targetKm`.

### `getCurrentAltitude()`

Returns the station's current altitude in kilometres.

- Returns `currentKm`.
- The value may be fractional while the station is moving.

### `setChangeRateMultiplier(multiplier)`

Sets the maximum altitude change-rate multiplier.

- `multiplier`: number in the closed range `1.0..10.0`.
- Returns `true, effectiveMultiplier` on success.
- The effective value is rounded to the nearest `0.5` step.

### `getChangeRateMultiplier()`

Returns the saved altitude change-rate multiplier.

- Returns `multiplier`.

### `getStatus()`

Returns a combined station altitude snapshot:

```lua
{
  stationId = 3,
  state = "ascending", -- "idle", "ascending", or "descending"
  currentAltitude = 8200.35,
  targetAltitude = 10100,
  changeRateMultiplier = 10.0
}
```

`state` is `idle` when the current and target altitudes differ by no more than
`0.01 km`.

# orientation_controller

## Info

Component name: `orientation_controller`

The Orientation Controller must be placed on a valid space station. The OC API
intentionally exposes the same two control axes as the GUI:

| API axis | GUI axis | Station axis | Meaning |
|---|---|---|---|
| yaw | Y | UP | Rotation around the station Y axis. |
| pitch | X | EAST | Rotation around the station X axis. |

Rates are expressed in rotations per hour. The supported range is `-60..60`;
valid fractional input is rounded to the nearest integer. The API does not
expose roll/Z-axis control.

## Methods

### `setYaw(rotationsPerHour)`

Sets the target Y-axis rotation rate.

- `rotationsPerHour`: number in the closed range `-60..60`.
- Returns `true, effectiveRate`.
- The effective rate is rounded to an integer.

### `getYaw()`

Returns the saved target Y-axis rotation rate in rotations per hour.

- Returns `targetRate`.
- This is a target rate, not the absolute yaw angle.

### `setPitch(rotationsPerHour)`

Sets the target X-axis rotation rate.

- `rotationsPerHour`: number in the closed range `-60..60`.
- Returns `true, effectiveRate`.
- The effective rate is rounded to an integer.

### `getPitch()`

Returns the saved target X-axis rotation rate in rotations per hour.

- Returns `targetRate`.
- This is a target rate, not the absolute pitch angle.

### `getOrientation()`

Returns the two controlled axes' absolute angles, current rates, and target
rates:

```lua
{
  yaw = 172.5,           -- degrees, normalized to [0, 360)
  pitch = 14.0,          -- degrees, normalized to [0, 360)
  yawRate = 3.25,        -- current rotations/hour
  pitchRate = -1.5,      -- current rotations/hour
  targetYawRate = 4,     -- target rotations/hour
  targetPitchRate = -2   -- target rotations/hour
}
```

# gravity_controller

## Info

Component name: `gravity_controller`

The Gravity Controller must be placed on a valid space station. It controls the
same target gravity value as the GUI. The target range is `0.10..1.00`, where
`1.00` is normal gravity, and is quantized to `0.01` steps.

Gravity changes progressively. The getter therefore returns both the current
station multiplier and the saved target multiplier.

## Methods

### `setGravityMultiplier(multiplier)`

Sets the target station gravity multiplier.

- `multiplier`: number in the closed range `0.10..1.00`.
- Returns `true, effectiveMultiplier`.
- The effective value is rounded to the nearest `0.01` step.
- `0` is not an off switch and is rejected with `out_of_range`.

### `getGravityMultiplier()`

Returns two numbers:

```lua
local currentMultiplier, targetMultiplier =
  component.gravity_controller.getGravityMultiplier()
```

- `currentMultiplier`: the station's actual current gravity multiplier.
- `targetMultiplier`: the controller's saved target multiplier.

# atmosphere_detector

## Info

Component name: `atmosphere_detector`

The Atmosphere Detector can be used in ordinary dimensions as well as on a
space station. Its callbacks inspect the atmosphere at the centre of the block
adjacent to each of its six sides.

Side numbers use Minecraft 1.7.10 `ForgeDirection` ordering:

| Side | Direction |
|---:|---|
| `0` | down |
| `1` | up |
| `2` | north |
| `3` | south |
| `4` | west |
| `5` | east |

An atmosphere entry has this form:

```lua
{
  id = "air",
  breathable = true,
  allowsCombustion = true
}
```

Atmosphere IDs are the stable unlocalized IDs registered with
AdvancedRocketry. Use `listAtmospheres()` to discover the exact IDs available
in the current modpack.

## Methods

### `listAtmospheres()`

Returns a numerically indexed array containing all registered atmosphere IDs,
sorted alphabetically.

```lua
for _, id in ipairs(component.atmosphere_detector.listAtmospheres()) do
  print(id)
end
```

### `getAtmosphere(side)`

Returns the full atmosphere entry for one adjacent side.

- `side`: integer from `0` through `5`.
- Returns a table with `id`, `breathable`, and `allowsCombustion`.
- Returns `invalid_side` when the side is outside the valid range.

### `getAtmosphereType(side)`

Returns only the stable atmosphere ID for one adjacent side.

- `side`: integer from `0` through `5`.
- Returns `id` as a string.

### `isBreathable(side)`

Returns whether the atmosphere on one adjacent side is breathable.

- `side`: integer from `0` through `5`.
- Returns a boolean.

### `allowsCombustion(side)`

Returns whether the atmosphere on one adjacent side permits combustion.

- `side`: integer from `0` through `5`.
- Returns a boolean.

### `getTargetAtmosphere()`

Returns the detector's configured target atmosphere ID.

- Returns `id` as a string.

### `setTargetAtmosphere(id)`

Sets the detector's target atmosphere using an exact registered ID.

- `id`: one of the strings returned by `listAtmospheres()`.
- Returns `true, effectiveId`.
- Returns `unknown_atmosphere` rather than silently falling back to air when the
  ID is not registered.
- The redstone detection result is recalculated by the detector's normal update
  cycle.

### `isDetected()`

Returns the detector's current cached redstone detection state.

- Returns a boolean.

### `getStatus()`

Returns the target, cached detection result, redstone input state, and all six
adjacent atmospheres:

```lua
{
  target = "air",
  detected = true,
  powered = false,
  atmospheres = {
    down = {
      id = "air",
      breathable = true,
      allowsCombustion = true
    },
    up = { ... },
    north = { ... },
    south = { ... },
    west = { ... },
    east = { ... }
  }
}
```

# biome_scanner

## Info

Component name: `biome_scanner`

The Biome Scanner must be a complete multiblock on a valid space station. It
scans the station's **current orbit target**, not the destination selected for
a future warp.

The observation path below the scanner must remain clear down to the bottom of
the world. Scanning is unavailable while the station is in warp, when the
multiblock is incomplete, when the observation path is obstructed, or when the
current target has no biome surface.

Minecraft 1.7.10 does not provide the later registry-name contract used by
OCRocketry for Minecraft 1.12.2. This component therefore returns the actual
numeric `biomeID` and display name. Results are de-duplicated and sorted by ID.

## Methods

### `scan()`

Returns a numerically indexed array of biome entries for the current orbit
target:

```lua
{
  { id = 1, name = "Plains" },
  { id = 4, name = "Forest" },
  { id = 18, name = "ForestHills" }
}
```

Example:

```lua
local scanner = component.biome_scanner
local biomes, code, message = scanner.scan()

if not biomes then
  error(code .. ": " .. message)
end

for _, biome in ipairs(biomes) do
  print(string.format("%3d | %s", biome.id, biome.name))
end
```

Possible scan-specific failures include:

- `incomplete_multiblock`
- `obstructed`
- `in_warp`
- `no_surface`
- `invalid_target`

Earth returns all non-null biomes registered in Minecraft's biome array. Other
AdvancedRocketry dimensions return the biome set configured for that dimension.

### `scanNames()`

Returns a numerically indexed array containing only biome display names.

```lua
{ "Plains", "Forest", "ForestHills" }
```

The same validation and failure rules as `scan()` apply.

### `getStatus()`

Returns scanner readiness without failing for an incomplete, obstructed, or
otherwise unscannable target:

```lua
{
  stationId = 3,
  currentTargetId = 0,
  multiblockComplete = true,
  obstructed = false,
  scannable = true,
  state = "ready",
  biomeCount = 42
}
```

When `scannable` is false, `state` contains the same stable reason code that
`scan()` would return, such as `obstructed` or `no_surface`. `biomeCount` is
zero for an unsuccessful scan.

# planet_selector

## Info

Component name: `planet_selector`

The Holographic Planet Selector must be placed on a valid space station. Its OC
API uses authoritative station state instead of the temporary visual entity
currently focused in the hologram:

- Current target: the body the station is actually orbiting.
- Destination target: the committed destination used for the next warp.

The selector accepts discovered AdvancedRocketry dimensions and the synthetic
black-hole target IDs supported by this fork. Selecting a target changes the
committed station destination but does not start a warp.

Unlike the `SimpleComponent`-based devices, this block owns a persistent OC
node so that it can emit `planet_selected` signals. Its component address is
preserved across normal chunk unload and reload cycles.

## Methods

### `getCurrentPlanet()`

Returns the ID of the target the station is currently orbiting.

- Returns `currentTargetId`.
- Returns `in_warp` while the station is between targets.

### `getDestination()`

Returns the committed station destination ID.

- Returns `destinationTargetId`.
- The destination remains readable while the station is in warp.

### `getTargetInfo(id)`

Describes a station target. The `id` argument is optional; when omitted, the
method describes the committed destination.

```lua
{
  id = 0,
  kind = "dimension", -- "dimension" or "black_hole"
  name = "Earth",
  known = true,
  current = true,
  destination = false
}
```

- Returns `invalid_target` when the ID does not resolve to a valid station
  target.

### `selectTarget(id)`

Commits a discovered planet or black-hole target as the station destination.

- `id`: integer AdvancedRocketry target ID.
- Returns `true, id`.
- The operation fails without changing the station for an invalid, unknown,
  current, or warp-time target.
- Common failures: `invalid_destination`, `unknown_destination`,
  `same_destination`, and `in_warp`.

### `getScale()`

Returns the actual hologram render scale multiplier.

- Returns a number in `0.8..10.8`.

### `setScale(multiplier)`

Sets the actual hologram render scale multiplier.

- `multiplier`: number in the closed range `0.8..10.8`.
- Returns `true, effectiveMultiplier`.
- The value is quantized to `0.1` steps.
- The setting is synchronized to clients and persisted across reloads.

### `isEnabled()`

Returns whether the hologram is currently enabled by its configured redstone
mode and redstone input.

- Returns a boolean.

### `getStatus()`

Returns a station and hologram snapshot:

```lua
{
  stationId = 3,
  currentTargetId = 0,
  destinationTargetId = 2,
  scale = 1.0,
  enabled = true,
  inWarp = false
}
```

## Signals

### `planet_selected`

Sent whenever the committed destination actually changes, whether that change
came from OC, the GUI, or another valid station control path:

```lua
local event = require("event")

local name, address, stationId, destinationId =
  event.pull("planet_selected")
```

Signal arguments after the event name:

1. `address`: the Planet Selector component address.
2. `stationId`: the owning space station ID.
3. `destinationId`: the newly committed destination ID.

The first load establishes a baseline and does not emit a false event. Repeated
observation of the same destination does not emit duplicate events. Programs
should call `getDestination()` after startup or reconnection because signals
are not replayed for changes that happened while the component was unloaded.

# warp_controller

## Info

Component name: `warp_controller`

The Warp Controller must be placed on a valid space station. It exposes the
station's committed destination, fuel and readiness state, and can request one
fully validated warp.

`warp()` is atomic: validation is completed before fuel is consumed. Invalid
or unknown destinations, missing artifacts, missing Warp Cores, and insufficient
fuel do not consume fuel. After the first successful request, the station enters
warp immediately and a repeated request returns `already_in_warp`.

The controller supports the black-hole targets implemented by this fork.

## Methods

### `getCurrentPlanet()`

Returns the ID of the target the station is currently orbiting.

- Returns `currentTargetId`.
- Returns `in_warp` while the station is in warp.

### `getDestination()`

Returns the committed station destination ID.

- Returns `destinationTargetId`.

### `setDestination(id)`

Commits a discovered target as the station destination.

- `id`: integer AdvancedRocketry target ID.
- Returns `true, id`.
- Uses the same validation rules as `planet_selector.selectTarget(id)`.

### `getTravelCost()`

Returns the current source-to-destination warp fuel cost.

- Returns a positive integer `travelCost`.
- Returns `invalid_travel_cost` when no finite positive route is available.

### `getFuelAmount()`

Returns the station's current warp fuel amount.

- Returns `fuelAmount`.

### `isInWarp()`

Returns whether the station is currently in the warp dimension.

- Returns a boolean.

### `canWarp()`

Returns readiness as `canWarp, reason`.

```lua
local ready, reason = component.warp_controller.canWarp()
if not ready then
  print("Cannot warp:", reason)
end
```

This method normally returns `false, reason` for a station that is not ready;
that is a readiness result, not the three-value soft-error form. Possible reason
codes include:

- `ready`
- `already_in_warp`
- `invalid_destination`
- `same_destination`
- `unknown_destination`
- `invalid_travel_cost`
- `no_usable_warp_core`
- `insufficient_fuel`
- `missing_artifacts`

### `warp(expectedDestinationId)`

Starts one validated station warp. `expectedDestinationId` is optional.

```lua
local destination = component.warp_controller.getDestination()
local ok, destinationOrCode, remainingTicksOrMessage =
  component.warp_controller.warp(destination)
```

On success the method returns:

```lua
true, destinationId, remainingTicks
```

Supplying the expected destination protects automation against another GUI or
computer changing the destination between program validation and the warp
request. If it no longer matches, the method returns
`false, "destination_changed", message` without consuming fuel.

The actual transition duration is based on travel cost and is capped at `5000`
ticks. The returned `remainingTicks` is calculated from the station's real
transition timestamp.

### `getStatus()`

Returns a complete warp snapshot:

```lua
{
  stationId = 3,
  currentTargetId = 0,
  destinationTargetId = 2,
  inWarp = false,
  travelCost = 120,
  fuelAmount = 500,
  fuelCapacity = 1000,
  hasUsableWarpCore = true,
  hasRequiredArtifacts = true,
  canWarp = true,
  reason = "ready",
  remainingTicks = 0
}
```

When no valid finite travel cost exists, `travelCost` is `-1`. During warp,
`remainingTicks` counts down toward zero.

## Signals

### `warp_started`

Sent when the station transitions into warp:

```lua
local name, address, stationId, destinationId, remainingTicks =
  event.pull("warp_started")
```

Arguments after the event name:

1. Warp Controller component address.
2. Station ID.
3. Destination ID.
4. Remaining transition ticks.

### `warp_finished`

Sent when the station exits warp and arrives at a target:

```lua
local name, address, stationId, arrivedTargetId =
  event.pull("warp_finished")
```

Arguments after the event name:

1. Warp Controller component address.
2. Station ID.
3. Arrived target ID.

The first loaded state does not generate a false edge. Signals are hints rather
than durable messages; after startup or network reconnection, call `getStatus()`
to recover the authoritative state.

# mining_laser

## Info

Component name: `mining_laser`

This component is provided by the Mining Laser / Orbital Laser Drill
multiblock. OC can configure coordinates and mining mode, inspect readiness and
run state, and request jam recovery.

Starting and stopping remains controlled by the block's redstone input. The OC
API intentionally does not provide `start()` or `stop()`, so programs cannot
bypass the existing gameplay and safety boundary.

Supported modes are:

| Mode | Meaning |
|---|---|
| `single` | Mine one target. |
| `line_x` | Advance targets along the X axis. |
| `line_z` | Advance targets along the Z axis. |
| `spiral` | Advance targets in a spiral. |

Mode input is case-insensitive; getters always return lowercase names.

## Methods

### `getCoordinates()`

Returns the current mining target as two integers:

```lua
local x, z = component.mining_laser.getCoordinates()
```

### `setCoordinates(x, z)`

Atomically sets both mining target coordinates.

- `x`, `z`: whole numbers in `-30000000..30000000`.
- Returns `true, effectiveX, effectiveZ`.
- Both values are validated before either is changed.
- Fractional values return `invalid_value`.
- NaN or infinite values return `not_finite`.
- Changing coordinates clears the completed state. In spiral mode it also
  resets the spiral cursor.
- If an operation is already running, the new coordinates apply to the next
  activation; they do not move the current laser entity.

### `isRunning()`

Returns whether the laser satellite is actively operating.

- Returns a boolean.

### `isFinished()`

Returns whether the current target has completed in `single` mode.

- Returns a boolean.

### `isJammed()`

Returns whether the output inventory currently jams the laser.

- Returns a boolean.

### `getMode()`

Returns the current lowercase mode name:

```text
single | line_x | line_z | spiral
```

### `setMode(mode)`

Sets the mining mode while the laser is idle.

- `mode`: case-insensitive string naming one of the four supported modes.
- Returns `true, effectiveMode` in lowercase.
- Returns `busy` while the laser is running.
- Returns `invalid_mode` for an unknown mode.
- Entering or leaving spiral mode resets the spiral cursor.

### `unjam()`

Requests the normal internal jam-recovery operation.

- Returns `true, wasJammed`.
- `wasJammed` reports the state before the recovery attempt.
- A successful callback means the attempt ran; use `isJammed()` or
  `getStatus()` afterward to verify that output space is now available.

### `getStatus()`

Returns a complete Mining Laser snapshot:

```lua
{
  x = 0,
  z = 0,
  mode = "single",
  running = false,
  finished = false,
  jammed = false,
  multiblockComplete = true,
  hasLens = true,
  hasEnergy = true,
  redstonePowered = false,
  canSeeTarget = true,
  state = "redstone_off"
}
```

`state` is one of:

- `running`
- `ready`
- `redstone_off`
- `incomplete_multiblock`
- `no_lens`
- `no_energy`
- `jammed`
- `finished`
- `in_warp`
- `invalid_target`

The state field is selected in the priority order represented by the current
machine implementation. The individual boolean fields should be used when a
program needs more than one simultaneous condition.

# monitoring_station

## Info

Component name: `monitoring_station`

The Rocket Monitoring Station exposes read-only telemetry for its linked
rocket or mission and a safe pre-launch request. The station can be used in
ordinary dimensions and does not require a space-station provider.

`prepareLaunch()` and its compatibility alias `launch()` call the rocket's
normal `prepareLaunch()` path. They do not call the raw launch operation and
therefore do not bypass AdvancedRocketry pre-launch events or destination
validation. A successful return means that the request was submitted, not that
the rocket is guaranteed to lift off.

The component intentionally does not expose OCRocketry's destructive
`deconstruct()` method.

Accepted fuel type names are case-insensitive:

- `liquid`
- `nuclear`
- `ion`
- `warp`
- `impulse`

## Methods

### `isRocketLinked()`

Returns whether the station currently has a live linked rocket.

- Returns a boolean.
- Use this before rocket-specific telemetry getters to avoid
  `rocket_not_found`.

### `prepareLaunch()`

Submits a launch request through the rocket's normal pre-launch path.

- Returns `true, "requested"` when the request was submitted.
- Returns `rocket_not_found` when no live rocket is linked.
- Final launch can still be rejected by an event, an invalid destination, or
  another AdvancedRocketry check.

### `launch()`

A compatibility alias for `prepareLaunch()` with identical safe behavior and
return values. It never calls the raw rocket launch method directly.

### `getRocketHeight()`

Returns the linked rocket's current world Y coordinate.

- Returns `height` when a live rocket is linked.
- If the rocket has transitioned into a linked mission, returns the configured
  orbit-height estimate instead.
- Returns `rocket_not_found` when neither state is available.

### `getRocketVelocity()`

Returns the live linked rocket's vertical velocity (`motionY`).

- Returns `velocity`.
- Returns `rocket_not_found` after the rocket is no longer a live entity.

### `getRocketThrust()`

Returns `StatsRocket.getThrust()` for the live linked rocket.

### `getRocketWeight()`

Returns `StatsRocket.getWeight()` for the live linked rocket.

### `getDrillingPower()`

Returns `StatsRocket.getDrillingPower()` for the live linked rocket.

### `getAcceleration()`

Returns `StatsRocket.getAcceleration()` in the native AdvancedRocketry 1.7.10
units.

### `getFuelAmount(type)`

Returns the current amount of one rocket fuel type.

- `type`: `liquid`, `nuclear`, `ion`, `warp`, or `impulse`.
- Returns `amount`.
- Returns `fuel_type_not_found` for an unknown type.

### `getFuelCapacity(type)`

Returns the capacity of one rocket fuel type.

- Returns `capacity`.

### `getFuelRate(type)`

Returns the per-tick consumption rate of one rocket fuel type.

- Returns `rate`.

### `getFuelStatus(type)`

Returns all three values for one fuel type:

```lua
{
  type = "liquid",
  amount = 1000,
  capacity = 2000,
  rate = 10
}
```

The returned `type` is always normalized to lowercase.

### `hasSeat()`

Returns whether the linked rocket has a pilot seat.

- Returns a boolean.

### `getRocketStatus()`

Returns a live rocket telemetry snapshot:

```lua
{
  height = 123.5,
  velocity = 0.42,
  thrust = 12000,
  weight = 8500,
  drillingPower = 0,
  acceleration = 0.15,
  hasSeat = true,
  alive = true
}
```

Returns `rocket_not_found` when no live rocket is linked.

### `isMissionLinked()`

Returns whether the station currently has a linked mission.

- Returns a boolean.
- Use this before mission-specific getters to avoid `mission_not_found`.

### `getMissionProgress()`

Returns normalized linked-mission progress in the closed range `0.0..1.0`.

### `getMissionRemainingTime()`

Returns the non-negative mission time remaining in seconds.

### `getMissionStatus()`

Returns a linked mission snapshot:

```lua
{
  missionId = "123456789",
  originDimension = 0,
  progress = 0.42,
  remainingSeconds = 180
}
```

`missionId` is returned as a string so that Lua does not lose precision for a
large Java mission identifier. Returns `mission_not_found` when no mission is
linked.
