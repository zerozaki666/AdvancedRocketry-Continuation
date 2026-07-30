# AdvancedRocketry Continuation 1.4.2 Release Notes

- 发布日期：2026-07-30
- 版本：`1.4.2-continuation`
- 适用于：Minecraft `1.7.10`、Forge `10.13.4.1614`、Java 8
- 依赖：`libVulpes-Continuation 0.2.10` 或更高版本

## 版本重点

### Kerr 黑洞天体

- 将 AdvancedRocketry 1.12.2 的黑洞恒星标记 backport 到 1.7.10，并在
  Continuation 层增加 Kerr 风格的质量、自旋、吸积盘与危险区参数。
- 黑洞数据支持 galaxy NBT、`planetDefs.xml`、登录同步和 XML 重置流程；未配置
  黑洞属性的旧世界与普通恒星继续使用原有行为。
- 星图、全息星球选择器、行星天空、空间站天空和自由空间均可识别并显示黑洞，
  不再把黑洞渲染为普通恒星。
- 顶级黑洞恒星可作为空间站的合成目标；station-only resolver 会优先保留真实
  dimension ID，并在冲突时使用备用编码。合成目标不会注册为 Forge dimension。
- `planetDefs.xml` 中可使用 `blackHole="true"`，并按需设置
  `blackHoleMass`、`blackHoleSpin`、`blackHoleAccretionRate`、
  `blackHoleAxisInclination`、`blackHoleAxisYaw`、吸积盘半径、视觉倍率和
  capture/influence/warning 半径。全部扩展属性均可省略并使用确定性默认值。

完整 XML schema、数据边界和实现说明见
[`KERR_BLACK_HOLE_AND_GENERATOR_IMPLEMENTATION_SPEC.md`](KERR_BLACK_HOLE_AND_GENERATOR_IMPLEMENTATION_SPEC.md)。

### Black Hole Generator

- 新增可合成的 Black Hole Generator 多方块机器、OBJ/贴图、英文与中文本地化、
  GUI 状态、TESR、NBT/网络同步和 RF 输出。
- 发电资格由服务端持续核验：机器必须位于已完成组装、且正直接环绕顶级黑洞
  主星的空间站。
- 基础输出为 `500 RF/t × blackHoleGeneratorMultiplier`；实际输出仍受现有
  LibVulpes RF 输出端口接收能力限制。
- 仅在发电资格与输出容量检查通过后，从物品输入端口消耗一件物质燃料；支持
  逐物品燃烧时间、默认燃烧时间以及是否允许未列出的物品。
- 停机、输出阻塞、空间站跃迁、结构破坏、区块重载与存档重载时会重新核验状态，
  避免 stale power、重复耗材或重复发电。
- 资源来源与本地修改记录见
  [`BLACK_HOLE_GENERATOR_RESOURCE_PROVENANCE.md`](BLACK_HOLE_GENERATOR_RESOURCE_PROVENANCE.md)。

### 自由空间交互

- 新增 `VISUAL_ONLY`、`WARNING`、`GRAVITY` 和 `CAPTURE` 四种黑洞交互模式。
- 默认值为 `VISUAL_ONLY`，升级后不会自动启用引力或破坏性捕获。
- 黑洞引力与速度更新由服务端权威处理；`CAPTURE` 及普通可着陆天体碰撞使用
  相对运动 swept-sphere 判定，降低高速穿透的概率。
- 新增 `BlackHoleCaptureEvent.Pre/Post`；addon 可在破坏性捕获前取消事件，并在
  捕获完成后接收通知。
- 若 `Pre` 未取消，`CAPTURE` 会清除待处理的延迟转移、杀死乘员并直接移除火箭；
  不会生成可回收的火箭结构掉落。

### 黑洞渲染

- 新增 `AUTO`、`FAST`、`HIGH` 和 `LEGACY` 渲染模式；固定管线 fallback 始终
  可用，不要求外部 shader pack。
- FAST/HIGH 支持 Kerr apparent shadow、近似光子临界环、前后吸积盘、上下引力
  透镜像、Doppler 亮度不对称和暖白—金色温度分布。
- 吸积盘使用有限厚度、周期性差速流动与按像素 footprint 的频率过滤，降低远景
  摩尔纹和闪烁，并增强侧视体积感。
- 修复黑洞离开视锥或位于玩家背后时投影半径发散、吸积盘覆盖整屏的问题。
- 修复 fallback blending、Kerr shadow LUT 截断和吸积盘覆盖中央阴影的问题；
  中央 apparent shadow 轮廓现在保持闭合。
- OpenGL 2.0/GLSL 1.20 或场景颜色捕获不可用、celestial target 使用 MSAA，
  或 `advancedVFX=false` 时会安全降级，并在日志中输出带
  `[BlackHoleRenderer]` 的原因。

### 写实大气渲染

- 以球形指数大气、Rayleigh/Mie 单次散射、光学深度 LUT 和有界 raymarch
  取代旧的五层半透明球壳与平面辉光。该效果是面向游戏实时渲染的
  **physically based approximation**，不代表科研级大气模拟精度。
