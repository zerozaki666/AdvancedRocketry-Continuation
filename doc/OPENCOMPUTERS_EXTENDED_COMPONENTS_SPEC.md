# AdvancedRocketry 1.7.10 OpenComputers 扩展组件集成规格

## 0. 文档状态

- 状态：Draft，等待玩法与 API 契约确认
- 目标分支：`agent/opencomputers-integration-spec`
- 基线提交：`369b8983b43b4cdfe9d6f300d7d5a33d182c2e6f`
- Minecraft：`1.7.10`
- Forge：`10.13.4.1614`
- OpenComputers：`1.12.44-GTNH`，mod id `OpenComputers`
- 前置规范：`doc/OPENCOMPUTERS_INTEGRATION_SPEC.md`
- 参考实现：`slava110/OCRocketry@95d3a78a422987ac8c394f4084441997667c2d61`

前置规范中的 Altitude Controller、Orientation Controller 与 Gravity
Controller 已完成实现，并已通过真实整合包冒烟测试。本规格只定义第二阶段的六个
扩展组件，不重新定义前三个组件的 callback。

本次规格提交只新增本文档，不包含功能代码。

---

## 1. 目标

为下列 AdvancedRocketry 1.7.10 方块增加原生 OpenComputers 组件：

| 游戏方块 | 1.7.10 TileEntity | OC 组件名 |
|---|---|---|
| Atmosphere Detector | `TileAtmosphereDetector` | `atmosphere_detector` |
| Biome Scanner | `TileBiomeScanner` | `biome_scanner` |
| Holographic Planet Selector | `TilePlanetaryHologram` | `planet_selector` |
| Warp Controller | `TileWarpShipMonitor` | `warp_controller` |
| Mining Laser / Orbital Laser Drill | `TileSpaceLaser` | `mining_laser` |
| Rocket Monitoring Station | `TileEntityMoniteringStation` | `monitoring_station` |

集成必须满足：

1. 六个方块都可以像已经完成的三个空间站控制器一样直接连接 OC Cable；
2. 不要求放置 OpenComputers Adapter；
3. OpenComputers 仍是纯可选依赖；
4. 所有读写世界、消耗燃料、启动跃迁或发射火箭的操作只在服务端执行；
5. GUI、红石与 OC 不复制三套状态机，而是调用相同的 canonical service/setter；
6. 不削弱星球发现、Artifact、燃料、Warp Core、维度有效性等玩法限制；
7. 对 OCRocketry 的有用 API 保持概念兼容，但不复制其 1.12.2 假设和已知缺陷；
8. 所有 callback 都使用非 direct 调用，不设置 `direct = true`。

---

## 2. 非目标

本阶段明确不做：

- 不把 OCRocketry 作为独立 addon 移植进整合包；
- 不引入 1.12.2 的 `BlockPos`、`EnumFacing`、Biome registry、Capability 或
  `SpaceStationObject` API；
- 不要求修改 LibVulpes；
- 不增加 ComputerCraft peripheral；
- 不允许 OC 绕过红石直接启动或停止 Mining Laser；
- 不暴露 Rocket Monitoring Station 的远程 `deconstruct`；
- 不允许向任意 Forge dimension id 跃迁；
- 不为不存在的 1.7.10 biome registry name 伪造 `modid:path`；
- 不使用反射访问 `selectedPlanet`、`linkedRocket`、`mission` 或
  `resetSpiral`；
- 不保证方块或 OC 网络未加载期间的事件补发。

Mining Laser 的工作开关继续由红石决定。OC 可以设置坐标、模式、解除卡住状态并
读取完整状态；玩家如需全自动启停，应使用 OC Redstone I/O 控制其红石输入。

Rocket deconstruction 会把实体火箭不可逆地转换回世界方块，而且 callback 没有可靠
玩家身份用于权限判定，因此不提供该高风险操作。

---

## 3. OCRocketry 源码审查

### 3.1 许可证与参考范围

OCRocketry 使用 MIT License。实现阶段可参考其公开 API 设计，但应以本 fork 的
1.7.10 源码为唯一行为真相。若实际复制了有实质性的源码片段，必须保留对应 MIT
版权与许可声明。

本规格优先采用重新实现：复用 callback 概念，不直接复制 1.12.2 driver 代码。

### 3.2 类映射

| OCRocketry 1.12.2 | 本 fork 1.7.10 | 关键差异 |
|---|---|---|
| `TileAtmosphereDetector` | `TileAtmosphereDetector` | 1.7.10 使用坐标字段与 `ForgeDirection` |
| `TileBiomeScanner` | `TileBiomeScanner` | 1.7.10 biome 只有稳定 numeric id/name |
| `TileHolographicPlanetSelector` | `TilePlanetaryHologram` | 当前选择是临时 UI Entity；空间站 destination 才是权威状态 |
| `TileWarpController` | `TileWarpShipMonitor` | 本 fork 已有黑洞目标、发现限制和更严格服务端校验 |
| `TileOrbitalLaserDrill` | `TileSpaceLaser` | 运行由红石驱动；类名、模式与内部卫星实现不同 |
| `TileRocketMonitoringStation` | `TileEntityMoniteringStation` | 1.7.10 保留旧拼写，燃料与 `StatsRocket` API 不同 |

### 3.3 不可照搬的 1.12.2 行为

