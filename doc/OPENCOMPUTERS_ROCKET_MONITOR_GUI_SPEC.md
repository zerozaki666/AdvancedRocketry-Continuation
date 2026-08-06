# OpenComputers Rocket Monitoring Station GUI 规格

## 0. 文档状态

- 状态：Implemented
- 目标分支：`agent/opencomputers-integration-spec`
- 基线提交：`872647be744ce82b55f9af95fec87a83fa5558cf`
- Minecraft：`1.7.10`
- Forge：`10.13.4.1614`
- OpenComputers：`1.12.44-GTNH`
- 目标组件：`monitoring_station`
- 计划新增程序：`oc_examples/rocket_monitor.lua`
- 相关程序：
  - `oc_examples/station_control.lua`
  - `oc_examples/biome_scanner.lua`
- API 参考：`doc/OPENCOMPUTERS_COMPONENT_REFERENCE.md`

本文定义面向玩家的 OpenOS GUI，不修改 Java component API。规格已经确认并由
`oc_examples/rocket_monitor.lua` 实现。

---

## 1. 目标

开发一套独立、支持触摸操作的 Rocket Monitoring Station 程序：

1. 完全沿用现有 Station Controller 与 Biome Scanner 程序的视觉语言及配色；
2. 将数量较多的监测数据拆成四个聚焦的页签，避免全部挤在同一屏；
3. 能够跟踪 linked rocket 从实体火箭到 mission 的完整生命周期；
4. 自动显示 AdvancedRocketry 的五种燃料，不要求玩家手动输入燃料类型；
5. 提供现有的安全预发射请求，并增加明确的二次确认；
6. 在 `50x16` 至 `80x25` 的屏幕分辨率范围内均可正常使用；
7. component 或 AR callback 返回 soft error 时只显示错误，不退出整个 GUI；
8. 无论正常退出、报错退出还是 OpenOS interrupt，都恢复原屏幕分辨率和颜色。

第一版只控制同一 OC 网络中枚举到的第一个 `monitoring_station`。

---

## 2. 非目标

第一版明确不做：

- 不修改或扩展现有 Java callbacks；
- 不暴露火箭解构或其他不可逆操作；
- 不调用原始的 rocket `launch()` 实现；
- 不绕过 `RocketPreLaunchEvent`、目的地校验、燃料要求或其他 AR 发射检查；
- 不选择或修改火箭目的地；
- 不显示 mission 类型、目标星球、火箭名称、驾驶员身份或 X/Z 坐标，因为当前
  API 没有提供这些信息；
- 不在同一程序内管理多个 Monitoring Station；
- 不跨程序重启保存遥测历史、曲线或日志；
- 不将“发射请求已提交”误报为“火箭已经成功起飞”；
- 不引入第三方 OpenOS GUI 库。

---

## 3. 现有 component 契约

当前 `monitoring_station` 的 19 个 callbacks 已足以完成第一版 GUI，不需要修改
Java。

### 3.1 Link 与生命周期

| 方法 | GUI 用途 |
|---|---|
| `isRocketLinked()` | 判断是否仍有 live rocket 实体连接。 |
| `isMissionLinked()` | 判断火箭转化后是否有 linked mission。 |
| `getRocketHeight()` | live rocket 时返回 Y 高度；mission 时返回配置的轨道高度估值。 |
| `prepareLaunch()` | 提交经过确认的安全发射请求。 |
| `launch()` | 兼容别名；本 GUI 不调用。 |

### 3.2 Rocket 遥测

| 方法 | GUI 用途 |
|---|---|
| `getRocketStatus()` | 首选快照：height、velocity、thrust、weight、drillingPower、acceleration、hasSeat、alive。 |
| `getRocketVelocity()` | 组合快照不可用时的兼容回退。 |
| `getRocketThrust()` | 兼容回退。 |
| `getRocketWeight()` | 兼容回退。 |
| `getDrillingPower()` | 兼容回退。 |
| `getAcceleration()` | 兼容回退。 |
| `hasSeat()` | 兼容回退。 |

`getRocketStatus()` 成功时不重复调用各个单项 getter。只有组合 getter 确实不存在
时才使用回退路径。

### 3.3 Fuel 遥测

