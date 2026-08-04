# AdvancedRocketry 1.7.10 AdvRocket OpenOS 程序套件与程序软盘规格

## 0. 文档状态

- 状态：Implemented，已按确认方案开发
- 目标分支：`agent/opencomputers-integration-spec`
- 审查基线：`4cb422418547b261affacf1f06210e516c06cb5a`
- Minecraft：`1.7.10`
- Forge：`10.13.4.1614`
- Java：`8`
- OpenComputers：`1.12.44-GTNH`，mod id `OpenComputers`
- AdvancedRocketry mod id：`advancedRocketry`
- 资源域：`advancedrocketry`
- 前置文档：
  - `doc/OPENCOMPUTERS_INTEGRATION_SPEC.md`
  - `doc/OPENCOMPUTERS_EXTENDED_COMPONENTS_SPEC.md`
  - `doc/OPENCOMPUTERS_COMPONENT_REFERENCE.md`
- 既有程序目录：`oc_examples/`
- 整合包确认版本：`loadedmod.csv` 中包含 OpenComputers
  `1.12.44-GTNH`

本文档最初以仅包含规格的提交供审阅；确认后所述 Java、Lua、资源、配方与
构建逻辑已经在当前实现中完成。

---

## 1. 执行摘要

本阶段在已经完成并通过实机验证的七套 OpenOS GUI 程序之上，增加：

1. 一个名为 `advrocket` 的统一主界面；
2. 一个仅在安装 OpenComputers 时存在的 `AdvRocket` 程序软盘；
3. 基于 OpenOS 标准 `install` 命令的整套安装流程；
4. 一个本地、离线、可覆盖升级的 `update` 命令；
5. 一个供主界面读取当前轨道目标名称的 Warp Controller callback。

`advrocket` 主界面实时显示：

- Minecraft 游戏内时间，24 小时制；
- 空间站当前 orbiting target 的名称，由 `warp_controller` 提供；
- 空间站当前高度，由 `altitude_controller` 提供；
- 七个进入既有子程序的按钮。

七个按钮按以下固定顺序排列：

| 编号 | 按钮 | 程序文件 |
|---:|---|---|
| 1 | Station Control | `station_control.lua` |
| 2 | Biome Scanner | `biome_scanner.lua` |
| 3 | Rocket Monitor | `rocket_monitor.lua` |
| 4 | Warp Controller | `warp_controller.lua` |
| 5 | Atmosphere Detector | `atmosphere_detector.lua` |
| 6 | Planet Selector | `planet_selector.lua` |
| 7 | Laser Drill | `laser_drill.lua` |

从主界面同步启动子程序后，子程序中的 `Q` 只结束当前子程序并返回
`advrocket`。只有在 `advrocket` 主界面按 `Q`，才恢复原显示状态并返回
OpenOS shell。

OpenComputers 仍是纯可选依赖。未安装 OpenComputers 时：

- AdvancedRocketry 客户端与 dedicated server 必须正常启动；
- 不注册程序软盘变体；
- Creative/NEI 中不出现该软盘；
- 不注册该软盘的合成配方；
- 不加载任何包含 OpenComputers 强类型引用的注册类。

安装 OpenComputers 时，AdvancedRocketry 通过 OC 官方程序软盘 API 注册一张
只读软盘。该软盘复用 OpenComputers 原生软盘物品、模型、颜色和贴图，不复制
OC 的纹理文件，也不额外占用一个 Forge item registry id。

---

## 2. 产品目标

### 2.1 必须达到

- 主程序命令名为 `advrocket`；
- GUI 配色、字体密度、按钮风格、状态栏和错误表达与现有七套程序一致；
- 支持触摸屏和键盘；
- 支持 `50x16` 至 `80x25` 的现有分辨率范围；
- Minecraft 时间按 `HH:MM` 显示，不显示现实世界时间；
- Warp Controller 缺失时，轨道目标一栏直接显示红色 `ERROR`；
- Altitude Controller 缺失时，高度一栏直接显示红色 `ERROR`；
- 对应 component 存在但 callback 返回 soft error 时，同样显示红色
  `ERROR`，状态栏显示稳定错误码；
- 空间站正在跃迁时显示黄色 `IN WARP`，不误报为 component 缺失；
- 七个子程序均可从主界面进入；
- 子程序按 `Q` 后返回主界面；
- 主界面按 `Q` 后返回 OpenOS；
- 子程序崩溃、缺文件或初始化失败不得同时终止主界面；
- 程序软盘可通过 Linker 与一张全新 OC Floppy Disk 无序合成；
- 程序软盘插入电脑后可用 OpenOS `install` 安装整套程序；
- 后续更换新版 AdvancedRocketry JAR 后，可插入同一张软盘并执行
  `update` 覆盖程序文件；
