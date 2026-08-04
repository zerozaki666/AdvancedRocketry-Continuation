# AdvancedRocketry Continuation 1.4.3 Release Notes

- 发布日期：2026-08-04
- 版本：`1.4.3-continuation`
- 适用于：Minecraft `1.7.10`、Forge `10.13.4.1614`、Java 8
- 依赖：`libVulpes-Continuation 0.2.10` 或更高版本
- 可选联动：OpenComputers `1.12.44-GTNH`

## 版本重点

### 原生 OpenComputers 控制组件

- Altitude、Orientation、Gravity Controller 可通过 OpenComputers 控制空间站高度、
  两轴姿态与重力；原方块 GUI、变化速率与服务端范围校验继续生效。
- Atmosphere Detector、Biome Scanner、Holographic Planet Selector、Warp Controller、
  Rocket Monitoring Station 和 Orbital Laser Drill 暴露只读遥测及受控操作 callback。
- Warp Controller 新增 `getCurrentTargetInfo()`，以单次服务端快照返回当前轨道目标的
  ID、类型和真实名称；未知 ID 不会回退成 Earth，跃迁中返回稳定 `in_warp` soft
  error。
- 所有可修改操作均经过服务端权威校验。自动化不能绕过已发现天体、燃料、Artifact、
  Warp Core、发射流程、红石启停或多方块结构等既有玩法约束。
- OpenComputers 保持可选依赖；未安装时 AdvancedRocketry 客户端与 dedicated server
  不加载 OC 网络/API 路径，原有方块和 TileEntity 仍可正常初始化。

完整 callback、返回值、soft error 与 signal 说明见
[`OPENCOMPUTERS_COMPONENT_REFERENCE.md`](OPENCOMPUTERS_COMPONENT_REFERENCE.md)。

### AdvRocket OpenOS 控制套件

- 新增 `advrocket` 统一主界面，显示 Minecraft 24 小时制时间、空间站当前环绕目标
  和高度；缺少 Warp/Altitude Controller 时对应数值会明确显示红色 `ERROR`。
- 主界面可通过触摸、数字键或方向键启动七套独立 GUI：Station Control、Biome
  Scanner、Rocket Monitor、Warp Controller、Atmosphere Detector、Planet Selector
  与 Laser Drill。
- 子程序按 `Q` 会返回 `advrocket`；只有主界面按 `Q` 才恢复 OpenOS 原显示状态并
  返回 shell。子程序缺失或异常不会同时终止主界面。
- 所有 GUI 继续使用同一深蓝/青色主题、`component.invoke()` 兼容调用层，以及
  `50x16` 至 `80x25` 自适应布局。

### 条件程序软盘与离线更新

- 安装 OpenComputers 时注册原生 cyan `AdvRocket` 程序软盘；它复用 OC 自带软盘
  物品、模型、贴图与只读文件系统，不新增 AR item registry ID，也不复制 OC 资源。
- 未安装 OpenComputers 时不注册该变体和配方，因此 Creative/NEI 中不可见、不可
  合成，并且不会把 OC API 打包进 AdvancedRocketry JAR。
- 使用 LibVulpes Linker 与一张完全空白、无 NBT 的 OC Floppy Disk 无序合成。
  自定义配方会拒绝 OpenOS、loot、染色、命名或包含玩家文件系统数据的软盘，避免
  误吞不可恢复的数据盘。
- 插盘后执行 `install AdvRocket` 安装完整套件；更换新版 AR JAR 后可插入同一张
  实体软盘执行 `update`，从新 JAR 的只读资源离线覆盖 managed files。
- universal JAR 会在 ForgeGradle reobfuscation 后恢复 OpenComputers ZIP 文件系统
  所需的显式目录条目，并在构建时逐级验证；修复程序盘可合成但插入后无法挂载、
  `install AdvRocket` 报 `Nothing to install labeled: AdvRocket` 的问题。
- 更新器校验 package ID、manifest format、只读 source、唯一来源、路径边界与
  symbolic link；先完整 staging，最后替换 manifest，提交失败时恢复旧文件。

程序盘文件布局、可选依赖隔离与验收矩阵见
[`OPENCOMPUTERS_ADVROCKET_SUITE_SPEC.md`](OPENCOMPUTERS_ADVROCKET_SUITE_SPEC.md)。

## 安装与使用

1. 客户端与服务器同时替换为 `1.4.3-continuation` JAR。
2. 如果需要电脑控制，安装 OpenComputers `1.12.44-GTNH`；不使用该联动时无需安装。
3. 使用 Linker 与空白 Floppy Disk 合成 `AdvRocket` 程序盘。
4. 在 OpenOS 中执行：

   ```sh
   install AdvRocket
   advrocket
   ```

5. 后续升级 AdvancedRocketry JAR 后，插入原有程序盘并执行：

   ```sh
   update
   ```

本版本不修改世界存档格式，也不提高 LibVulpes 的最低版本。请勿同时保留旧版与
`1.4.3-continuation` AdvancedRocketry JAR。

## 验证范围

- Java 单元测试覆盖 Warp 当前目标信息与程序盘配方的数据安全边界。
- Lua 验证覆盖主界面最低/最高分辨率、组件缺失、旧 callback、跃迁状态、七个
  子程序返回路径，以及更新器的来源、路径、staging、提交和回滚流程。
- Gradle 构建会断言 universal/deobf JAR 均包含完整程序盘资源，且不包含
  `li/cil/oc/api/**`。