| 方法 | GUI 用途 |
|---|---|
| `getFuelStatus(type)` | 首选来源，一次获取 amount、capacity 与每 tick rate。 |
| `getFuelAmount(type)` | 组合 getter 不可用时的兼容回退。 |
| `getFuelCapacity(type)` | 兼容回退。 |
| `getFuelRate(type)` | 兼容回退。 |

固定显示顺序为：

1. `liquid`
2. `nuclear`
3. `ion`
4. `warp`
5. `impulse`

### 3.4 Mission 遥测

| 方法 | GUI 用途 |
|---|---|
| `getMissionStatus()` | 首选快照：missionId、originDimension、progress、remainingSeconds。 |
| `getMissionProgress()` | 组合 getter 不可用时的兼容回退。 |
| `getMissionRemainingTime()` | 兼容回退。 |

`missionId` 必须始终作为 string 保存和显示，不能先经过 `tonumber()`。Java 的
mission id 可能超过 Lua 能精确表示的整数范围。

---

## 4. 应用状态模型

GUI 同时读取 component 连接状态和两个 link flag，不假定 rocket 与 mission 在
生命周期切换时绝对不会短暂重叠。

| Component | Rocket link | Mission link | 显示状态 | 颜色 |
|---|---:|---:|---|---|
| 不存在 | N/A | N/A | `OFFLINE` | Bad / 红色 |
| 在线 | false | false | `IDLE` | Muted |
| 在线 | true | false | `ROCKET LINKED` | Good / 绿色 |
| 在线 | false | true | `MISSION ACTIVE` | Accent / 青色 |
| 在线 | true | true | `TRANSITION` | Warning / 黄色 |

Overview 除汇总状态外还会显示两个原始 link flag，避免优先级判断掩盖过渡状态。

### 4.1 生命周期切换

- `OFFLINE -> IDLE/ROCKET LINKED/MISSION ACTIVE`：重新连接或手动扫描；
- `ROCKET LINKED -> MISSION ACTIVE`：实体火箭转化为 AR mission；
- `MISSION ACTIVE -> IDLE`：mission 完成并解除连接；
- 任意状态 `-> OFFLINE`：component 被移除、区块卸载或返回 `invalid_tile`。

link 状态变化时立即清除不再属于新状态的数据。旧 rocket/fuel 数值不能残留在
mission 界面上，看起来像仍是实时数据。

---

## 5. 视觉设计

### 5.1 配色

与现有两个程序完全一致：

| 用途 | RGB |
|---|---|
| Background | `0x101820` |
| Panel | `0x1B2A36` |
| Active panel/tab | `0x28506B` |
| Primary text | `0xE8F1F5` |
| Muted text | `0x8EA7B3` |
| Accent/header | `0x53C7F0` |
| Good | `0x63D471` |
| Warning | `0xF2C14E` |
| Bad | `0xF25F5C` |
| Button | `0x2C4454` |
| Disabled button | `0x26323A` |
| Button text | `0xFFFFFF` |

### 5.2 分辨率与固定框架

- 实际分辨率取 GPU 最大值与 `80x25` 中较小者；
- 最低支持 `50x16`，低于此值时在修改屏幕前直接报错；
- 第 1 行：`Advanced Rocketry Rocket Monitor  v1.0.0`；
- 第 2 行：component 短地址、汇总状态、rocket link 与 mission link；
- 第 3 行：四个可触摸页签；
- 底部倒数第 2 行：短期状态/错误消息；
- 最后一行：固定键盘帮助；
- 继续使用居中标题、panel 分隔、触摸按钮以及既有的 active/disabled 样式。

### 5.3 页签

| 按键 | 页签 | 用途 |
|---:|---|---|
| `1` | Overview | Link 状态、关键遥测、生命周期摘要与安全发射。 |
| `2` | Rocket | 完整 live rocket 与性能遥测。 |
| `3` | Fuel | 五种燃料、消耗率与推算续航。 |
| `4` | Mission | Mission ID、来源维度、进度与剩余时间。 |

即使当前页签没有适用的 linked object，触摸和数字键切页仍保持可用。

---

## 6. 页面规格

### 6.1 Overview

Overview 是默认页，也是唯一包含发射控制的页面。

#### `ROCKET LINKED` 时