- 更新过程不需要 Internet Card、Pastebin、GitHub 或外部服务器；
- 更新器只覆盖 AdvRocket 自己管理的文件；
- 不将 OpenComputers API 类打包进 AdvancedRocketry JAR。

### 2.2 明确不做

- 不增加 ComputerCraft 软盘或 ComputerCraft 程序安装流程；
- 不将 OpenComputers 改为 FML required dependency；
- 不创建一个在无 OC 环境中仍占用 registry id 的占位物品；
- 不复制 `opencomputers` namespace 下的软盘 PNG；
- 不从网络下载程序；
- 不在启动电脑时自动覆盖硬盘文件；
- 不通过软盘 autorun 静默安装；
- 不自动启动 `advrocket`；
- 不在主界面复制七个子程序的业务逻辑；
- 不让主界面依赖 `planet_selector` 才能显示当前轨道名称；
- 不在 Lua 内维护 planet id 到名称的静态表；
- 不修改七个组件现有的安全检查、燃料消耗或控制范围；
- 不在本阶段增加用户账户、ACL 或远程更新签名系统。

---

## 3. 当前实现基线与缺口

### 3.1 已完成的程序

当前分支已经包含以下独立程序：

| 程序 | 主要 component | 当前状态 |
|---|---|---|
| `station_control.lua` | `altitude_controller`、`orientation_controller`、`gravity_controller` | 实机通过 |
| `biome_scanner.lua` | `biome_scanner` | 实机通过 |
| `rocket_monitor.lua` | `monitoring_station` | 实机通过 |
| `warp_controller.lua` | `warp_controller` | 实机通过 |
| `atmosphere_detector.lua` | `atmosphere_detector` | 已完成 |
| `planet_selector.lua` | `planet_selector` | 已完成 |
| `laser_drill.lua` | `mining_laser` | 已完成 |

七个程序已经共享以下约定：

- 最大使用 `80x25`；
- 最低要求 `50x16`；
- 启动时记录 GPU 分辨率、前景色和背景色；
- 退出或中断时恢复显示状态；
- 使用 `component.invoke(address, method, ...)`；
- 不使用 `type(proxy[method]) == "function"` 探测动态 callback；
- 以一秒为主要刷新周期；
- 通过 soft error 保持 GUI 存活；
- `Q` 结束当前 Lua 程序。

这些程序的核心控制逻辑在本阶段不重写。主界面以同步子进程方式运行它们，利用
“子程序正常结束后控制权返回调用者”的 OpenOS 行为实现二级导航。

### 3.2 当前 Warp Controller API 缺少名称

`warp_controller.getCurrentPlanet()` 当前只返回轨道 target 的 integer id。
`warp_controller.getStatus()` 也只有 `currentTargetId`，没有 target 名称。

主界面不能通过以下方式补救：

- 不能依赖 `planet_selector.getTargetInfo()`，因为用户明确要求此信息依赖
  Warp Controller；
- 不能读取 `planetDefs.xml`，因为 Lua 电脑无法可靠访问服务端配置；
- 不能内置名称表，因为自定义星球、随机星球和黑洞目标会使其失效；
- 不能把 numeric id 冒充名称。

因此实现阶段必须为 `warp_controller` 增加一个原子的只读 callback，详见第 7 节。

### 3.3 OpenComputers 已提供第三方程序软盘 API

目标 OC 版本的官方 API 提供：

```java
Items.registerFloppy(
        String name,
        int color,
        Callable<li.cil.oc.api.fs.FileSystem> factory,
        boolean doRecipeCycling)
```

该 API 的特性与本需求完全匹配：

- 只能在 init 或更晚阶段调用；
- 返回可用于配方输出的 `ItemStack`；
- 将软盘显示在 OpenComputers creative item list；
- 使用普通 OC Floppy Disk 的图标与 driver；
- 通过 factory 为软盘生成只读文件系统；
- `FileSystem.fromClass(...)` 可直接挂载 AdvancedRocketry JAR 中的资源目录。

因此本规格不创建新的 `Item` 子类，而是创建一个由 AdvancedRocketry 注册、拥有
唯一 loot-factory id 和程序内容的 OC floppy variant。

---

## 4. `advrocket` 主界面

### 4.1 程序身份

| 属性 | 值 |
|---|---|
| OpenOS 命令 | `advrocket` |
| 源文件 | `oc_examples/advrocket.lua` |
| 安装路径 | `/usr/bin/advrocket.lua` |
| 初始程序版本 | `1.0.0` |
| 刷新周期 | `1` 秒 |
| 最低分辨率 | `50x16` |
| 最大使用分辨率 | `80x25` |

### 4.2 配色

必须复用现有 GUI 的颜色常量，不增加另一套主题：