| OCRocketry 行为 | 风险 | 本规格处理 |
|---|---|---|
| 使用 `DriverSidedTileEntity` | 需要 Adapter，无法提供当前阶段已经验证过的原生 Cable 体验 | 方块自身成为 native component |
| Hologram 反射读取 `selectedPlanet` | UI Entity 会重建、为空且不是持久 destination | 使用空间站 `getDestOrbitingBody()` |
| Monitoring Station 反射读取 rocket/mission | 字段重命名即静默失效 | 增加显式只读 accessor |
| Mining Laser 反射调用 `resetSpiral` | obfuscation/签名变化会失效 | 在 TileEntity 内部 canonical setter 调用私有方法 |
| Warp `setDestination` 对无效目标仍返回 `true` | 程序无法判断请求是否真正生效 | fail-closed，并返回具体 soft error |
| Warp 在 Artifact 检查前调用 `useFuel` | 失败跃迁也可能先扣燃料 | 全部验证后只执行一次燃料 mutation |
| Monitoring fuel parser 检查 Laser `MODE` 枚举 | 所有正常 fuel type 都可能被错误拒绝 | 严格解析 `FuelRegistry.FuelType` |
| Biome Scanner 用空数组同时表示障碍、Warp、Gas Giant | 错误语义不可区分 | 返回稳定 error code |
| 直接调用 `rocket.launch()` | 绕过 `RocketPreLaunchEvent` | `launch()` callback 作为安全别名调用 `prepareLaunch()` |
| 暴露 `deconstruct()` | 无确认、不可逆且缺少调用者权限 | 不提供 |
| 使用 magic int 表示无选中星球 | 易与真实/合成 target id 混淆 | 使用 `StationTarget` kind 与 soft error |

### 3.4 API 对照摘要

| OCRocketry callback/signal | 本规格 | 结论 |
|---|---|---|
| Atmosphere `getAtmosphereType` | `getAtmosphereType` | 保留，并增加结构化 `getAtmosphere` |
| Atmosphere `isBreathable` | `isBreathable` | 保留 |
| Atmosphere `allowsCombustion` | `allowsCombustion` | 保留 |
| Biome `scan(withModId)` | `scan()` / `scanNames()` | 适配 1.7.10，无虚假 registry name |
| Hologram `currentPlanet` | `getCurrentPlanet` / `getDestination` | 拆分 current orbit 与权威 destination |
| Hologram `selectPlanet` | `selectTarget` | 支持 planet 与本 fork 的 black-hole target |
| `planetSelected` | `planet_selected` | 保留状态边沿语义，使用项目命名约定 |
| Warp `getDestination` | `getDestination` | 保留 |
| Warp `setDestination` | `setDestination` | 保留并改为 fail-closed |
| Warp `getTravelCost` | `getTravelCost` | 保留，调用本 fork 统一算法 |
| Warp `warp` | `warp(expectedId?)` | 保留并增加竞态保护与原子 fuel mutation |
| Warp `isInWarp` | `isInWarp` | 保留 |
| Warp `currentPlanet` | `getCurrentPlanet` | 保留概念，统一 getter 前缀 |
| `warpStarted` / `warpFinished` | `warp_started` / `warp_finished` | 保留并增加 component/station identity |
| Laser `getCoords` / `setCoords` | `getCoordinates` / `setCoordinates` | 改为清晰全名与原子双坐标写入 |
| Laser running/finished/mode | 同概念 callbacks | 保留并增加 status/jam 查询 |
| Laser `start` / `stop` | 不提供 | OCRocketry 中本就被注释；红石继续拥有运行权 |
| Monitor `prepareLaunch` | `prepareLaunch` | 保留并走正常 event 路径 |
| Monitor raw `launch` | safe alias `launch` | 保留脚本入口，但不绕过 pre-launch event |
| Monitor stats/fuel/mission getters | 同概念 callbacks | 保留并修正 1.7.10 类型与错误语义 |
| Monitor `deconstruct` | 不提供 | 排除不可逆、无权限边界的操作 |

---

## 4. 组件连接架构

### 4.1 四个普通组件

以下四个 TileEntity 直接实现 `SimpleComponent`：

- `TileAtmosphereDetector`
- `TileBiomeScanner`
- `TileSpaceLaser`
- `TileEntityMoniteringStation`

使用与第一阶段控制器完全相同的可选依赖形式：

```java
@Optional.Interface(
        iface = "li.cil.oc.api.network.SimpleComponent",
        modid = "OpenComputers")
public class TileExample extends TileEntity implements SimpleComponent {

    @Override
    @Optional.Method(modid = "OpenComputers")
    public String getComponentName() {
        return "example_component";
    }
}
```

它们不需要主动发送异步信号，因此继续使用已经通过真实游戏验证的 transformer
注入生命周期。

### 4.2 Planet Selector 与 Warp Controller

OCRocketry 为这两个组件提供状态变化 signal。`SimpleComponent` 官方 API 明确不向
宿主暴露注入的 node，因此不能依赖私有 transformer 字段或反射获取 node。

为保留异步信号，同时保持原生 Cable 连接，这两个 TileEntity 使用显式
`li.cil.oc.api.network.Environment`：

- `TilePlanetaryHologram`
- `TileWarpShipMonitor`

约束如下：

- 不同时实现 `SimpleComponent`；
- node 使用 `Visibility.Network`；
- node component name 分别为 `planet_selector` 与 `warp_controller`；
- server-side `validate`/首次 tick 时创建并 `Network.joinOrCreateNetwork(this)`；
- `invalidate()` 与 `onChunkUnload()` 必须 `node.remove()`；
- node NBT 使用独立 key `openComputersNode`；
- 未安装 OC 时不加载任何含 OC 强类型字段的 helper；
- TileEntity 内 node storage 使用 `Object` 或同等的可选依赖隔离，不能让 JVM 在
  无 OC 环境解析 `Node` 字段；
- `node()`、`onConnect()`、`onDisconnect()`、`onMessage()` 及 callback 均使用
  `@Optional.Method(modid = "OpenComputers")`；
- 暂存 node NBT，使玩家临时移除 OC 后再装回时不会无故改变 component address。

建议新增：

