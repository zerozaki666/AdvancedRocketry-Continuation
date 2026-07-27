# AdvancedRocketry-TFRU ahead commit 移植审计

审计范围：`kuzuanpa/AdvancedRocketry-TFRU` 在共同基线 `d79d71d` 之后至
`161cd3b6` 的全部 56 条提交，包含首条 `d0c66bc0` 与末条 `161cd3b6`。目标是
移植通用 bugfix 与 feature，同时排除 TerraFirmaCraft、TFRU 整合包、
GT6/GregAPI 强耦合和戴森球内容。这里的“修改后移植”也包括用更安全或兼容
Java 7/Forge 1.7.10 的实现重写原提交。

| # | 提交 | 原主题 | 结论 | 判断及移植修改点 |
|---:|:---|:---|:---|:---|
| 1 | `d0c66bc0` | GT tank duplicate / bucket rocket filling | 修改后移植 | 保留通用流体复制与桶装填漏洞修复；不新增 GT6/GregAPI 调用，并将氧气补充过程改为先验证后原子式扣除。 |
| 2 | `f0718e4a` | fix NPE | 修改后移植 | 移植通用空值保护，避免沿用与整合包环境绑定的调用路径。 |
| 3 | `41db6d0d` | translations | 直接移植 | 合并通用英文和中文文本；不加入 TFRU 品牌文本。 |
| 4 | `5acc5b28` | Recipe XML NBTTag | 修改后移植 | 保留 Recipe XML 物品 NBT 读写，并增加类型与无效内容检查。 |
| 5 | `007e0a24` | delimiter / startup warnings | 修改后移植 | XML 分隔符统一为分号；模型异常改为记录日志而非 `System.exit`。 |
| 6 | `8efbc461` | FTL Dilithium OreDict | 修改后移植 | Warp Core 接受 `gemDilithium` 与 `crystalDilithium`，按配置燃料点和实际需要向上取整消耗。 |
| 7 | `56557602` | planet XML `<spawnable>` | 修改后移植 | 完成注册名/类名解析、权重、NBT、XML 转义和存档兼容；只替换明确配置的生物类型，并保护实体身份/位置等 NBT。 |
| 8 | `1afd1be6` | TFC dimension move | 修改后移植 | 明确排除 TFC 强制 respawn 到维度 11；该提交移除 PotentialSpawns FATAL 日志的通用效果由目标基线及新 spawn 实现保持，无需复制 TFC 补丁。 |
| 9 | `f1e649af` | Sun dimension / Dyson Sphere | 不移植 | 恒星维度与戴森球均为明确排除内容。 |
| 10 | `d00a6c75` | Planet Selector GUI optimization | 修改后移植 | 合并平滑缩放、输入节流和导航修复，同时保留原有星体 ID 偏移约定。 |
| 11 | `e95bc6bf` | Planet Selector GUI optimization | 修改后移植 | 合并后续缩放/坐标修复，修正星系视图坐标随 zoom 缩放。 |
| 12 | `a2bf2bf9` | Dyson renderer rotation | 不移植 | 只作用于戴森球渲染器。 |
| 13 | `02234eb4` | Jetpack renderer initialization | 直接移植 | 渲染器改为静态单例，避免每次玩家渲染事件重复构造。 |
| 14 | `6fe6f706` | multistage rocket basics | 修改后移植 | 加入两种分级发动机、配方、语言文本、分级结构数据和查询 API；实体自动分离/点火尚未启用。 |
| 15 | `fcfb8a6e` | multistage computation thread | 修改后移植 | 重写为每服务器生命周期一条 daemon 工作线程、阻塞队列和 generation token；主线程先深拷贝含 TileEntity NBT 的快照，worker 不接触实时火箭。 |
| 16 | `81d105a5` | small optimizations | 修改后移植 | 仅选取不改变原任务语义的通用空值、集合和渲染优化。 |
| 17 | `c910dc4a` | projector API | 修改后移植 | 投影仪使用 libVulpes 的字符通配 API 和延迟注册；不要求 GT6。 |
| 18 | `08187140` | TFRU version tag | 不移植 | 整合包专用版本标识。 |
| 19 | `640a2ee7` | TFRU version tag | 不移植 | 整合包专用版本标识的后续调整。 |
| 20 | `b1c68d58` | GregTech projector support | 不移植 | 直接依赖 GregAPI/GT6 tileentity，按要求排除。 |
| 21 | `cc54e236` | GregTech projection optimization | 不移植 | 仅优化上一条被排除的 GT6 适配。 |
| 22 | `81f4a42b` | GregTech projector bugfix | 不移植 | 仅修复被排除的 GT6 投影路径。 |
| 23 | `a10f7fd4` | Jetpack renderer singleton | 合并提交（不重复） | 与第 13 条目标相同，最终实现只保留一个静态实例。 |
| 24 | `0eb0d48d` | mixed bugfix | 修改后移植 | 选择性合并通用渲染、链接器、空值与机器修复；剔除整合包耦合部分。 |
| 25 | `c9bce41b` | catch GregTech crash | 不移植 | 异常来自被排除的 GT6 幻影/投影适配。 |
| 26 | `2ab6ce0b` | catch GregTech crash again | 不移植 | 同上，仅继续包裹 GT6 专用路径。 |
| 27 | `a81318e4` | TFRU environment detection | 不移植 | 整合包运行环境检测和专用日志策略。 |
| 28 | `e853a154` | TFRU environment fix | 不移植 | 仅修复上一条被排除的 TFRU 检测。 |
| 29 | `ce568b86` | nullable checks | 修改后移植 | 不做全仓格式迁移；吸收电梯/氧气/卫星损坏数据、恒星、对接端口、收发器、粒子字符串、温室温度、星图及 NEI 本地化等实际通用修复。 |
| 30 | `a5dd952e` | warnings / nullable / projector | 修改后移植 | 选择性采用通用 projector，以及维度生成、着陆点 NBT、燃料/任务、喷气背包、矿脉图、星图/全息与进度渲染的空值或浮点运算修复；拒绝格式 churn 和 GT6 分支。 |
| 31 | `f7ec2987` | Phantom Block bugfix | 修改后移植 | 加入通用、可翻译的幻影方块提示和安全物品检查。 |
| 32 | `2fa1e7bf` | XML bugfix | 直接移植 | 修复 XML 配方/属性分隔处理，保持旧格式兼容。 |
| 33 | `60e2b3d3` | Phantom translation / Dyson Cloud | 修改后移植 | 只移植幻影方块本地化；戴森云代码与资源全部排除。 |
| 34 | `c8948814` | Dyson render update | 不移植 | 只更新戴森系统渲染。 |
| 35 | `cd6410c7` | linker / rocket interaction fix | 修改后移植 | 修复 linker 状态保存、交互判断与本地化消息，保持原火箭任务语义。 |
| 36 | `a3846125` | model updates | 修改后移植 | 只带入通用火箭液箱模型/贴图；恒星、戴森与整合包 seat 资源不带入。 |
| 37 | `af333d5b` | libraries | 不移植 | 为整合包构建补入第三方二进制，不将 jar 提交到延续仓库。 |
| 38 | `a7cf3d2a` | merge upstream MC1.7 | 合并提交（不重复） | 合并提交本身无独立 feature；目标仓库已有共同上游历史。 |
| 39 | `8f8e966f` | libraries | 不移植 | 继续调整打包二进制，不属于源码移植。 |
| 40 | `c7bbb076` | merge upstream MC1.7 | 合并提交（不重复） | 合并提交无额外独立补丁。 |
| 41 | `ce1d98ad` | sky render / space travel dimension | 修改后移植 | 将原空间站 provider 重命名分离，新增自由空间 provider 与 LoD 星空；不带入恒星登陆/戴森/seat 方块。 |
| 42 | `c652097f` | Maven publishing versions | 不移植 | 只服务原作者发布流程；延续版采用自己的版本号和仓库构建。 |
| 43 | `3186190c` | disable selector render | 不移植 | 临时关闭已有 GUI，不是可保留的修复。 |
| 44 | `c4596415` | rocket / space render / orbital offset | 修改后移植 | 合并轨道偏移、同步及自由空间火箭修复；使用正 Y 原点、天体下限和火箭位移 floor 避免 1.7.10 虚空清除，并修正维度冲突。 |
| 45 | `c2d917fa` | simulated universe travel | 修改后移植 | 用 Java 7 重写确定性层级轨道模拟，验证缺失父级、重复 ID、自环和环路；不创建 Sun/Dyson 实体。 |
| 46 | `4b684a88` | basic rocket space travel | 修改后移植 | 只让玩家实际驾驶且持行星芯片的火箭进入自由航行；显式 seat 标志允许 `x=-1` 且保留无座位 getter/NBT 旧语义，空座及原任务保持原流程。 |
| 47 | `09f621ca` | rocket speed limit | 修改后移植 | 使用可配置的三轴速度上限，避免写死整合包速度。 |
| 48 | `8027e535` | stellar Y coordinate | 直接移植 | 网络/命令同步与显示恒星 Y 坐标，避免只处理 X/Z。 |
| 49 | `884282f4` | travel/render bugfix | 修改后移植 | 吸收与当前自由航行实现相符的传送、碰撞、显示列表及渲染修复；不复制会破坏原任务链的整段替换。 |
| 50 | `7c583186` | null crash | 修改后移植 | 自由空间渲染和模拟查询均在缺失天体时安全跳过。 |
| 51 | `70122584` | remove debug code | 直接移植 | 去除模拟/渲染调试分支和输出。 |
| 52 | `ee4f7a35` | Dyson render / README / license | 修改后移植 | 戴森渲染排除；接受 AGPL-3.0 许可要求并保留原 MIT 文本与来源说明。 |
| 53 | `845cf26d` | README / license | 修改后移植 | 重写为延续版说明，移除 TFRU 品牌及被排除功能宣称。 |
| 54 | `de7e98c6` | README | 修改后移植 | 只吸收适用于通用延续版的构建与功能说明。 |
| 55 | `d9420005` | README feature descriptions | 修改后移植 | 记录 FTL OreDict、模拟宇宙、LoD、GUI 和 bugfix；明确多级火箭仍为结构分析阶段。 |
| 56 | `161cd3b6` | render binding / Dyson assets / branding | 不移植 | 修复依赖被排除的 3D 恒星/戴森渲染路径；相关模型、贴图、Logo 和 TFRU 品牌均不加入。 |

