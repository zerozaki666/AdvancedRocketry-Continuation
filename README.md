# AdvancedRocketry Continuation for Minecraft 1.7.10

This is a community-maintained continuation fork of AdvancedRocketry for
Minecraft 1.7.10. The current release is `1.4.3-continuation` and requires
[`libVulpes-Continuation`](https://github.com/zerozaki666/libVulpes-Continuation)
`0.2.11` or later.

For the complete changes and upgrade notes, see
[`doc/RELEASE_NOTES_1_4_2.md`](doc/RELEASE_NOTES_1_4_2.md) and
[`doc/RELEASE_NOTES_1_4_3.md`](doc/RELEASE_NOTES_1_4_3.md).

This branch selectively ports 56 commits by which
[`kuzuanpa/AdvancedRocketry-TFRU`](https://github.com/kuzuanpa/AdvancedRocketry-TFRU)
is ahead of the shared baseline `d79d71d`, covering the range from `d0c66bc0`
through `161cd3b6`, inclusive. The porting policy is to retain generally useful
bug fixes and new features while removing content written specifically for the
TFRU modpack, TerraFirmaCraft, GT6/GregAPI, and the Dyson sphere system. A
commit-by-commit audit of the decisions and adaptations is available in
[`TFRU_COMMIT_PORT_AUDIT.md`](https://github.com/zerozaki666/AdvancedRocketry-Continuation/blob/MC1.7/TFRU_COMMIT_PORT_AUDIT.md).

## Additions and Fixes in This Continuation

- Added native OpenComputers components for space-station and rocket automation,
  seven independent OpenOS GUIs, and the unified `advrocket` launcher. When
  OpenComputers is installed, the native AdvRocket program disk can be crafted,
  installed with `install AdvRocket`, and safely refreshed from a newer JAR with
  the offline `update` command. Without OpenComputers, neither the disk nor its
  recipe is registered, and AdvancedRocketry still starts independently.
- Added an unpowered Oxygen Detector for dual-door airlock interlocks. It ignores
  faces blocked by airtight blocks and can output redstone when either any
  exposed face or every exposed face is breathable. With OpenComputers
  installed, the individual state of all six faces—north, south, east, west, up,
  and down—can also be queried.
- Added zero-buffer airtight RF and fluid passthrough interfaces. Until the last
  current source is removed, the fluid interface accepts only one fluid type.
  When the relevant mods are installed, the mod also conditionally registers a
  six-sided airtight OpenComputers cable, an 8-channel ME smart interface, and a
  32-channel ME dense smart interface. These integration blocks and recipes are
  not registered when OC or AE2 is absent.
- Backported Kerr-style black-hole stars and the Black Hole Generator from the
  1.12.2 code line. Black holes persist through XML/NBT/network synchronization,
  appear correctly in the star map, holographic selector, planetary sky,
  space-station sky, and free space, and can be selected as synthetic
  space-station targets without being registered as Forge dimensions.
- Added a server-authoritative Black Hole Generator multiblock. A completed
  station directly orbiting a top-level black hole can consume configurable
  matter fuels and produce a base 500 RF/t, multiplied by
  `blackHoleGeneratorMultiplier`, while revalidating structure, orbit, fuel,
  output capacity, chunk reloads, and warp transitions.
- Added `AUTO`, `FAST`, `HIGH`, and `LEGACY` black-hole render modes with
  Kerr-style apparent shadows, photon-ring approximation, animated accretion
  disks, gravitational-lensing images, Doppler asymmetry, and a fixed-function
  fallback that does not require an external shader pack. Optional free-space
  interaction modes range from the safe default `VISUAL_ONLY` through warnings
  and gravity to opt-in destructive `CAPTURE`.
- Replaced the old layered atmosphere shells with a real-time spherical
  scattering approximation using Rayleigh/Mie scattering, optical-depth LUTs,
  altitude-dependent haze, terminators, twilight, and safe legacy/off fallbacks.
  Existing planet XML remains compatible and may optionally override the visual
  atmosphere profile.
- Reworked the space-station planet from a flat LEO plane into a rotating,
  subdivided sphere with a matching atmosphere shell, high-resolution Earth and
  Moon textures, and configurable scale, rotation speed, and texture tiling.
  The Altitude Controller now also provides a persisted 1x-10x maximum altitude
  change-rate slider.
- Fixed unmanaged third-party dimensions inheriting an oxygenless AdvancedRocketry
  atmosphere, so unmapped RFTools, Compact Machines, and similar dimensions
  default to breathable air while explicit dimension mappings keep their
  configured atmospheres.
- Fixed dynamic oxygen-blob isolation for airtight non-full blocks such as
  whitelisted Galacticraft airlock seals, and fixed Oxygen Detector redstone
  updates through an adjacent powered solid block.
- Restored the Crystallizer in the Holographic Projector and fixed the placed
  Quartz Crucible's identity, localization, and multiblock recognition.

- The FTL Warp Core now consumes `gemDilithium` or `crystalDilithium` through the
  Ore Dictionary and correctly calculates fuel points using the
  `pointsPerDilithium` configuration value.
- Planet XML now supports `<spawnable>` entries with NBT. Entity registry names,
  class names, weights, and NBT are validated safely, while creature categories
  without custom configuration continue to use the vanilla spawn tables.
- Recipe XML now supports item NBT. Rewritten XML escapes text correctly and
  consistently uses semicolons as delimiters.
- Fixed duplication exploits, null pointers, invalid output, and non-atomic fluid
  consumption in the Oxygen Charger.
- Added experimental multistage-rocket structural analysis. Stage engine blocks,
  asynchronous structure scans, NBT persistence, and API queries are available.
  The main thread first creates a detached snapshot containing TileEntity NBT,
  so the background thread never reads the live rocket blocks or TileEntities.
  Stages are not yet separated or ignited automatically.
- Added a projector API through which other mods can register character-mapped
  structures. No direct GregAPI or GT6 integration layer was introduced.
- The jetpack renderer is now initialized only once, alongside multiple fixes for
  GUIs, star-map zooming, the Linker, phantom blocks, rocket rendering, and null
  pointers.
- Separated the space-station dimension from the free-flight space dimension.
  Their default IDs are now `-2` and `-3`, respectively, and startup checks for
  dimension conflicts. Legacy addon class names `WorldProviderSpace` and
  `RenderSpaceSky` retain their space-station semantics.
- Added Java 7-compatible stellar and planetary orbital simulation together with
  level-of-detail (LoD) starfield rendering.
- The simulated universe now uses a safe positive-Y origin and lower bounds for
  celestial bodies. On both client and server, free-space rockets are clamped to
  a safe altitude before movement to prevent Minecraft 1.7.10's void-removal
  logic from deleting them incorrectly.
- Planet Identification Chips use `DIRECT` travel by default. Setting
  `planetChipTravelMode` to `MANUAL` makes only player-piloted rockets enter free
  space, travel according to the player's view direction, and land after
  approaching a target body. Empty-seat remote launches, satellites, unmanned
  rockets, and asteroid missions retain their previous workflows.
- Added an explicit seat-presence flag so a newly built rocket whose seat is at
  structure coordinate `x=-1` is no longer mistaken for a seatless rocket.
  `getSeatX()` still returns the legacy value `-1` when no seat exists, and old
  saves with the seatless marker are migrated correctly.
- Expanded the Simplified Chinese translation and added multiple startup,
  network-synchronization, and dimension-property compatibility fixes.

Related configuration options:

- `spaceStationId`: space-station dimension ID; default `-2`.
- `freeSpaceId`: free-flight space dimension ID; default `-3`.
- `planetChipTravelMode`: Planet Identification Chip travel mode; default
  `DIRECT`. Set it to `MANUAL` to enable manual free-space flight.
- `maxSpaceRocketSpeed`: maximum rocket speed in each direction while in free
  space.
- `pointsPerDilithium`: FTL fuel points supplied by each piece of Dilithium. The
  corresponding code field is `fuelPointsPerDilithium`.
- `blackHoleRenderMode`: black-hole visual quality/fallback mode; default
  `AUTO`.
- `blackHoleFreeSpaceInteraction`: free-space black-hole gameplay behavior;
  default `VISUAL_ONLY`.
- `blackHoleGeneratorMultiplier`: multiplier applied to the generator's
  500 RF/t base output; default `1.0`.
- `atmosphereRenderMode`: atmosphere visual quality/fallback mode; default
  `AUTO`.
- `stationPlanetSphereScaleMultiplier`: apparent size of the planet below a
  space station; default `1.5`.
- `overworldSkyOverride`: whether AdvancedRocketry replaces the Overworld sky
  renderer; default `false` for new configurations.

## Explicitly Not Ported

- TerraFirmaCraft/TFRU dimension migration, environment detection, version
  suffixes, and modpack-specific configuration.
- Dyson spheres, Dyson swarms, stellar dimensions, landing on stars, and the
  related models, textures, and renderers.
- GregAPI-based GT6 projector integration and third-party JARs bundled directly
  into the repository.
- Maven/build-script changes added for modpack distribution and temporary changes
  that disabled GUIs.

The project's pre-existing general GregTech compatibility code and optional
integration remain in place. The excluded content is specifically the tightly
coupled GT6/GregAPI implementation later added by the TFRU branch.

## Original Core Features

- Build rockets from most blocks and operate them with fuel, guidance chips, and
  multiple mission payloads.
- Construct space stations orbiting planets or moons, perform warp travel, dock
  vehicles, and control local gravity.
- Define planetary systems through XML, with dynamic orbits, gases and oxygen,
  satellites, astronomical observation, and data research.
- Automate asteroid mining, gas-giant resource collection, orbital lasers, and
  cross-dimensional logistics.
- Integrate with IC2, RF, and mappings for many external dimensions.

## Building

JDK 8 is required. The accompanying libVulpes subproject targets Java 8, while
AdvancedRocketry itself retains Java 7 source and target compatibility. The
recommended layout places the two repositories side by side:

```text
advancedRocketryProject/
├── AdvancedRocketry-Continuation/
└── libVulpes-Continuation/
```

Then run the following from `AdvancedRocketry-Continuation`:

```bash
./gradlew build
```

`settings.gradle` prefers the adjacent `libVulpes-Continuation` directory while
retaining a fallback for the legacy directory name `libVulpes`. Both subprojects
use Gradle 7.4.2 and a Gradle-7-compatible ForgeGradle fork. The build scripts
first attempt to resolve the historical Galacticraft 3.0.12.504 development
dependencies. If those old Maven coordinates are unavailable, place matching
3.0.12 development JARs for both `MicdoodleCore` and `GalacticraftCore` in
`AdvancedRocketry-Continuation/libs/`; the build will use the local JARs instead.
The repository does not distribute those third-party JARs.

## License and Sources

This continuation contains ported code from an AGPL-3.0 branch and is therefore
released as a whole under the GNU Affero General Public License v3.0 in
[`LICENSE`](https://github.com/zerozaki666/AdvancedRocketry-Continuation/blob/MC1.7/LICENSE).
The MIT license text for the original AdvancedRocketry code is retained in
[`LICENSE-MIT`](https://github.com/zerozaki666/AdvancedRocketry-Continuation/blob/MC1.7/LICENSE-MIT),
and its original copyright notices continue to apply to the corresponding code.

---
# AdvancedRocketry Continuation for Minecraft 1.7.10

这是 AdvancedRocketry 1.7.10 的社区延续分支。当前版本为
`1.4.3-continuation`，需要配套的
[`libVulpes-Continuation`](https://github.com/zerozaki666/libVulpes-Continuation)
`0.2.11` 或更高版本。

本版本的完整变更与升级说明见
[`doc/RELEASE_NOTES_1_4_2.md`](doc/RELEASE_NOTES_1_4_2.md) 与
[`doc/RELEASE_NOTES_1_4_3.md`](doc/RELEASE_NOTES_1_4_3.md)。

本分支选择性移植了
[`kuzuanpa/AdvancedRocketry-TFRU`](https://github.com/kuzuanpa/AdvancedRocketry-TFRU)
在共同基线 `d79d71d` 之后至 `161cd3b6`（首条 `d0c66bc0`、末条
`161cd3b6` 均计入）的 56 条 ahead commit。移植原则是保留通用
bugfix 与新功能，剥离专为 TFRU 模组包、TerraFirmaCraft、GT6/GregAPI 及戴森球
系统编写的内容。逐提交的判断与改造记录见
[`TFRU_COMMIT_PORT_AUDIT.md`](TFRU_COMMIT_PORT_AUDIT.md)。

## 本次延续版新增与修复

- 新增原生 OpenComputers 空间站/火箭自动化组件、七套独立 OpenOS GUI 与
  `advrocket` 统一主界面。安装 OpenComputers 时可合成原生 AdvRocket 程序软盘，
  使用 `install AdvRocket` 安装，并通过离线 `update` 从新版 JAR 安全覆盖套件；
  未安装 OpenComputers 时不注册软盘或配方，AdvancedRocketry 仍可独立启动。
- 新增无需供电的 Oxygen Detector，用于双门气闸联锁。它会忽略被气密方块遮挡的
  面，并以“任一暴露面”或“全部暴露面”可呼吸为条件输出红石；安装
  OpenComputers 时还可读取东南西北上下六面的独立检测结果。
- 新增零缓存的气密 RF 与流体直通接口；流体接口在最后一个当前来源拆除前只允许
  单一流体。安装对应模组时还会条件注册六面连通的气密 OpenComputers Cable、
  8 频道 ME 智能接口及 32 频道 ME 致密智能接口，OC/AE2 未安装时这些联动方块与
  配方完全不注册。
- 从 1.12.2 代码线 backport Kerr 风格黑洞恒星与 Black Hole Generator。黑洞数据
  可通过 XML、NBT 与网络同步持久保存，并会在星图、全息星球选择器、行星天空、
  空间站天空及自由空间中正确显示；空间站可选择合成黑洞目标，但不会把黑洞注册成
  Forge 维度或普通火箭着陆目标。
- 新增服务端权威校验的 Black Hole Generator 多方块。完成组装且正直接环绕顶级
  黑洞主星的空间站可消耗可配置的物质燃料，并以
  `500 RF/t × blackHoleGeneratorMultiplier` 为基础输出；结构、轨道、燃料、
  输出容量、区块重载及空间站跃迁都会触发资格重检。
- 黑洞渲染新增 `AUTO`、`FAST`、`HIGH` 与 `LEGACY` 模式，包含 Kerr 风格
  apparent shadow、近似光子环、动态吸积盘、引力透镜像与 Doppler 明暗不对称；
  固定管线降级无需外部 shader pack。自由空间交互可从默认安全的
  `VISUAL_ONLY` 切换到警告、引力或需主动启用的破坏性 `CAPTURE`。
- 以实时球形散射近似取代旧版分层大气壳，加入 Rayleigh/Mie 散射、光学深度 LUT、
  随高度变化的薄雾、明暗交界与晨昏效果，并提供安全的 legacy/off 降级。现有
  planet XML 保持兼容，也可通过可选字段覆盖大气视觉参数。
- 将空间站下方的 LEO 平面改为带匹配大气壳的旋转细分球体，升级 Earth/Moon
  高清贴图，并提供视觉倍率、自转速度及贴图 tiling 配置。Altitude Controller
  新增会保存并同步的最大高度变化速率 slider，可在旧速度的 1 倍至 10 倍间调整。
- 修复未受 AR 管理的第三方维度错误继承无氧大气的问题；未映射的 RFTools、
  Compact Machines 等维度默认使用可呼吸空气，显式 dimension mapping 仍按配置
  工作。
- 修复气密白名单中的非完整方块（例如 Galacticraft airlock seal）动态闭合时未能
  及时切断氧气 Blob 的问题，并修复 Oxygen Detector 隔着相邻实体方块时的红石
  更新传播。
- 恢复全息投影器中的结晶器结构，并修复石英坩埚放置后的方块身份、本地化及
  多方块识别。

- FTL Warp Core 通过 OreDictionary 消耗 `gemDilithium` 或
  `crystalDilithium`，并按 `pointsPerDilithium` 配置正确计算燃料点。
- 行星 XML 支持带 NBT 的 `<spawnable>`；实体注册名、类名、权重及 NBT 均会做
  安全校验，未配置的生物类型继续使用原版生成表。
- Recipe XML 支持物品 NBT，XML 写回时会转义文本并统一使用分号分隔符。
- 修复氧气补充器的复制漏洞、空指针、无效输出和非原子式流体消耗。
- 增加实验性的多级火箭结构分析：分级发动机方块、异步结构扫描、NBT 保存和
  API 查询已可用。主线程会先创建含 TileEntity NBT 的脱离快照，后台线程不会
  访问实体火箭的实时方块或 TileEntity。当前不会自动拆分或点燃各级。
- 投影仪 API 可供其他模组注册字符映射的结构；没有引入 GregAPI/GT6 的直接适配层。
- Jetpack 渲染器只初始化一次，并包含多处 GUI、星图缩放、链接器、幻影方块、
  火箭渲染及空指针修复。
- 空间站维度与自由航行空间维度分离；默认分别为 `-2` 和 `-3`，启动时会检查
  维度冲突。旧 addon 使用的 `WorldProviderSpace` 与 `RenderSpaceSky` 类名继续
  保持空间站语义。
- 增加 Java 7 兼容的恒星/行星轨道模拟与远近细节（LoD）星空渲染。
- 模拟宇宙使用安全的正 Y 原点和天体下限；自由空间火箭在客户端与服务端都会在
  位移前截断到安全高度，避免 Minecraft 1.7.10 的虚空清除逻辑误杀。
- 行星识别芯片默认使用 `DIRECT` 模式直接前往所选维度；将
  `planetChipTravelMode` 改为 `MANUAL` 后，由玩家实际驾驶的火箭才会进入自由空间，
  按玩家视线手动航行并接近目标天体着陆。空座远程发射、卫星、无人火箭和小行星
  任务流程保持不变。
- 使用显式座位标志修复新建火箭的座位位于结构 `x=-1` 时被误判为无座位的问题；
  `getSeatX()` 对无座位仍返回旧值 `-1`，并正确迁移旧存档的“无座位”标记。
- 增补中文翻译以及多项启动、网络同步和维度属性兼容性修复。

相关配置：

- `spaceStationId`：空间站维度 ID，默认 `-2`。
- `freeSpaceId`：自由空间维度 ID，默认 `-3`。
- `planetChipTravelMode`：行星识别芯片旅行模式，默认 `DIRECT`；设为 `MANUAL`
  才启用自由空间手动飞行。
- `maxSpaceRocketSpeed`：自由空间中火箭各方向的最大速度。
- `pointsPerDilithium`：每颗 Dilithium 提供的 FTL 燃料点（对应代码字段
  `fuelPointsPerDilithium`）。
- `blackHoleRenderMode`：黑洞视觉质量与降级模式，默认 `AUTO`。
- `blackHoleFreeSpaceInteraction`：自由空间黑洞玩法行为，默认
  `VISUAL_ONLY`。
- `blackHoleGeneratorMultiplier`：Black Hole Generator 的 500 RF/t 基础输出
  倍率，默认 `1.0`。
- `atmosphereRenderMode`：大气视觉质量与降级模式，默认 `AUTO`。
- `stationPlanetSphereScaleMultiplier`：空间站下方星球的视觉大小倍率，默认
  `1.5`。
- `overworldSkyOverride`：是否由 AdvancedRocketry 接管主世界天空；新生成配置
  的默认值为 `false`。

## 明确未移植

- TerraFirmaCraft/TFRU 的维度迁移、环境检测、版本后缀和整合包专用配置。
- 戴森球、戴森云、恒星维度、登陆恒星及相关模型、贴图和渲染器。
- 基于 GregAPI 的 GT6 投影仪适配及打包进仓库的第三方 jar。
- 为整合包发布而添加的 Maven/构建脚本和临时禁用 GUI 的改动。

项目原有的通用 GregTech 兼容代码与可选依赖仍被保留；这里排除的是 TFRU 分支
后来新增的 GT6/GregAPI 强耦合实现。

## 原有主要功能

- 由绝大多数方块组装火箭，并使用燃料、制导芯片和多种任务载荷。
- 环绕行星或卫星的空间站、曲速航行、对接与局部重力控制。
- XML 行星系统、动态轨道、气体与氧气、卫星、天文观测和数据研究。
- 小行星自动采集、气态巨行星资源采集、轨道激光和跨维度物流。
- IC2、RF 与多种外部维度映射兼容。

## 构建

需要 JDK 8。配套的 libVulpes 子项目以 Java 8 为目标；AdvancedRocketry
子项目自身继续保持 Java 7 的 source/target 兼容级别。建议将两个仓库放在同一目录：

```text
advancedRocketryProject/
├── AdvancedRocketry-Continuation/
└── libVulpes-Continuation/
```

然后在 `AdvancedRocketry-Continuation` 中执行：

```bash
./gradlew build
```

`settings.gradle` 会优先使用相邻的 `libVulpes-Continuation`，同时保留对旧目录名
`libVulpes` 的回退。两个子项目统一使用 Gradle 7.4.2 与
Gradle-7-compatible ForgeGradle fork。构建脚本会优先解析旧版 Galacticraft
3.0.12.504 开发依赖；若该历史 Maven 坐标不可用，可把 `MicdoodleCore` 与
`GalacticraftCore` 两个匹配的 3.0.12 dev jar 都放入
`AdvancedRocketry-Continuation/libs/`，脚本会改用本地 jar。仓库不分发这些
第三方 jar。

## 许可与来源

本延续分支包含来自 AGPL-3.0 分支的移植代码，因此整体以
[`LICENSE`](LICENSE) 中的 GNU Affero General Public License v3.0 发布。
原 AdvancedRocketry 代码的 MIT 许可文本保存在
[`LICENSE-MIT`](LICENSE-MIT)，其原有版权声明继续适用于对应代码。