| 用途 | RGB |
|---|---:|
| Background | `0x101820` |
| Panel | `0x1B2A36` |
| Active panel | `0x28506B` |
| Text | `0xE8F1F5` |
| Muted | `0x8EA7B3` |
| Accent | `0x53C7F0` |
| Good | `0x63D471` |
| Warning | `0xF2C14E` |
| Error | `0xF25F5C` |
| Button | `0x2C4454` |
| Disabled button | `0x26323A` |
| Button text | `0xFFFFFF` |

### 4.3 顶部实时信息

主界面顶部依次显示：

| 行 | 标题 | 正常示例 | 数据源 |
|---|---|---|---|
| 1 | Minecraft Time | `14:37` | `os.date("%H:%M")` |
| 2 | Orbiting Target | `Earth` | `warp_controller.getCurrentTargetInfo()` |
| 3 | Station Altitude | `4300 km` | `altitude_controller.getCurrentAltitude()` |

OpenComputers 的 `os.date()` 已使用 Minecraft world time，并处理 Minecraft
一天从 06:00 开始的偏移。本程序不得改用 `computer.uptime()` 或现实系统时钟。

高度按最接近的整数 km 显示，以避免实际高度渐进变化时产生过长的小数。

长 target 名称必须安全截断，不能挤出屏幕。截断只影响显示，不修改或缓存服务端
名称。

### 4.4 错误与特殊状态

| 场景 | 主值 | 颜色 | 状态栏 |
|---|---|---|---|
| 未找到 `warp_controller` | `ERROR` | Error | `warp_controller not found` |
| Warp callback 缺失 | `ERROR` | Error | `AdvancedRocketry API is too old` |
| Warp callback 其他 soft error | `ERROR` | Error | 显示 error code |
| 空间站正在 warp | `IN WARP` | Warning | 可显示 `in_warp` |
| target 名称为空或 target 无效 | `ERROR` | Error | `invalid_target` |
| 未找到 `altitude_controller` | `ERROR` | Error | `altitude_controller not found` |
| Altitude callback soft error | `ERROR` | Error | 显示 error code |
| 正常 | 真实值 | Text/Good | `Ready` |

“组件缺失”与“组件存在但状态无效”都必须 fail closed。主值不可继续显示已经过期的
旧数据；如需诊断，可在状态栏中显示最近错误，不得把旧值伪装成实时值。

### 4.5 布局

布局必须保证所有实时信息位于全部按钮上方。

| 分辨率 | 按钮布局 |
|---|---|
| `50x16` 至 `65x25` | 单列七行，完整显示七个按钮 |
| `66x16` 至 `80x25` | 两列，左列四个、右列三个 |

最低 `50x16` 下仍必须保留：

- 标题；
- 三项实时信息；
- 七个按钮；
- 一行状态；
- `Q Quit` 与 `R Rescan` 提示。

按钮标签允许在小屏幕使用较短的人类可读名称，但程序路径始终使用固定文件名。

### 4.6 输入

| 输入 | 行为 |
|---|---|
| 触摸按钮 | 启动对应子程序 |
| `1` 至 `7` | 启动对应编号子程序 |
| 上/下/左/右 | 移动按钮选择 |
| Enter | 启动当前选中程序 |
| `R` | 重新扫描 Warp/Altitude component 并刷新数据 |
| `Q` | 退出 `advrocket`，返回 OpenOS |
| OpenOS interrupt | 与主界面 `Q` 相同，安全恢复显示 |

主界面中所有七个程序按钮即使对应游戏 component 缺失也应保持可启动。子程序已有
各自的离线诊断界面，主菜单不得因为暂时缺少方块而阻止进入。

如果程序文件本身缺失，按钮显示 disabled；触摸或快捷键不会退出主界面，并在
状态栏显示缺少的绝对路径。

### 4.7 Component 发现与调用

主界面只主动发现：

- `warp_controller`
- `altitude_controller`

规则：

1. 使用 `component.list(componentName, true)`；
2. 与现有程序一致，存在多个同名 component 时使用 OC 返回的第一个地址；
3. 使用 `component.invoke(address, method, ...)`；
4. 监听 `component_added` 与 `component_removed` 后重新扫描；
5. 一秒轮询作为权威恢复路径；
6. 不使用动态 proxy 字段类型判断；
7. component address 变化后立即丢弃旧值。

---

## 5. 主界面与子程序的返回协议

### 5.1 同步子进程模型

主界面通过 OpenOS `shell.execute(...)` 同步运行安装目录中的子程序：

```lua
local path = "/usr/lib/advrocket/apps/station_control.lua"
local ok, reason = shell.execute(path)
-- 子程序结束后从这里继续，重新扫描并重绘主界面。
```

不使用并行 thread，不让两个 GUI 同时读取键盘或绘制同一 GPU。

### 5.2 `Q` 的语义

子程序本身仍保持“`Q` 结束当前进程”的简单行为：