## 汇总

- 直接移植：5 条。
- 修改后移植：31 条。
- 不移植：17 条。
- 合并提交（不重复）：3 条。
- 合计：56 条。

## 兼容性与实现说明

- 联构需要 JDK 8（配套 libVulpes 的 source/target 为 8）；AdvancedRocketry
  子项目自身的 source/target 仍为 Java 7，且没有使用 lambda、stream 或接口
  default method。
- 配套 libVulpes 最低版本提升为 `0.2.10`，用于投影仪字符 API 与延迟注册。
- 空间站维度配置使用 `spaceStationId`，自由空间使用 `freeSpaceId`；读取旧
  `spaceSpaceId` 拼写时会迁移，但不再生成错误键。
- 公开字段 `Configuration.spaceDimId` 继续以空间站 ID 为唯一语义；新增
  `freeSpaceDimId` 专用于自由航行，避免破坏旧 addon 的读写行为。
- 为保持 addon 二进制兼容，旧类名 `WorldProviderSpace` 仍代表空间站并继承
  `WorldProviderStation`，旧 `RenderSpaceSky` 也仍代表空间站天空；新自由空间
  使用 `WorldProviderFreeSpace` 与 `RenderFreeSpaceSky`。
- `loadedmod.csv` 所列 Galacticraft、ExtraPlanets、GalaxySpace 等环境中，
  AdvancedRocketry 只在自己的 planetary provider 禁用 Galacticraft 氧气逻辑，
  且不会覆盖其他模组的天空 renderer。
- 原仓库已有的通用 GregTech 兼容性保留；没有添加 TFRU 分支中的
  `gregapi`/GT6 直接源码依赖。

## 验证记录

- 所有 Java 源文件通过 Java 语法解析检查。
- `git diff --check` 无空白错误。
- 已扫描新增 Java 与构建源码：没有加入 TerraFirmaCraft、TFRU、Dyson 或新的
  GT6 projector 适配；只保留目标基线原有的通用 GregAPI/GT6 兼容依赖。
  AdvancedRocketry Java 源中未发现 Java 8 语法。
- 构建已迁移到 Gradle 7.4.2 与
  `com.anatawa12.forge:ForgeGradle:1.2-1.1.1`；受执行环境网络白名单限制，
  wrapper distribution 下载在配置/编译任务启动前被阻断。因此本次环境内无法
  完成完整 `compileJava`/`build`，不是 Java 编译报错。