```text
integration/opencomputers/OpenComputersNetworkNodeSupport.java
```

该类只在确认 `CompatibilityMgr.isOpenComputersLoaded()` 后调用，集中处理 node
创建、join、save/load、remove 与 signal。TileEntity 本身仍是 `Environment` host。

### 4.3 为什么不注册 OCRocketry 式 driver

OpenComputers 1.12.44-GTNH 仍有 1.7.10 版本的 `DriverSidedTileEntity`，但 driver
面向“通过 Adapter 访问第三方方块”的场景。这里的代码直接位于 AdvancedRocketry
本体中，并且第一阶段已经建立“Cable 直接连接控制器”的用户预期，因此不采用
Adapter driver。

### 4.4 组件命名

本项目沿用 OpenComputers 官方推荐的 lowercase + underscore 命名，而不是
OCRocketry 的无下划线名称。

| 本项目 | OCRocketry 1.12.2 |
|---|---|
| `atmosphere_detector` | `atmospheredetector` |
| `biome_scanner` | `biomescanner` |
| `planet_selector` | `planetselector` |
| `warp_controller` | `warpcontroller` |
| `mining_laser` | `mininglaser` |
| `monitoring_station` | `monitoringstation` |

不额外注册 legacy alias，避免同一方块在 OC 网络中出现两个 component address。

---

## 5. 通用 callback 契约

### 5.1 返回值

Getter 成功时直接返回值：

```lua
value
```

Getter 失败时：

```lua
nil, errorCode, message
```

Mutator/action 成功时：

```lua
true, appliedValue...
```

Mutator/action 失败时：

```lua
false, errorCode, message
```

参数类型错误继续使用 `Arguments.check*` 的标准 OC exception；合法类型但非法值、
机器状态或玩法限制使用 soft error，不用 exception 作为正常控制流。

### 5.2 访问验证

所有 callback 首先验证：

1. TileEntity 未 invalid；
2. `worldObj != null`；
3. `!worldObj.isRemote`；
4. 对应坐标仍加载且 `worldObj.getTileEntity(...) == this`；
5. 需要空间站的组件位于 `WorldProviderStation`；
6. 能从当前坐标重新解析到同一 `ISpaceObject`。

不得在 environment 构造时永久缓存 station reference。Warp Controller 现有
`getSpaceObject()` cache 可继续服务 GUI，但 OC service 每次 action 必须重新验证
world、tile 与 station。

建议新增通用 helper：

```text
integration/opencomputers/OpenComputersComponentAccess.java
```

保留现有 `OpenComputersControllerAccess` 以免破坏第一阶段；新 helper 可复用它的
错误格式，或在不改变 callback 契约的前提下安全抽取公共逻辑。

### 5.3 权限模型

OC callback 没有可靠 `EntityPlayerMP` 调用者。权限边界由以下条件共同构成：

- 物理 OC 有线网络；
- 组件必须在服务端加载；
- 组件必须属于有效空间站/机器；
- AdvancedRocketry 自身的 discovered target、artifact、fuel、core 与状态校验。

Warp/launch 不伪造玩家传给 `isAuthorizedActor`。需要玩家身份的 GUI 校验继续保留；
OC 使用 actor-independent、fail-closed 的 service 校验。

### 5.4 数值与表

- Java `int` 以 Lua number 返回；
- 可能超过 Lua 精确整数范围的 mission id 以十进制 string 返回；
- 状态 table 使用稳定英文 key；
- 返回新建的 map/list，不暴露内部可变集合；
- 不返回本地化文本作为稳定标识符；
- 所有 callback 都是非 direct server-thread callback。

---

## 6. 共享 gameplay service

### 6.1 Station destination service

Holographic Planet Selector 与 Warp Controller 必须共用一个不依赖 OC API 的
destination service。建议放在 station package：

```text
stations/StationDestinationService.java
```

`trySetDestination(tile, station, rawId)` 的验证顺序：

1. station 当前不在 `SpaceObjectManager.WARPDIMID`；
2. `StationTargetResolver.resolve(rawId).isDestination()`；
3. target 不是 station 当前轨道；
4. Dimension target 的 `DimensionProperties` 存在且 `station.isPlanetKnown(...)`；
5. Black-hole target 的 `StellarBody` 存在且 `station.isStarKnown(...)`；
6. 最后一次性调用 `station.setDestOrbitingBody(rawId)`。

GUI 与 OC 都调用该 service。失败时不得改变 destination。

### 6.2 Warp execution service

将 `TileWarpShipMonitor` 中的 travel cost、Artifact 检查与跃迁 mutation 收敛为
无 OC 依赖的 service/result：

```text
tile/station/StationWarpService.java
```

`tryWarp(tile, station, expectedDestination)` 必须：

1. 重新解析 station；
2. 拒绝已在 Warp 的 station；
3. 若提供 expected destination，确认仍与 station destination 相同；
4. 解析并验证 destination；
5. 验证 destination 已知；
6. 验证 destination 与当前轨道不同；
7. 计算有限且大于 0 的 travel cost；
8. 验证可用 Warp Core；
9. 验证燃料足够；
10. 验证全部 required artifacts；
11. 以上全部成功后只调用一次 `station.useFuel(cost)`；
12. 仅当返回的消耗量等于 cost 时调用 `moveStationToBody`；
13. 更新 Warp Core inventory、station packet、achievement 与现有 side effect；
14. 返回 destination、cost 与实际 remaining ticks。

任何失败路径必须保证 fuel、destination、orbit、artifact inventory 均不变。

### 6.3 Biome scan service

Biome Scanner GUI 与 OC 共用一个纯服务端 scan helper，统一：