- 从 OpenOS 直接运行子程序时，`Q` 返回 OpenOS；
- 从 `advrocket` 启动时，子进程结束后自然返回正在等待的主界面；
- 不需要让七个程序互相 import 主界面；
- 不需要给每个程序硬编码父程序路径；
- 不通过重启电脑模拟返回。

这使七个程序仍可独立运行，同时满足套件内的二级导航。

### 5.3 显示状态所有权

显示恢复顺序：

1. `advrocket` 启动时记录 OpenOS 原始分辨率与颜色；
2. 主界面切换到 `50x16..80x25` 范围；
3. 子程序启动时记录主界面的显示状态；
4. 子程序退出时恢复主界面状态并清屏；
5. `shell.execute` 返回；
6. 主界面重新扫描 component、刷新 snapshot 并完整重绘；
7. 只有主界面退出时才恢复第 1 步记录的 OpenOS 状态。

### 5.4 子程序异常

主界面必须用受保护调用包裹 `shell.execute`。以下情况只返回主界面并显示错误：

- 文件不存在；
- Lua syntax/load error；
- 子程序初始化失败；
- GPU/component 调用抛出错误；
- 子程序返回 non-zero exit code。

子程序退出后主界面必须清理可能残留的 touch hitbox 和 status，不复用子程序的
局部状态。

---

## 6. 安装后的文件布局

程序软盘挂载后提供以下只读结构：

```text
/.prop
/usr/bin/advrocket.lua
/usr/bin/update.lua
/usr/lib/advrocket/manifest.lua
/usr/lib/advrocket/apps/station_control.lua
/usr/lib/advrocket/apps/biome_scanner.lua
/usr/lib/advrocket/apps/rocket_monitor.lua
/usr/lib/advrocket/apps/warp_controller.lua
/usr/lib/advrocket/apps/atmosphere_detector.lua
/usr/lib/advrocket/apps/planet_selector.lua
/usr/lib/advrocket/apps/laser_drill.lua
```

`.prop` 使用 OpenOS 标准安装描述：

```lua
{
  label = "AdvRocket",
  fromDir = "usr",
  root = "usr"
}
```

这样 `install AdvRocket` 会把软盘 `/usr` 下的内容安装到硬盘 `/usr`，而不会把
`.prop` 复制到硬盘根目录。

### 6.1 Canonical source

七个已经实机验证的脚本必须继续以 `oc_examples/*.lua` 为唯一源码，不在
`src/main/resources` 中维护第二份手工副本。

实现阶段扩展 `processResources`：

- 将 `oc_examples/advrocket.lua` 映射到软盘 `/usr/bin/advrocket.lua`；
- 将 `oc_examples/update.lua` 映射到软盘 `/usr/bin/update.lua`；
- 将现有七个脚本映射到软盘 `/usr/lib/advrocket/apps/`；
- 将 `.prop` 与 manifest template 从 `src/main/resources` 正常打包；
- 在 manifest 中展开 AdvancedRocketry 构建版本。

该方案保证 GitHub 上供人阅读/单独复制的脚本与 JAR 中程序软盘提供的脚本完全
一致。

---

## 7. Warp Controller API 扩展

### 7.1 新 callbacks

新增：

```lua
warp_controller.getCurrentTargetInfo()
warp_controller.getTargetInfo(id)
```

正常返回：

```lua
{
  id = 0,
  kind = "dimension", -- "dimension" or "black_hole"
  name = "Earth"
}
```

返回表可以包含现有 `StationDestinationService.describe(...)` 的附加字段，例如
`known`、`current` 和 `destination`；主界面只依赖 `id`、`kind`、`name`。

`getCurrentTargetInfo()` 始终描述当前轨道目标；`getTargetInfo(id)` 描述指定 ID，
省略参数时描述已提交目的地。后者供 Warp Controller 子程序在数字 ID 改变时显示
权威名称和类型，无效 ID 返回 `invalid_target`。

### 7.2 服务端实现规则

callback 必须：

1. 使用 `OpenComputersComponentAccess.resolveStation(this)`；
2. 通过现有 `getAutomationStation(access)` 取得受支持的 `SpaceObject`，否则返回
   `not_on_station`；
3. 在一次 callback 内读取当前 `orbitingPlanetId`；
4. 若当前值为 `SpaceObjectManager.WARPDIMID`，返回 `in_warp` soft error；
5. 使用 `StationTargetResolver.getInstance().resolve(id)`；
6. 只接受 `DIMENSION` 或 `BLACK_HOLE_STAR`；
7. 复用 `StationDestinationService.describe(station, target)` 生成名称；
8. 不通过 legacy `DimensionManager` 的“未知 id 回退主世界”行为解析；
9. 不修改 station 状态；
10. 保持非-direct callback；
11. 使用 `@Optional.Method(modid = "OpenComputers")`。