- 空间视角现在会根据主恒星方向显示明暗交界、背光边缘辉光与地表遮挡；地表和
  近地平线视角会连续呈现昼夜、暮光、星空可见度和随高度变化的大气柱。
- 现有 `planetDefs` 的 `atmosphereDensity`、`skyColor`、`fogColor` 和气态巨行星
  标记继续驱动默认视觉，同时可用可选 `atmosphereRendering` 字段覆盖散射、
  吸收、尺度高度、行星/大气半径、太阳强度及云层参数。
- 新增 `AUTO`、`FAST`、`HIGH`、`LEGACY` 和 `OFF` 大气渲染模式；旧存档、未提供
  新字段的星球和玩法侧大气压力语义保持不变。
- GLSL 1.20 或 LUT 不可用时会使用连续球壳 fallback；检测到外部 shader program
  时会安全跳过内部 pass，避免破坏 shader pack、HUD 或后续世界渲染状态。

## 空间站行星渲染

- 将空间站下方的 LEO 平面改为 64×32 细分球体，并将大气层同步改为球壳。
- 星球视觉大小继续由原有轨道高度驱动；Orientation Controller 与 Altitude
  Controller 的目标值语义保持不变。
- 新增星球视觉倍率、自转速度和地表贴图 tiling 配置。默认自转周期约 1000 秒，
  LEO 地表默认使用 2×2 tiling；星球视觉倍率默认值现为 `1.5`。
- Altitude Controller 在 Target Altitude 下方新增 Max Altitude Change Rate
  slider；最低档 `1×` 与旧版速度完全一致，最高可调至旧版速度的 `10×`，选择值
  会随控制器存档并同步至服务端。
- 修复高轨目标区间中原有步长归零或变为负值、导致控制器停滞或反向移动的问题；
  不高于 `38100 km` 时的 `1×` 步长计算保持不变。
- 修复较大星球视觉倍率配合低轨道高度时，相机会进入大气代理球并触发近/远裁剪，
  从而在俯视画面中形成凹洞/开口的问题；正常高度与默认倍率下的既有构图保持不变。
- Earth 与 Moon 的 LEO 贴图升级为高分辨率资源。

## 修复与兼容性

- 修复未被 AdvancedRocketry 管理的 RFTools、Compact Machines 等第三方维度
  错误继承 overworld 大气状态、从而被判为无氧环境的问题。
- 未映射的第三方维度现在默认使用可呼吸的 `AIR`；AdvancedRocketry 行星、自由
  空间、空间站以及显式 `dimMapping` 仍使用各自的大气配置。
- 第三方维度中的密闭氧气区域和 Oxygen Vent 仍可正常加压与失压。
- 修复必须乘坐火箭才能在其 GUI 中选择目的地的问题；同世界、处于合法交互距离
  的玩家现在可以完成选择。
- 修复空间站信息包在客户端重建错误维度属性，以及缺失恒星数据时亮度计算可能
  触发空指针的问题。
- 普通火箭、卫星任务和外部 dimension mapping 仍只把真实 dimension 作为可操作
  目标，不会把 synthetic black-hole star target 当作可着陆维度。

## 新增配置

| 分类 | 配置 | 默认值 | 说明 |
|---|---|---:|---|
| Client | `blackHoleRenderMode` | `AUTO` | `AUTO`、`FAST`、`HIGH` 或 `LEGACY` |
| Client | `blackHoleShaderMinScreenRadius` | `24` | 启用 shader 路径所需的最小屏幕半径 |
| Client | `blackHoleMaxShaderBodies` | `2` | 单次 celestial pass 的 shader 黑洞上限 |
| Performance | `blackHoleShaderStepsFast` | `16` | FAST 的有界近似步数 |
| Performance | `blackHoleShaderStepsHigh` | `32` | HIGH 的有界近似步数 |
| Client | `atmosphereRenderMode` | `AUTO` | `AUTO`、`FAST`、`HIGH`、`LEGACY` 或 `OFF` |
| Client | `atmosphereMaxShaderBodies` | `2` | 单次 celestial pass 的 raymarched 大气上限 |
| Client | `atmosphereMinShaderRadiusPixels` | `6` | 启用 atmosphere shader 的最小投影半径 |
| Client | `atmosphereEnableCloudLayer` | `true` | profile 允许时独立渲染云层 |
| Client | `atmosphereDebugView` | `NONE` | 大气诊断输出；正常游玩保持 `NONE` |
| Performance | `atmosphereOpticalDepthLutWidth` | `128` | CPU 光学深度 LUT 宽度 |
| Performance | `atmosphereOpticalDepthLutHeight` | `64` | CPU 光学深度 LUT 高度 |
| General | `blackHoleFreeSpaceInteraction` | `VISUAL_ONLY` | 自由空间危险行为；`CAPTURE` 为破坏性 opt-in |
| General | `blackHoleGravityConstant` | `0.01` | 自由空间玩法引力系数 |
| General | `blackHoleMaxAcceleration` | `0.05` | 每 tick 最大黑洞加速度 |
| General | `blackHoleWarningInterval` | `40` | 同一火箭/天体警告的最小 tick 间隔 |
| General | `blackHoleLuminosityMode` | `UPSTREAM_COMPAT` | `UPSTREAM_COMPAT` 或 `ACCRETION_RATE` |
| Energy | `blackHoleGeneratorMultiplier` | `1.0` | Black Hole Generator 的 500 RF/t 基础输出倍率 |
| Energy | `defaultBurnTime` | `500` | 未单独配置的物质燃料燃烧 tick |
| Energy | `blackHoleTimings` | 常见方块各 `1` tick | `modid:item[:meta-or-*];ticks` 覆盖列表 |
| Energy | `blackHoleAllowUnlistedMatter` | `true` | 是否允许未列出的物品使用 `defaultBurnTime` |
| Client | `stationPlanetSphereScaleMultiplier` | `1.5` | 空间站下方星球的视觉大小倍率 |
| Client | `stationPlanetRotationSpeedMultiplier` | `1.0` | 星球自转倍率；`0` 停止自转 |
| Client | `stationPlanetTextureTilingMultiplier` | `1.0` | 默认 2×2 tiling 的倍率；`0.5` 恢复 1×1 |