- multiblock complete；
- scanner 下方观测通道无遮挡；
- station/current orbit 解析；
- Warp、Gas Giant 与 Black Hole 无地表错误；
- Overworld biome array 与 AR planet biome entries；
- 按 biome id 去重并稳定排序。

当前 GUI 的显示结果保持不变，但不再复制 scan 条件。

---

## 7. `atmosphere_detector` API

### 7.1 方位编号

与 Minecraft 1.7.10 `ForgeDirection` 的六个有效方向一致：

| side | 方向 |
|---:|---|
| `0` | down |
| `1` | up |
| `2` | north |
| `3` | south |
| `4` | west |
| `5` | east |

`UNKNOWN` 不可作为 callback 参数。小于 0 或大于 5 返回 `invalid_side`。

### 7.2 Callbacks

| Callback | 成功返回 | 说明 |
|---|---|---|
| `listAtmospheres()` | `table` | 已注册 atmosphere 列表，按稳定 id 排序 |
| `getAtmosphere(side)` | `table` | 指定相邻方块中心的完整 atmosphere 信息 |
| `getAtmosphereType(side)` | `string` | OCRocketry 兼容概念；返回稳定 unlocalized id |
| `isBreathable(side)` | `boolean` | 指定方向是否可呼吸 |
| `allowsCombustion(side)` | `boolean` | 指定方向是否允许燃烧 |
| `getTargetAtmosphere()` | `string` | Detector 当前检测目标 |
| `setTargetAtmosphere(id)` | `true, string` | 设置检测目标并返回实际 id |
| `isDetected()` | `boolean` | 当前红石输出所代表的缓存检测结果 |
| `getStatus()` | `table` | target、detected、powered metadata 与六面 atmosphere |

Atmosphere table：

```lua
{
  id = "air",
  breathable = true,
  allowsCombustion = true
}
```

`setTargetAtmosphere` 必须先精确匹配 `AtmosphereRegister` 中的注册 id。不得直接调用
会把未知值静默回退为 air 的 `getAtmosphere(String)` 来验证输入。

### 7.3 现有行为修正

新增 canonical `setTargetAtmosphere`，GUI packet、NBT 与 OC 全部调用它。Setter 在
服务端：

- `markDirty()`；
- `markBlockForUpdate()`；
- 下一次 10-tick detector update 重算输出。

同时将现有检测循环修正为恰好遍历 `DOWN..EAST` 六个方向，不能跳过 DOWN 或把
`UNKNOWN` 当作第七个方向。旧存档缺失/非法 `atmName` 时安全回退 air。

---

## 8. `biome_scanner` API

### 8.1 Callbacks

| Callback | 成功返回 | 说明 |
|---|---|---|
| `scan()` | `table` | 当前轨道天体的 biome 详细列表 |
| `scanNames()` | `table` | 只返回 biome display name 的平面数组 |
| `getStatus()` | `table` | 扫描器、空间站、当前目标与阻塞状态 |

`scan()` 每项：

```lua
{
  id = 1,
  name = "Plains"
}
```

Minecraft 1.7.10 没有 1.12.2 的 `Biome#getRegistryName()` 契约，因此不接受
OCRocketry 的 `withModId` 参数，也不返回伪造的 mod id。数字 `biomeID` 是该版本
实际可用的稳定标识。

### 8.2 扫描规则

- 只扫描 station 当前 orbit，不扫描预选 destination；
- station 在 Warp 时返回 `in_warp`；
- multiblock 未完成返回 `incomplete_multiblock`；
- scanner 下方光路存在非空气方块返回 `obstructed`；
- Gas Giant 或 Black Hole target 返回 `no_surface`；
- 无有效 AR dimension target 返回 `invalid_target`；
- Earth 使用全部非 null `BiomeGenBase.getBiomeGenArray()`；
- 其他 AR dimension 使用 `DimensionProperties.getBiomes()`；
- 结果按 biome id 去重并升序排序。

当前 1.7.10 Biome Scanner GUI 本身没有明确的单次能量扣除行为。OC scan 不额外发明
能源成本，但仍要求 multiblock 完成。

---

## 9. `planet_selector` API

### 9.1 权威状态

OCRocketry 的 `currentPlanet()` 反射读取临时 `EntityUIPlanet selectedPlanet`。本 fork
中这些 Entity 会在 rebuild、红石关闭与 chunk lifecycle 中销毁，而且 visual focus
不等于已提交的 station destination。

OC API 一律以以下状态为准：

- 当前天体：`station.getOrbitingPlanetId()`；
- 预选目标：`station.getDestOrbitingBody()`。

### 9.2 Callbacks

| Callback | 成功返回 | 说明 |
|---|---|---|
| `getCurrentPlanet()` | `number` | 当前 orbit target；Warp 中返回 `in_warp` |
| `getDestination()` | `number` | 当前已提交 destination，Warp 中仍可读取 |
| `getTargetInfo(id?)` | `table` | 省略 id 时查询 destination |
| `selectTarget(id)` | `true, number` | 选择已知 planet 或 black-hole target |
| `getScale()` | `number` | 实际 hologram scale multiplier |
| `setScale(multiplier)` | `true, number` | 设置并返回量化后的 scale |
| `isEnabled()` | `boolean` | 当前红石模式下 hologram 是否启用 |
| `getStatus()` | `table` | station、current、destination、scale、enabled、inWarp |

Target info 至少包含：

```lua
{
  id = 0,
  kind = "dimension", -- dimension | black_hole
  name = "Earth",
  known = true,
  current = true,
  destination = false
}
```

`selectTarget` 调用共享 Station destination service，并支持本 fork 的 black-hole
synthetic target id。无效、未知、当前目标或 Warp 中的请求不得改变 station。

### 9.3 Hologram scale

当前内部 slider 值 `size` 为 `0.00..1.00`，渲染倍率是 `size * 10 + 0.8`。
OC 暴露实际倍率：