错误保持现有 getter soft-error 形式：

```lua
nil, "in_warp", "Station is currently in warp."
nil, "invalid_target", "Current orbit target is invalid."
nil, "not_on_station", "Component is not on a valid space station."
```

### 7.3 兼容性

- 不修改 `getCurrentPlanet()` 的现有返回值；
- 不删除或重命名任何 callback；
- 不改变 `getStatus()` 的既有字段；
- `warp_controller.lua` 使用新 callbacks 显示名称；旧服务端缺少 callback 时仍保留
  数字 ID 和 soft-error fallback；
- 更新 `doc/OPENCOMPUTERS_COMPONENT_REFERENCE.md`；
- 为名称解析增加 dimension、black hole、warp、invalid target 测试。

---

## 8. AdvRocket 程序软盘物品

### 8.1 物品实现选择

程序软盘在玩法和配方层面是 AdvancedRocketry 新内容，但技术上实现为 OC 官方
注册的 floppy variant，而不是独立 Java `Item`：

```java
ItemStack programDisk = li.cil.oc.api.Items.registerFloppy(
        "AdvRocket",
        6, // cyan
        fileSystemFactory,
        false);
```

| 属性 | 设计 |
|---|---|
| 显示/文件系统 label | `AdvRocket` |
| 颜色 | Cyan，Minecraft dye index `6` |
| Creative 位置 | OpenComputers creative item list |
| 贴图 | OpenComputers 原生 cyan floppy |
| 文件系统 | AdvancedRocketry JAR 中只读资源 |
| Recipe cycling | `false` |
| 单独 registry id | 无 |
| OC 缺失时 | 完全不注册 |

`doRecipeCycling=false`，避免该程序盘被 OC 的软盘循环配方转换成其他 loot disk；
唯一正常取得方式是本规格定义的 Linker 配方或创造模式。

### 8.2 为什么不创建普通 AR Item

使用官方 registered floppy variant 可以同时避免：

- 无 OC 时注册一个无法工作的空物品；
- 在 client icon registration 阶段引用不存在的 OC texture；
- 自行实现 OC filesystem item driver；
- 复制 OC 贴图造成资源重复与版本不一致；
- 为条件内容占用额外 numeric item id；
- 把 OC API 变成 required dependency。

该变体仍具有唯一 NBT factory id，由 AdvancedRocketry 在自身 init 生命周期注册，
Creative/NEI 中可与普通 Floppy Disk 区分，并可作为独立配方输出。

### 8.3 只读文件系统 factory

factory 使用：

```java
FileSystem.fromClass(
        OpenComputersProgramDiskRegistration.class,
        "advancedrocketry",
        "loot/advrocket")
```

对应 JAR 路径：

```text
/assets/advancedrocketry/loot/advrocket/
```

factory id/注册名称必须永久保持稳定，不包含 mod 版本号。已有程序软盘在更新
AdvancedRocketry JAR 后，会通过相同 factory id 挂载新 JAR 内的最新资源，因此
玩家不需要重新合成软盘即可执行 `update`。

### 8.4 注册阶段

OpenComputers 文档要求 `Items.registerFloppy` 和 `FileSystem` API 在 init 或更晚
阶段使用。本项目应在 `FMLInitializationEvent` 中注册：

```text
preInit
  CompatibilityMgr.getLoadedMods()

init
  if CompatibilityMgr.openComputersLoaded
    OpenComputersProgramDiskRegistration.register()
```

当前 `@Mod` 已声明 `after:OpenComputers`，无需增加 required dependency。

如果 OC 已加载但 API 返回 null，必须记录 error 并跳过软盘和配方注册，不能因一个
可选内容注册失败而让 AdvancedRocketry 整体崩溃。

---

## 9. 条件无序合成配方

### 9.1 配方

```text
LibVulpes Linker + OpenComputers Blank Floppy Disk
  -> AdvRocket Program Disk
```

要求：

- shapeless；
- 恰好两个非空输入；
- Linker 通过 `LibVulpesItems.itemLinker` 匹配；
- Floppy 通过 OC `Items.get("floppy")` 的 canonical blank stack 匹配；
- 输出始终使用 `registerFloppy(...)` 返回值的 copy；
- Linker 与 Floppy 均正常消耗；
- OC 不存在时不实例化 recipe class，也不注册配方。

### 9.2 必须拒绝有数据的软盘

Forge 1.7.10 的普通 shapeless ItemStack 匹配不能安全区分同一 OC item/meta 下的
blank floppy、loot disk 和已有文件系统数据的 floppy。直接使用
`GameRegistry.addShapelessRecipe(...)` 可能误吞 OpenOS 安装盘或玩家数据盘。

因此实现一个仅在 OC 存在时加载的自定义 `IRecipe`：

