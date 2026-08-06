# AdvancedRocketry Continuation for Minecraft 1.7.10

这是 AdvancedRocketry 1.7.10 的社区延续分支。当前版本为
`1.4.3-continuation`，需要配套的
[`libVulpes-Continuation`](https://github.com/zerozaki666/libVulpes-Continuation)
`0.2.10` 或更高版本。

本版本的完整变更与升级说明见
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