- 最小 `0.8`；
- 最大 `10.8`；
- 步进 `0.1`；
- 默认 `1.0`。

现有 `size` 未写入 NBT。实现本规格时必须补齐 NBT 与 description packet，使 GUI、
OC、客户端渲染和重启后状态一致。

### 9.4 Signal

当 station destination 真正发生变化时发送：

```lua
planet_selected, componentAddress, stationId, destinationId
```

约束：

- 第一次加载只建立 baseline，不发送伪事件；
- visual focus、进入子星系视图或重建 UI Entity 不发送；
- GUI、OC 或其他合法 station controller 改变 destination 都可触发；
- 相同 destination 不重复发送；
- 方块/网络未加载期间的变化不补发。

---

## 10. `warp_controller` API

### 10.1 Callbacks

| Callback | 成功返回 | 说明 |
|---|---|---|
| `getCurrentPlanet()` | `number` | 当前 orbit；Warp 中返回 `in_warp` |
| `getDestination()` | `number` | 已提交 destination |
| `setDestination(id)` | `true, number` | 设置已知且有效的 destination |
| `getTravelCost()` | `number` | 当前 source 到 destination 的 Warp fuel cost |
| `getFuelAmount()` | `number` | station Warp fuel |
| `isInWarp()` | `boolean` | 当前 orbit id 是否为 `WARPDIMID` |
| `canWarp()` | `boolean, string` | 是否满足跃迁条件及稳定 reason code |
| `warp(expectedDestinationId?)` | `true, number, number` | destination id 与实际 remaining ticks |
| `getStatus()` | `table` | 完整 Warp 状态快照 |

`warp(expectedDestinationId)` 的可选参数用于防止程序在 destination 被其他 GUI/电脑
改动后误跃迁。若提供且不匹配，返回 `destination_changed`。省略参数时使用调用瞬间
的 station destination。

`getStatus()` 至少包含：