- item 与 meta 必须等于 canonical blank floppy；
- candidate 不得带 filesystem/loot/custom NBT；
- 任何 `oc:lootFactory` 程序盘必须拒绝；
- 有额外输入时拒绝；
- 不读取或依赖 OC 私有 Scala 类；
- 只使用公开 `li.cil.oc.api.Items` 和 Minecraft `ItemStack` API。

这使配方保持用户要求的无序形式，同时避免不可恢复的数据损失。

---

## 10. OpenComputers 可选依赖隔离

### 10.1 类边界

所有新增 OC 强类型引用集中在：

```text
zmaster587.advancedRocketry.integration.opencomputers
  OpenComputersProgramDiskRegistration.java
  AdvRocketProgramDiskRecipe.java
```

约束：

- `AdvancedRocketry` 主类不得新增 `li.cil.oc.*` import；
- `AdvancedRocketryItems` 不保存 OC 类型字段；
- common proxy/client proxy 不新增 OC 类型签名；
- 注册类只在 `CompatibilityMgr.openComputersLoaded == true` 后首次加载；
- recipe class 只由注册类构造；
- 不在 static initializer 中提前访问 `API.items` 或 `API.fileSystem`；
- 不使用反射查找 OC item；
- build 继续使用 `compileOnly` API；
- 最终 JAR 不包含 `li/cil/oc/api/**`。

主类可以持有 Minecraft `ItemStack` 返回值或仅让注册类内部持有；不得把
`ItemInfo`、OC `FileSystem` 或 `ManagedEnvironment` 泄漏到通用类字段/方法签名。

### 10.2 无 OC 启动保证

无 OC 环境的启动验证必须覆盖：

- client preInit/init/postInit；
- dedicated server preInit/init/postInit；
- Creative tab 构建；
- NEI 存在与不存在；
- 新世界与已有 AR 世界；
- `Class.forName`/FML optional stripping 过程中无 `NoClassDefFoundError`；
- AdvancedRocketry 原有九个 OC component 宿主仍能在无 OC 时加载 TileEntity 类。

---

## 11. `install` 流程

### 11.1 玩家流程

1. 合成并插入 `AdvRocket` 程序软盘；
2. 启动装有 OpenOS 的电脑；
3. 执行：

```sh
install AdvRocket
```

如果只有一个合法 source 和一个 writable target，也允许直接执行：

```sh
install
```

4. 接受 OpenOS 标准安装确认；
5. 安装完成后执行：

```sh
advrocket
```

安装器不要求重启电脑，也不修改 boot address、硬盘 label、`/home/.shrc` 或
`/etc/rc.cfg`。

### 11.2 安装覆盖范围

初次安装只创建/覆盖 manifest 列出的 AdvRocket 文件：

- `/usr/bin/advrocket.lua`
- `/usr/bin/update.lua`
- `/usr/lib/advrocket/manifest.lua`
- `/usr/lib/advrocket/apps/*.lua`

本规格按用户要求提供全局 `update` 命令。OpenOS `1.12.44-GTNH` 基础系统自身不
包含 `/bin/update.lua` 或 `/usr/bin/update.lua`。如果未来整合包安装了另一个同名
命令，则需要在实现前将此决定改为 namespaced `advrocket-update`；默认方案仍保留
用户要求的 `update`。

---

## 12. `update` 覆盖更新流程

### 12.1 玩家流程

1. 将整合包中的 AdvancedRocketry JAR 更新到新版；
2. 启动游戏与 OpenOS 电脑；
3. 插入原有 `AdvRocket` 程序软盘；
4. 执行：

```sh
update
```

5. 更新器从软盘当前挂载的新版 JAR 资源复制全部 managed files；
6. 更新成功后重新执行 `advrocket`。

更新不需要重新合成软盘，也不需要网络连接。

### 12.2 Manifest

`/usr/lib/advrocket/manifest.lua` 至少包含：

```lua
return {
  packageId = "advancedRocketry:advrocket",
  formatVersion = 1,
  modVersion = "1.4.3-continuation+suite.2",
  files = {
    "usr/bin/advrocket.lua",
    "usr/bin/update.lua",
    "usr/lib/advrocket/apps/station_control.lua",
    "usr/lib/advrocket/apps/biome_scanner.lua",
    "usr/lib/advrocket/apps/rocket_monitor.lua",
    "usr/lib/advrocket/apps/warp_controller.lua",
    "usr/lib/advrocket/apps/atmosphere_detector.lua",
    "usr/lib/advrocket/apps/planet_selector.lua",
    "usr/lib/advrocket/apps/laser_drill.lua",
    "usr/lib/advrocket/manifest.lua"
  },
  remove = {}
}
```

`modVersion` 在 Gradle `processResources` 阶段由模组版本和独立 suite revision
共同展开，不能手工复制一个容易忘记更新的版本号。suite revision 必须在同一模组
版本内修改 managed Lua 文件时递增，确保已安装电脑上的 `update` 不会误判为同版本。