| 字段 | 来源 | 格式 |
|---|---|---|
| Rocket linked | `isRocketLinked()` | `YES` / `NO` |
| Mission linked | `isMissionLinked()` | `YES` / `NO` |
| Height | `getRocketStatus().height` | 两位小数，原生 world Y |
| Vertical velocity | `getRocketStatus().velocity` | 带正负号，三位小数，原生 `motionY` |
| Acceleration | `getRocketStatus().acceleration` | 三位小数，native AR units |
| Alive | `getRocketStatus().alive` | `YES` / `NO` |
| Pilot seat | `getRocketStatus().hasSeat` | `INSTALLED` / `NONE` |

页面另显示一个简短的 fuel readiness 摘要：capacity 大于 0 且 amount 大于 0 的
燃料类型数量。该摘要只描述燃料储存情况，不自行判断火箭是否“可发射”；最终规则
仍由 AdvancedRocketry 决定。

#### `MISSION ACTIVE` 时

| 字段 | 来源 | 格式 |
|---|---|---|
| Mission ID | `getMissionStatus().missionId` | 原始 string |
| Origin dimension | `getMissionStatus().originDimension` | 有符号整数 |
| Progress | `getMissionStatus().progress` | 百分比与进度条 |
| Remaining | `getMissionStatus().remainingSeconds` | `DD:HH:MM:SS` 或 `HH:MM:SS` |
| Orbit estimate | `getRocketHeight()` | AR 配置的轨道高度估值 |

#### `IDLE` 时

显示：

- `No rocket or mission is currently linked.`
- 将 Monitoring Station 连接到火箭的简短提示；
- 当前 component 地址。

`IDLE` 属于正常状态，不显示成错误。

#### 安全发射流程

只有同时满足以下条件时，发射按钮才可用：

- component 在线；
- `isRocketLinked() == true`；
- `isMissionLinked() == false`；
- 最新 rocket snapshot 中 `alive == true`；
- 当前没有等待确认或正在提交的发射请求。

第一次激活会把 `ARM LAUNCH` 切换为 `CONFIRM LAUNCH`，并显示内嵌确认面板。
面板明确提示：请求仍会经过 AdvancedRocketry 的正常发射校验。

- 确认状态 8 秒后自动失效；
- 点击 `CONFIRM LAUNCH`、按 Enter 或在 armed 状态再次按 `L` 时，只调用一次
  `prepareLaunch()`；
- 点击 `CANCEL`、按 Escape、切换页签、component 断线或 link 状态变化时取消；
- 成功返回后用绿色显示 `Launch request submitted`；
- soft error 用红/黄色显示 code 与 message；
- 不能只根据 callback 的成功返回显示 `Rocket launched`；
- 之后只通过 link 与遥测状态变化反映火箭是否真正起飞。

兼容别名 `launch()` 不会被本 GUI 使用。

### 6.2 Rocket

Rocket 页显示完整的 `getRocketStatus()`：

| 字段 | 显示格式 |
|---|---|
| Height | 两位小数 |
| Vertical velocity | 带正负号，三位小数 |
| Thrust | 两位小数；整值可简化为整数 |
| Weight | 两位小数；整值可简化为整数 |
| Acceleration | 三位小数，并标注 `native AR units` |
| Drilling power | 两位小数；整值可简化为整数 |
| Pilot seat | `INSTALLED` / `NONE` |
| Entity alive | `YES` / `NO` |

当 weight 大于 0 时可派生显示 `Thrust / Weight`。它必须标注为普通数值比，而非
Earth TWR，因为 callback 返回的是游戏原生统计值，不是严格的 SI 力与质量。

没有 live rocket 时：

- 显示 `Live rocket telemetry unavailable`；
- 如果已有 mission，则额外显示 `Rocket has transitioned to a mission`；
- 不持续调用 rocket-only getters，也不每秒重复刷 `rocket_not_found`。

### 6.3 Fuel

Fuel 页始终为五种燃料各保留一行，包括 capacity 为 0 的类型。

| 列 | 含义 |
|---|---|
| Type | `LIQUID`、`NUCLEAR`、`ION`、`WARP`、`IMPULSE` |
| Amount | 当前储量 |
| Capacity | 最大储量 |
| Fill | `amount / capacity`，显示范围限制为 `0..100%` |
| Rate | 每 tick 消耗 |
| Endurance | rate 为正数时推算的剩余时间 |