```lua
{
  stationId = 1,
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

### 10.2 Warp action 原子性

`warp()` 只能调用第 6.2 节的共享 service，不能在 callback 内复制 GUI 代码。

特别保证：

- Artifact 缺失时不扣燃料；
- Fuel 不足时不扣部分燃料；
- 无 Warp Core 时不扣燃料；
- invalid/unknown/current destination 不扣燃料；
- 第一次成功后 station 立即进入 Warp，重复调用返回 `already_in_warp`；
- 单次成功严格只调用一次 `useFuel`；
- `cost * 5` 使用 long 中间值并安全限制到 `5000`；
- 返回的 remaining ticks 以 `transitionTime - totalWorldTime` 计算，包含
  `travelTimeMultiplier` 的实际结果。

### 10.3 Signals

进入 Warp 时：

```lua
warp_started, componentAddress, stationId, destinationId, remainingTicks
```

完成 Warp 时：

```lua
warp_finished, componentAddress, stationId, arrivedTargetId
```

Signal 仅针对真实状态边沿，首次加载不发送。若 Warp Controller chunk 在整个跃迁期间
未加载，则不保证补发完成事件；程序应在启动或重连后调用 `getStatus()` 重新同步。

---

## 11. `mining_laser` API

### 11.1 Callbacks

| Callback | 成功返回 | 说明 |
|---|---|---|
| `getCoordinates()` | `number, number` | 当前 X/Z 目标坐标 |
| `setCoordinates(x, z)` | `true, number, number` | 原子设置 X/Z |
| `isRunning()` | `boolean` | 当前 laser satellite 是否实际工作 |
| `isFinished()` | `boolean` | SINGLE 模式当前目标是否完成 |
| `isJammed()` | `boolean` | 输出 inventory 是否导致 jam |
| `getMode()` | `string` | `single`、`line_x`、`line_z` 或 `spiral` |
| `setMode(mode)` | `true, string` | 空闲时设置模式 |
| `unjam()` | `true, boolean` | 尝试解除 jam；第二值为调用前是否 jammed |
| `getStatus()` | `table` | 完整结构、能源、红石、目标与运行状态 |

### 11.2 坐标与模式

- X/Z 必须是整数；
- 有效范围为 `-30000000..30000000`；
- 两个坐标先全部验证，再一起写入；
- `setCoordinates` 清除 `finished`；
- SPIRAL 模式下调用内部 `resetSpiral()`；
- callback 不使用 reflection；
- 若当前已有 operation 在运行，新坐标用于下一次 activation，不迁移当前激光实体；
- `setMode` 在实际运行中返回 `busy`；
- mode 大小写不敏感，但返回统一 lowercase；
- 从 SPIRAL 切出或切入 SPIRAL 时重置 spiral cursor；
- setter `markDirty()` 并更新客户端 GUI/description state。

### 11.3 运行控制

不提供 `start()` 或 `stop()`。OCRocketry 源码中的这两个 callback 本身也是注释状态。

`getStatus()` 至少包含：

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

稳定 state 建议值：`running`、`ready`、`redstone_off`、`incomplete_multiblock`、
`no_lens`、`no_energy`、`jammed`、`finished`、`in_warp`、`invalid_target`。

---

## 12. `monitoring_station` API

### 12.1 显式 accessor

在 `TileEntityMoniteringStation` 中增加只读 accessor：

```java
EntityRocketBase getLinkedRocketForAutomation()
IMission getLinkedMissionForAutomation()
```

可使用更通用的命名，但不得通过 reflection 访问字段。Accessor 不允许调用者替换
link；rocket/mission link 生命周期仍由现有 `IInfrastructure` 逻辑拥有。

### 12.2 Rocket callbacks

| Callback | 成功返回 | 说明 |
|---|---|---|
| `isRocketLinked()` | `boolean` | 是否有有效 linked rocket |
| `prepareLaunch()` | `true, string` | 走正常 pre-launch event 路径 |
| `launch()` | `true, string` | OCRocketry 名称兼容；安全别名，不直调 raw `launch()` |
| `getRocketHeight()` | `number` | linked rocket Y；mission 时使用 orbit estimate |
| `getRocketVelocity()` | `number` | 当前 vertical motionY |
| `getRocketThrust()` | `number` | `StatsRocket.getThrust()` |
| `getRocketWeight()` | `number` | `StatsRocket.getWeight()` |
| `getDrillingPower()` | `number` | `StatsRocket.getDrillingPower()` |
| `getAcceleration()` | `number` | 1.7.10 `StatsRocket.getAcceleration()` 原始单位 |
| `getFuelAmount(type)` | `number` | 指定 fuel type 当前量 |
| `getFuelCapacity(type)` | `number` | 指定 fuel type 容量 |
| `getFuelRate(type)` | `number` | 指定 fuel type 每 tick 消耗率 |
| `getFuelStatus(type)` | `table` | amount/capacity/rate |
| `hasSeat()` | `boolean` | 是否有 pilot seat |
| `getRocketStatus()` | `table` | rocket 与基础 stats 快照 |

`prepareLaunch()` 与 `launch()` 都调用 `EntityRocketBase.prepareLaunch()`。成功返回的
第二值为 `"requested"`，表示请求已交给 AR；最终是否升空仍可能被
`RocketPreLaunchEvent`、无效目的地或 AR 内部校验拒绝。

不允许 callback 直接调用 `EntityRocketBase.launch()` 绕过 pre-launch event。

### 12.3 Fuel type

接受以下大小写不敏感字符串：

- `liquid`
- `nuclear`
- `ion`
- `warp`
- `impulse`

解析目标必须是 `FuelRegistry.FuelType`。未知值返回 `fuel_type_not_found`。

### 12.4 Mission callbacks

| Callback | 成功返回 | 说明 |
|---|---|---|
| `isMissionLinked()` | `boolean` | 是否有 linked mission |
| `getMissionProgress()` | `number` | normalized `0.0..1.0` |
| `getMissionRemainingTime()` | `number` | 剩余秒数 |
| `getMissionStatus()` | `table` | id、origin dimension、progress、remaining seconds |

Mission status 示例：

```lua
{
  missionId = "123456789",
  originDimension = 0,
  progress = 0.42,
  remainingSeconds = 180
}
```

无 linked rocket/mission 时，详细 getter 分别返回 `rocket_not_found` 或
`mission_not_found`，而 `is*Linked()` 正常返回 `false`。

### 12.5 不提供 deconstruct

即使 OCRocketry 1.12.2 暴露 `deconstruct()`，本规格不提供同名 callback。原因：

- 操作不可逆；
- 可能在乘员仍在火箭内时执行；
- OC callback 无可靠玩家/owner 身份；
- 当前 Monitoring Station GUI 也没有对应的普通远程按钮。

---

## 13. 异步 signal 契约

本阶段只有 `planet_selector` 与 `warp_controller` 主动发送 signal。

| Signal | 参数 |
|---|---|
| `planet_selected` | `componentAddress, stationId, destinationId` |
| `warp_started` | `componentAddress, stationId, destinationId, remainingTicks` |
| `warp_finished` | `componentAddress, stationId, arrivedTargetId` |

共同规则：

- 仅服务端发送；
- 使用 `node.sendToReachable("computer.signal", ...)`；
- payload 只含 string/boolean/number；
- component address 放在首个 payload 参数，支持同一网络多个同类组件；
- 初始 baseline 不产生事件；
- 同一状态不重复产生事件；
- node 不可用或网络未加载时静默跳过，不刷 exception/log；
- event 只是提示，程序必须用 getter 再确认权威状态。

Lua 示例：

```lua
local event = require("event")

while true do
  local name, address, stationId, targetId, remaining = event.pull()
  if name == "planet_selected" or name == "warp_started"
      or name == "warp_finished" then
    print(name, address, stationId, targetId, remaining)
  end