### 12.3 Source 发现

`update` 必须：

1. 枚举 mounted filesystems；
2. 排除当前 writable root 与 `/tmp`；
3. 只接受 read-only filesystem；
4. 查找 `usr/lib/advrocket/manifest.lua`；
5. 验证 `packageId == "advancedRocketry:advrocket"`；
6. 验证 `formatVersion == 1`；
7. 恰好找到一个合法 source 才继续；
8. 未插盘时清楚提示 `Insert the AdvRocket program disk`；
9. 多张匹配盘同时存在时 fail closed，要求玩家只保留一张。

不能只依赖可修改的 filesystem label 判断 source。

### 12.4 路径安全

更新器在复制前验证 manifest 中每个路径：

- 必须是相对路径；
- 必须位于 `usr/bin/` 或 `usr/lib/advrocket/`；
- 不允许 `..`；
- 不允许空路径；
- 不允许重复路径；
- 不允许指向目录外的 symlink；
- 不允许覆盖 `/home`、`/etc`、boot filesystem 或其他程序目录。

### 12.5 覆盖与失败恢复

更新应采用 staged replace，而不是逐个直接截断目标文件：

1. 验证完整 manifest 和全部 source 文件可读；
2. 在目标硬盘建立 AdvRocket 专用 staging 目录；
3. 把所有新文件复制到 staging；
4. 验证 staging 文件存在且长度与 source 相同；
5. 将旧 managed files 移入 rollback 目录；
6. 将 staging 文件 rename 到最终路径；
7. 最后替换本地 manifest；
8. 成功后删除 rollback；
9. 任一步失败则恢复旧文件并返回 non-zero。

更新器自身在执行前已经被 Lua 载入内存，因此可以安全替换
`/usr/bin/update.lua`；本次进程继续执行已加载 chunk，下次运行使用新版。

`remove` 列表只允许删除同一安全前缀下、由旧 manifest 管理的文件，并进入 rollback
流程。不得按目录递归删除未知内容。

### 12.6 版本行为

- source 与 installed `modVersion` 相同：显示 `Already up to date`，不写盘；
- 版本不同：玩家显式执行 `update` 即表示以插入软盘为权威版本；
- manifest 缺失或格式过新：拒绝更新；
- installed manifest 缺失：提示先执行 `install AdvRocket`；
- 不尝试在线查询“最新版本”；
- 不依赖字符串版本大小比较来猜测版本新旧。

---

## 13. 构建与资源打包

### 13.1 `processResources`

实现阶段扩展现有 Gradle task，但不改变 Java 7/8 compatibility 或现有 JAR 产物：

- `oc_examples` 脚本是 task input；
- 七个 app 只选择明确白名单，不能把 README/spec 临时文件打进软盘；
- launcher/updater 映射到 `/usr/bin`；
- apps 映射到 `/usr/lib/advrocket/apps`；
- manifest 展开 `project.version`；
- `.prop` 保留原文件名；
- universal 与 deobf JAR 都应包含相同程序资源。

### 13.2 JAR 断言

构建测试检查 JAR 中恰好存在：

```text
assets/advancedrocketry/loot/advrocket/.prop
assets/advancedrocketry/loot/advrocket/usr/bin/advrocket.lua
assets/advancedrocketry/loot/advrocket/usr/bin/update.lua
assets/advancedrocketry/loot/advrocket/usr/lib/advrocket/manifest.lua
assets/advancedrocketry/loot/advrocket/usr/lib/advrocket/apps/*.lua
```

并检查不存在：

```text
li/cil/oc/api/
```

---

## 14. 预计代码改动

### 14.1 新增

| 文件 | 职责 |
|---|---|
| `oc_examples/advrocket.lua` | 主界面、实时信息与子程序导航 |
| `oc_examples/update.lua` | 本地只读软盘覆盖更新器 |
| `integration/opencomputers/OpenComputersProgramDiskRegistration.java` | 条件注册软盘、factory 与 recipe |
| `integration/opencomputers/AdvRocketProgramDiskRecipe.java` | NBT 安全的 shapeless recipe |
| `assets/advancedrocketry/loot/advrocket/.prop` | OpenOS install metadata |
| `assets/advancedrocketry/loot/advrocket/usr/lib/advrocket/manifest.lua` | package manifest template |
| 对应 Java/Lua tests | 可选依赖、recipe、GUI 和 update 验证 |

### 14.2 修改

| 文件 | 改动 |
|---|---|
| `AdvancedRocketry.java` | init 阶段在 OC 存在时调用隔离注册类 |
| `TileWarpShipMonitor.java` | 新增 `getCurrentTargetInfo()` |
| `build.gradle` | 将 canonical `oc_examples` 映射进软盘资源 |
| `doc/OPENCOMPUTERS_COMPONENT_REFERENCE.md` | 记录新 callback |
| `oc_examples/README.md` | 记录 `advrocket`、程序盘、install/update 流程 |
| 七个现有 Lua | 仅在验证需要时补充清晰 exit code；不改业务逻辑 |