Endurance 只用于显示：

```text
seconds = amount / rate / 20
```

capacity 为 0、rate 小于等于 0 或任一必要字段不可用时显示 `--`。GUI 不使用
Endurance 判断是否允许发射。

#### Fuel 行显示规则

- Capacity `<= 0`：整行 muted，并显示 `NOT INSTALLED`；
- Fill 高于 50%：绿色；
- Fill 介于 20% 至 50%：黄色；
- Fill 低于 20%：红色；
- 进度条会 clamp，但原始非负数值仍显示出来以便诊断；
- `80x25` 下显示全部列和文本进度条；
- 窄屏优先隐藏进度条，再缩短 Endurance；Type、Amount/Capacity、Fill、Rate
  始终保留。

只在 live rocket 存在时读取 fuel。火箭转化为 mission 后不再有 live
`StatsRocket`，因此 Fuel 页改为显示说明性 unavailable 状态。

### 6.4 Mission

Mission 页以 `getMissionStatus()` 为首选数据源：

| 字段 | 显示格式 |
|---|---|
| Mission ID | 原始十进制 string，不转换成 Lua number |
| Origin dimension | 有符号整数 |
| Progress | 一位小数百分比与宽度自适应进度条 |
| Remaining time | 不足一天为 `HH:MM:SS`；一天以上为 `DD:HH:MM:SS` |
| Orbit estimate | mission linked 时由 `getRocketHeight()` 取得 |

即使 Java 已经限制进度范围，GUI 显示前仍 clamp 到 `0..1`。如果 progress 达到
100% 但 mission 尚未 unlink，显示 `Completing...`，不能提前伪造 completed
状态。

没有 mission 时：

- 显示 `No linked mission`，作为正常状态；
- 如果仍有 live rocket，则提示 mission 遥测会在火箭转化后出现；
- 不持续调用 mission-only getters。

---

## 7. 输入与操作

| 输入 | 行为 |
|---|---|
| 触摸页签 / `1`-`4` | 切换页面。 |
| `R` | 重新扫描 OC 网络，并连接第一个 `monitoring_station`。 |
| `L` | 在 Overview 中 arm launch；已 armed 时确认。 |
| Enter | 确认已 armed 的发射请求。 |
| Escape / `C` | 取消发射确认。 |
| `Q` | 退出并恢复屏幕。 |
| OpenOS interrupt | 退出并恢复屏幕。 |

触屏界面提供明确的 `ARM LAUNCH`、`CONFIRM LAUNCH` 与 `CANCEL` 按钮。
不在 Overview 或发射条件不满足时，键盘发射快捷键不执行任何操作。

---

## 8. 轮询、缓存与 OC call budget

### 8.1 刷新频率

- Event loop 与两个 link flag：每秒一次；
- Overview、Rocket、Mission 当前页遥测：每秒一次；
- Fuel 当前页遥测：每两秒一次，因为需要最多五次结构化 callback；
- 重新扫描、切换页签、提交发射、component added/removed 后立即刷新。

### 8.2 只读取当前页面

全局只持续刷新 link flags，详细数据只读取当前页，避免把 OC component call budget
浪费在三个隐藏页面：

- Overview：读取当前适用的 rocket 或 mission snapshot，并节流读取 fuel 摘要；
- Rocket：读取一次组合 rocket snapshot；
- Fuel：每两秒读取五次 `getFuelStatus(type)`；
- Mission：读取一次 mission snapshot 与 orbit estimate。

缓存记录最近一次成功更新时间。当前页刷新失败时，状态栏显示错误，同时把旧数据标成
`STALE`，不能继续伪装成 live 数据。link 状态变化则直接清除不兼容缓存。

---

## 9. OpenComputers 调用兼容性

所有 callbacks 均通过 component 地址调用：

```lua
component.invoke(address, methodName, ...)
```

禁止使用：

```lua
type(proxy[methodName]) == "function"
```

GTNH OpenComputers 的动态代理不保证 network callback 会表现为普通 Lua function。
这是 `station_control.lua v1.0.1` 已经验证并写入 reference 的同一兼容规则。

每次调用都包在 `pcall` 中。Getter soft error 按以下结构处理：

```lua
nil, code, message
```

Mutator 按以下结构处理：