end
```

---

## 14. 稳定 error code

| Code | 适用场景 |
|---|---|
| `invalid_tile` | TileEntity 已卸载、替换或 invalid |
| `not_server` | 错误地在客户端执行 |
| `not_on_station` | 需要 station 的方块不属于有效空间站 |
| `in_warp` | 当前状态不允许该操作 |
| `already_in_warp` | 重复调用 Warp action |
| `invalid_side` | Atmosphere side 不在 0..5 |
| `unknown_atmosphere` | 未精确注册的 atmosphere id |
| `incomplete_multiblock` | Biome Scanner/Mining Laser 结构不完整 |
| `obstructed` | Biome Scanner 观测通道受阻 |
| `no_surface` | Gas Giant/Black Hole 等无可扫描地表 |
| `invalid_target` | 当前 source/target 无有效 AR target |
| `invalid_destination` | 请求 id 不是 destination |
| `unknown_destination` | station 尚未发现目标 |
| `same_destination` | 目标就是当前 orbit |
| `destination_changed` | expected id 与当前 destination 不一致 |
| `no_usable_warp_core` | 无可用 Warp Core |
| `invalid_travel_cost` | cost 非法或不可达 |
| `insufficient_fuel` | Warp fuel 不足 |
| `missing_artifacts` | 缺少目标要求的 Artifact |
| `out_of_range` | 数值超出范围 |
| `invalid_mode` | Mining Laser mode 不存在 |
| `busy` | 当前运行状态禁止修改 |
| `fuel_type_not_found` | Rocket fuel type 不存在 |
| `rocket_not_found` | Monitoring Station 无有效 rocket link |
| `mission_not_found` | Monitoring Station 无有效 mission link |
| `action_rejected` | AR event/状态机拒绝请求且无更具体原因 |

Message 为人类可读英文，不作为程序分支依据；Lua 程序应只依赖 code。

---

## 15. GUI、NBT 与 packet 一致性

### 15.1 Canonical mutation

以下 mutation 必须由 GUI 与 OC 共用：

- Atmosphere detection target；
- Station destination；
- Hologram scale；
- Mining Laser X/Z；
- Mining Laser mode；
- Mining Laser unjam；
- Warp action；
- Rocket launch request。

不允许 callback 直接写字段而 GUI 继续走另一条路径。

### 15.2 持久化

| 状态 | 要求 |
|---|---|
| Atmosphere target | 保留现有 `atmName`，非法/缺失时 air fallback |
| Hologram scale | 新增 NBT，默认兼容旧存档的 `1.0` actual scale |
| Station destination | 继续由 `SpaceObject` 持久化 |
| Mining Laser coords/mode | 复用现有 NBT，并增加安全 enum 边界读取 |
| Monitoring links | 保持现有 rocket relink/mission id 行为 |
| OC explicit node | `openComputersNode`，仅 Planet Selector/Warp Controller |
| Signal baseline | 可持久化 last observed state，但不得产生 load-time 假事件 |

### 15.3 客户端同步

OC mutation 成功后：

- `markDirty()`；
- 必要时 `markBlockForUpdate()`；
- station mutation 复用 `PacketStationUpdate`；
- GUI 已打开时下一次 sync 显示新值；
- dedicated server 不加载任何 client-only class。

---

## 16. 测试计划

### 16.1 单元测试

至少新增：

1. Atmosphere side `0..5` 映射与越界；
2. 未知 atmosphere 不会静默变成 air；
3. detector 六面循环不包含 UNKNOWN；
4. biome id 去重、排序、Earth 与 AR planet 分支；
5. obstructed、in-warp、gas-giant scan result；
6. destination invalid/unknown/current/in-warp 拒绝且无 mutation；
7. black-hole synthetic target 可通过已知目标校验；
8. Warp 每个失败路径 fuel 不变；
9. Warp 成功只消耗一次精确 cost；
10. expected destination race guard；
11. transition duration overflow 与 multiplier；
12. Mining Laser coordinate 边界与原子写入；
13. 四种 laser mode parse 与 invalid mode；
14. SPIRAL reset contract；
15. 五种 `FuelType` 全部可解析，Laser mode 名称全部被拒绝；
16. Monitoring Station 无 link 的 soft error；
17. launch callback 只调用 safe prepare path；
18. explicit OC node NBT save/load/remove lifecycle；
19. 首次 node load 不发送 signal；
20. signal 仅在真实 state edge 发送一次。

Pure gameplay service 测试不得依赖 OC jar。需要 callback signature 的测试使用当前
`testCompileOnly` / `testRuntimeOnly` OC API 配置。

### 16.2 静态契约测试

- 六个 component name 唯一；
- callback 名称无重复；
- 所有 callback 均有 `@Optional.Method`；
- 无 `direct = true`；
- 无 1.12.2 import；
- 无 `java.lang.reflect` / `FieldUtils` / `MethodHandle`；
- universal jar 不包含 OC API class；
- 无 OC 环境不出现 required-mod dependency；
- Planet Selector/Warp Controller 不同时实现 `SimpleComponent` 与 `Environment`。

### 16.3 构建矩阵

| 环境 | 期望 |
|---|---|
| `compileJava` + OC API | 通过 |
| `compileTestJava` + OC API | 通过 |
| `test` | 全部通过 |
| client，无 OC | 正常启动，六个类无缺类错误 |
| dedicated server，无 OC | 正常启动，无 required-mod 提示 |
| client + server，OC `1.12.44-GTNH` | 九个 AR component 均可发现 |
| dedicated server + OC | 不加载 Minecraft client class |
| save 后移除 OC 再加载 | AR 方块与存档仍可用 |
| 再装回 OC | component 恢复且无 node 泄漏/重复地址 |

### 16.4 游戏内验收

#### Atmosphere Detector

- 六个方向分别查询 air、sealed oxygen 与 hostile atmosphere；
- 用 OC 改 detector target，红石输出在下一次检测周期更新；
- 重启后 target 保持。

#### Biome Scanner

- 正常空间站、无遮挡结构返回稳定 biome 列表；
- 加入障碍返回 `obstructed`；
- Warp/Gas Giant 返回对应错误；
- GUI 与 OC 列表一致。

#### Holographic Planet Selector

- 选择已知 planet 与 black-hole target；
- 未知/无效/current target 被拒绝；
- GUI 立即显示同一 destination；
- scale 可设为 `0.8`、`1.0`、`10.8` 并重启保持；
- destination 变化只发送一个 `planet_selected`。

#### Warp Controller

- 无 core、缺 fuel、缺 artifact 均失败且燃料不变；
- 成功一次只扣一次 fuel；
- 第二次立即调用返回 `already_in_warp`；
- `warp_started`/`warp_finished` 各一次；
- 黑洞跃迁与当前 branch 的 target resolver 一致。

#### Mining Laser

- 读写 X/Z 与四种 mode；
- SPIRAL 改坐标重置路径；
- running 时 setMode 返回 `busy`；
- OC 不能绕过红石启动；
- 红石启动后 status 正确报告 running/finished/jammed；
- unjam 与 GUI Reset 使用同一逻辑。

#### Rocket Monitoring Station

- linked rocket 的 height、velocity、stats 与 fuel 数据正确；
- 五种 fuel type 均可查询；
- `launch()` 与 `prepareLaunch()` 都触发正常 pre-launch event；
- 无 rocket/mission 时返回 soft error；
- mission progress/remaining time 与 GUI 一致；
- component 中不存在 `deconstruct`。

---

## 17. Lua 冒烟示例

### 17.1 Atmosphere Detector

```lua
local c = require("component")
local detector = c.atmosphere_detector