LibVulpes 不需要修改。Linker 通过现有公开
`LibVulpesItems.itemLinker` 作为 recipe ingredient。

---

## 15. 验证计划

### 15.1 Java/Forge

- `clean build`；
- 新 callback 的 dimension name 测试；
- black-hole target name 测试；
- warp target 返回 `in_warp` 测试；
- invalid target fail-closed 测试；
- recipe 只接受 Linker + canonical blank floppy；
- recipe 拒绝 OpenOS/loot/program/有数据软盘；
- recipe 拒绝多余输入；
- registration factory 路径存在；
- JAR 不包含 OC API；
- OC 存在时 client/server 启动；
- OC 缺失时 client/server 启动。

### 15.2 Lua 语法与模拟

- Lua 5.2 syntax parse；
- `50x16`、`60x20`、`80x25` 无绘制越界；
- 七个触摸按钮逐一命中正确程序；
- 数字键 `1..7` 逐一命中；
- 键盘选择与 Enter；
- Warp/Altitude 都在线；
- 两者都缺失并显示红色 `ERROR`；
- 只缺失其中一个；
- component remove/add 自动恢复；
- Warp `in_warp` 显示黄色状态；
- callback soft error 不终止 GUI；
- target 长名称截断；
- 子程序 `Q` 返回主界面；
- 主界面 `Q` 返回 OpenOS；
- 子程序 error 后主界面继续运行；
- 最终显示状态恢复。

### 15.3 安装与更新实机矩阵

| 场景 | 预期 |
|---|---|
| OC 未安装 | 无软盘、无配方、AR 正常启动 |
| OC 已安装 | Creative/NEI 可见 AdvRocket 盘 |
| Blank floppy + Linker | 生成一张 AdvRocket 盘 |
| OpenOS disk + Linker | 不匹配，原盘不被吞 |
| 初次 `install AdvRocket` | 安装 9 个 Lua 文件和 1 个 manifest |
| 安装后 `advrocket` | 主界面可运行 |
| 子程序按 `Q` | 返回主界面 |
| 主界面按 `Q` | 返回 OpenOS |
| 更新 AR JAR 后插入旧实体软盘 | 挂载新 JAR 中的程序内容 |
| 执行 `update` | managed files 全部替换 |
| 未插盘执行 `update` | 清晰报错，不改硬盘 |
| 更新中 source 缺文件 | rollback，不留下半套程序 |
| 同版本 `update` | no-op |

---

## 16. 验收标准

满足以下全部条件才算实现完成：

1. `advrocket` 在支持的所有分辨率正常绘制；
2. 时间确认为 Minecraft 游戏内 `HH:MM`；
3. 轨道目标名称只依赖 Warp Controller component；
4. 高度只依赖 Altitude Controller component；
5. 缺 component 时主值显示红色 `ERROR`；
6. 七个按钮进入正确程序；
7. 七个程序按 `Q` 均返回主界面；
8. 主界面按 `Q` 返回 OpenOS；
9. 程序软盘使用 OC 原生 floppy 图标与 driver；
10. 无 OC 时软盘与配方均不存在，AR 正常启动；
11. 有 OC 时软盘可见且配方可用；
12. 配方不会吞掉有数据的软盘；
13. `install AdvRocket` 安装完整套件；
14. `update` 从当前 JAR-backed 软盘覆盖 managed files；
15. 更新失败可回滚；
16. universal JAR 不打包 OC API 类；
17. Component reference 与 README 同步更新；
18. Java build、Lua 模拟以及有/无 OC 冒烟测试全部通过。

---

## 17. 本规格的默认审阅决定

实现前请重点确认以下默认决定：

1. **程序盘采用 OC registered floppy variant**，不是单独的 AR registry item；
2. **软盘颜色为 Cyan**，label 为 `AdvRocket`；
3. **时间显示为 `HH:MM`**，不显示快速跳动的游戏内秒数；
4. **Warp 期间显示黄色 `IN WARP`**，只有缺失/无效状态显示红色 `ERROR`；
5. **配方只接受全新 blank floppy**，避免吞掉玩家数据盘；
6. **初次安装使用 `install AdvRocket`**；
7. **全局更新命令名为 `update`**，与用户需求一致；
8. **程序安装到 `/usr`**，而不是 `/home`；
9. **现有七个 `oc_examples` 文件保持 canonical source**，由 Gradle 打包进软盘；
10. **主界面同步启动子进程**，不对七个程序建立复杂的 launcher-specific API。

这些决定确认后即可按本文档直接进入实现。