## 升级说明

1. 备份世界、`planetDefs.xml` 和现有配置。
2. 替换 AdvancedRocketry JAR，并确认仍在使用
   `libVulpes-Continuation 0.2.10` 或更高版本。
3. 完整重启客户端与服务器，让配置文件生成新增项目。
4. 已有 `advancedRocketry.cfg` 不会自动覆盖原
   `stationPlanetSphereScaleMultiplier`；如需采用新默认视觉大小，请手动改为
   `1.5`。
5. 若第三方维度本应具备非 Earth 大气，请使用显式 `dimMapping`；未映射维度会按
   可呼吸 `AIR` 处理。
6. 破坏性黑洞行为必须手动将 `blackHoleFreeSpaceInteraction` 设置为
   `CAPTURE`；升级不会自动启用。

本次无需手工迁移存档或重建世界；新增 galaxy/generator NBT 字段向后兼容，
也不提高 LibVulpes 的最低版本。请勿同时保留 `1.4.1-continuation` 与
`1.4.2-continuation` 两个 AdvancedRocketry JAR。

## 验证状态

- 已在游戏内确认 Black Hole Generator 可以运行，且此前的 HIGH renderer 已完成
  多轮 playtest；最后一轮吸积盘稳定化提交仍需要最终游戏内回归。
- 已在用户环境确认写实大气默认观感，以及放大空间站行星后在低轨高度俯视时的
  大气代理球缺口修复。
- Java 与 GLSL 1.20 解析、投影/culling、Kerr LUT、动画周期、球体网格和 GL
  state 契约已完成静态验证。
- 当前分支的数学、光学深度 LUT、profile/NBT、零光照、天体方向、空间站大气
  包络与 Altitude Controller 速率共 47 项回归测试通过；6 个 GLSL 1.20 大气
  shader 也已通过离线编译验证。
- 当前开发环境具备同级 `libVulpes-Continuation` 与 Gradle 7.4.2，但运行 Gradle
  所需的 ForgeGradle/旧 Maven 依赖未完整缓存，且 Java 子进程无法访问外网，
  因此未在该环境执行完整的 `./gradlew clean build`。
- 写实大气仍需完成游戏内视觉矩阵、1920×1080 GPU/CPU 性能门槛、真实 GL2
  驱动、OptiFine/Angelica、外部 shader pack 和 context recreation 实机验收。
- 尚未完成 dedicated server、完整多人，以及 Angelica、外部 shader pack、
  MSAA/FBO 与多 GPU 的完整组合矩阵验收。

## 已知限制

- 该 renderer 是面向 Minecraft 1.7.10/OpenGL 兼容路径的 Kerr 风格近似，不是
  科研精度的完整 Kerr geodesic solver。
- 黑洞不是 Forge dimension 或普通火箭的着陆目标；只有空间站可以直接环绕顶级
  黑洞主星。
- 子恒星黑洞可保存和显示，但 1.4.2 的空间站目标、发电资格和自由空间危险区只
  对顶级主星提供完整支持。
- `HIGH`/`FAST` 在 MSAA celestial target、场景捕获失败、
  OpenGL 2.0/GLSL 1.20 能力不足或 `advancedVFX=false` 时会降级到
  fixed-function renderer。
- 同一个 celestial pass 不会让黑洞继续透镜另一个黑洞，以避免旧版 OpenGL
  路径上的 FBO ping-pong 和递归合成问题。
- 先前尝试的高分辨率 GUI scaling 修复已因回归而回滚；星球选择器的小窗口行为
  已恢复，但 4K 最大化窗口下的布局问题仍待后续处理。