local info = detector.getAtmosphere(1)
print(info.id, info.breathable, info.allowsCombustion)
print(detector.setTargetAtmosphere("air"))
print(detector.isDetected())
```

### 17.2 Biome Scanner

```lua
local scanner = require("component").biome_scanner
local biomes, code, message = scanner.scan()
assert(biomes, tostring(code) .. ": " .. tostring(message))

for _, biome in ipairs(biomes) do
  print(biome.id, biome.name)
end
```

### 17.3 Planet Selector 与 Warp Controller

```lua
local component = require("component")
local selector = component.planet_selector
local warp = component.warp_controller

local target = 2
local ok, value, message = selector.selectTarget(target)
assert(ok, tostring(value) .. ": " .. tostring(message))

local status = warp.getStatus()
assert(status.canWarp, status.reason)

local started, destination, remaining = warp.warp(target)
assert(started, tostring(destination) .. ": " .. tostring(remaining))
print("Warping to", destination, "ETA ticks", remaining)
```

### 17.4 Mining Laser

```lua
local laser = require("component").mining_laser

assert(laser.setMode("spiral"))
assert(laser.setCoordinates(1200, -800))

local status = laser.getStatus()
print(status.mode, status.x, status.z, status.state)
-- 使用 OC redstone component 给机器供电；没有 laser.start()。
```

### 17.5 Monitoring Station

```lua
local monitor = require("component").monitoring_station

local rocket = monitor.getRocketStatus()
print(rocket.height, rocket.thrust, rocket.weight)

local fuel = monitor.getFuelStatus("liquid")
print(fuel.amount, fuel.capacity, fuel.rate)

local ok, state, message = monitor.prepareLaunch()
assert(ok, tostring(state) .. ": " .. tostring(message))
```

同一 OC 网络存在多个同类组件时，脚本必须使用 `component.list(type)` 与
`component.proxy(address)`，不能依赖 `component.<type>` 的任意选择顺序。

---

## 18. 实施阶段

### Phase 0：共享 refactor 与回归修复

- 新增通用 component access/error helper；
- 新增 destination 与 Warp service；
- 抽取 biome scan helper；
- Atmosphere Detector 六面与非法 NBT 修复；
- Hologram scale NBT/description sync；
- Mining Laser canonical coordinate/mode setter；
- Monitoring Station 显式 accessor；
- 为所有纯 service 建立单元测试。

### Phase 1：Atmosphere Detector 与 Biome Scanner

- 接入两个 `SimpleComponent`；
- 实现 callback 与 table codec；
- 验证无 OC 启动矩阵；
- 游戏内核对红石与 GUI。

### Phase 2：Planet Selector 与 Warp Controller

- 实现 optional explicit Environment node；
- 接入 destination/Warp service；
- 实现 signal baseline 与边沿检测；
- 重点验证 fuel mutation、black-hole target 与 node lifecycle。

### Phase 3：Mining Laser 与 Monitoring Station

- 接入两个 `SimpleComponent`；
- 实现 coordinates/mode/status/unjam；
- 实现 rocket/mission/fuel/status/safe launch；
- 确认无 start/stop/deconstruct callback。

### Phase 4：整体验收

- 执行全部单测、构建矩阵和游戏内验收；
- 更新 `OPENCOMPUTERS_INTEGRATION_SPEC.md` 的完成状态或添加交叉链接；
- 提供最终 Lua smoke-test script；
- 记录相对 OCRocketry 的 API mapping 与安全差异。

---

## 19. Definition of Done

- 六个新组件均能通过 Cable 在 OC 中发现；
- 加上第一阶段后共九个 AR native components；
- 所有文档规定 callback 均存在且返回契约一致；
- 无 OC client/server 正常启动；
- 无 1.12.2 class、API 或 runtime assumption；
- GUI、NBT、packet 与 OC mutation 一致；
- destination 与 Warp 失败均 fail-closed；
- Warp 所有失败路径不扣 fuel，成功严格扣一次；
- Planet/Warp signal 无 load-time 假事件与重复边沿；
- Mining Laser 仍由红石控制运行；
- Monitoring Station 不暴露 raw launch/deconstruct；
- 不使用 reflection；
- 所有测试通过并完成真实 dedicated server 冒烟测试。

---

## 20. 参考资料

- [OCRocketry repository](https://github.com/slava110/OCRocketry)
- [OCRocketry reviewed commit](https://github.com/slava110/OCRocketry/tree/95d3a78a422987ac8c394f4084441997667c2d61)
- [OCRocketry drivers](https://github.com/slava110/OCRocketry/tree/95d3a78a422987ac8c394f4084441997667c2d61/src/main/java/com/ocrocketry/driver)
- [OCRocketry MIT License](https://github.com/slava110/OCRocketry/blob/95d3a78a422987ac8c394f4084441997667c2d61/LICENSE)
- [OpenComputers 1.12.44-GTNH source](https://github.com/GTNewHorizons/OpenComputers/tree/1.12.44-GTNH)
- [OpenComputers SimpleComponent API](https://github.com/GTNewHorizons/OpenComputers/blob/1.12.44-GTNH/src/main/java/li/cil/oc/api/network/SimpleComponent.java)
- [OpenComputers Environment API](https://github.com/GTNewHorizons/OpenComputers/blob/1.12.44-GTNH/src/main/java/li/cil/oc/api/network/Environment.java)
- [Existing first-stage specification](./OPENCOMPUTERS_INTEGRATION_SPEC.md)