```lua
true, value
-- 或
false, code, message
```

发射流程还必须防止重复 touch/key event；一次确认最多产生一次
`prepareLaunch()` 调用。

---

## 10. 错误处理

| 情况 | GUI 行为 |
|---|---|
| 无 component | 显示 offline 页面，保留 `R`。 |
| `invalid_tile` | 丢弃地址并标为 offline，等待自动重连或手动 rescan。 |
| `rocket_not_found` | 重新读取两个 link flag，清除 rocket/fuel 数据，转为正常 unavailable 状态。 |
| `mission_not_found` | 重新读取两个 link flag，清除 mission 数据，转为正常 unavailable 状态。 |
| `fuel_type_not_found` | 只把对应 fuel 行标成 API error，其余行继续显示。 |
| 组合 getter 不存在 | 回退到单项 getters。 |
| 单项回退也不存在 | 显示当前页 compatibility error，程序继续运行。 |
| OC exception | 状态栏显示 code/message，其余有效 panel 继续工作。 |
| Component removed | 清除 live cache、取消 launch confirmation 并显示 offline。 |
| Screen/GPU 报错 | 尝试恢复屏幕后将错误写到 stderr。 |

一个 fuel 类型或一个可选字段失败，不能导致整套应用白屏。

---

## 11. 已实现文件范围

本阶段修改：

- 新增 `oc_examples/rocket_monitor.lua`；
- 更新 `oc_examples/README.md`，加入前置条件、安装方式、操作说明和发射安全语义；
- 保留本文 `doc/OPENCOMPUTERS_ROCKET_MONITOR_GUI_SPEC.md`，状态为 Implemented。

当前不需要修改 Java 或 component reference。如果实现阶段发现真正的 API 缺口，
必须单独提出，而不是静默扩大本 GUI 的开发范围。

---

## 12. 验证结果与游戏内检查

### 12.1 Lua 静态与模拟测试

以下检查已经通过：

1. Lua 5.2/OpenOS 兼容语法解析；
2. proxy 字段不暴露普通 Lua function、但 `component.invoke` 可用的 mock OC 环境；
3. `OFFLINE`、`IDLE`、`ROCKET LINKED`、`MISSION ACTIVE`、
   `TRANSITION` 五种显示状态；
4. rocket 与 mission 组合 snapshot 不存在时的单项 getter 回退；
5. mission ID 保持 string，不转换成 number；
6. 五种 fuel、零 capacity、零 rate 与单行失败；
7. 键盘与触屏发射确认均恰好调用一次 `prepareLaunch()`；
8. 取消、超时、切页与 link 变化不会意外触发发射；
9. 成功文案为 `Launch request submitted`，不会误报已经起飞；
10. `50x16`、`60x20`、`80x25` 绘制边界与退出恢复。

### 12.2 仓库验证

- `git diff --check` 通过；
- 所有调用的方法名均与当前 `monitoring_station` callbacks 对照通过；
- 本阶段没有 Java、资源或 Gradle 变更，因此按确认要求不启动 GitHub Actions。

### 12.3 游戏内冒烟测试

1. 将一台完整的 Rocket Monitoring Station 连接到 OC 网络；
2. 无 linked object 时启动，确认显示 `IDLE`；
3. 连接一枚建造完成的火箭，检查 Overview、Rocket、Fuel；
4. 确认未安装的 fuel 类型显示 muted，而不是 error；
5. 分别用触摸和键盘测试 arm/cancel；
6. 只提交一次发射请求，并确认 AR 的正常发射校验仍会执行；
7. 观察 rocket 转 mission，确认旧 rocket/fuel 数据立即消失；
8. 验证 mission 进度与剩余时间持续更新直到完成/unlink；
9. 拔掉并重新连接 OC cable，按 `R` 验证恢复。

---

## 13. 已确认的默认决策

当前实现采用：

1. 四个页签：Overview、Rocket、Fuel、Mission；
2. 发射只放在 Overview，并采用 8 秒有效的两阶段确认；
3. 五种 fuel 始终全部显示，capacity 为 0 时标记 `NOT INSTALLED`；
4. Fuel 每两秒刷新一次，其他当前页遥测每秒刷新一次；
5. 第一版只显示当前 snapshot，不做历史曲线；
6. 本阶段不修改 Java/API。
