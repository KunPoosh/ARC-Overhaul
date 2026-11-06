# ARC Overhaul 实现研究

## dev.16：陆行舰战略速度

游戏 1.2.15.3 的 `Fleet.speed(ArrayList, BonusSet, WorldMap)` 从 1.5 上限起取最慢舰的 `Airship.getMainMapSpeed`，应用最大的舰长/勋章加成，最后强制至少为 0.03。此前 `ShipSpeedReportMixin` 只替换面板调用，舰队继续使用旧空气阻力公式。原生 `Fleet.tick` 按舰队速度乘毫秒及 0.0125 推进行程，ETA 与拦截也调用舰队速度。因此面板 2 km/h 的舰船可能在大地图跑 60.85 km/h；只接入正确速度仍会被约 15.4 km/h 的最低速度抬高。

`FleetSpeedMixin` 仅对陆行舰替换这一处单舰速度读取，且仅在编组含陆行舰时把最后的速度下限改成零。三处独立的低补给常量（实例速度及两种 `timeTo`）对陆行舰改为 `min(0.03, 修正后的舰队速度)`。编组、加成、上限、路线几何与纯飞艇规则仍由原生执行。公共战役 API 没有速度钩子，故使用这五处精确注入，并检查发行字节码及 Knot 实际变换。

`EffectiveSpeed.strategicMapSpeed` 共用原有摩擦求解器，但不传入战斗上下文。`Airship.tick` 写入 `CURRENT_COMBAT_DELETEME` 后没有对应清空；战略侧使用它会带入旧战场水阻及战斗可用升力。地图移动按设计升力、空气阻力及所属国家的原生地图倍率计算，战斗面板保留原有现场计算。验证范围见 [TESTING](TESTING.zh-CN.md)。

## dev.14 — 2026-11-06

dev.14 恢复文字旁边有完整高度的原版带框资源条，五项仍在两行内同时显示。极小缩放下行高不足原版固定内边距时，使用一像素边框保留可见填充。开局设置新增独立的 AI 城市数（1 至 4）和城镇数（0 至 8），两者均可填 -1 保留原版。额外 AI 城市使用原生 AI 收入，槽位容量同时计入城市和城镇；旧规则缺 `aiCities` 按 -1 读取。范围文案改用字体可显示的分隔符。

游戏 1.2.15.3 / API 0.3.7 **通过 1,079 项检查**，证据 `build/runtime-tests/dev14-final/summary.json`，JAR SHA256 `7400c17a72ffe93b865dc2989f6f809ee014fe70c9ad20152e4732657abca436`。新增覆盖混合定居点数量、原生 AI 收入、存档恢复，以及 50/100/125/150/200% 缩放下用记录 Frame 执行原生资源绘制。GPU 观感、完整世界生成和真实联机仍需实机验收。未替换已安装的游戏包。


[English](RESEARCH.md)

## dev.11：资源、履带、观战研究与 AI 城镇

2026-11-06。本次起仅支持 Airships **1.2.15.3**。研究基于其发行字节码和私有反编译材料，不分发游戏源码。下文较早版本的定位属于历史记录。

`CommandButtonsPanel.renderShipQuantities(MyDraw, Airship, int, int, int)` 在指令和直控面板中共用一个两行区域。原生 2×2 布局只在另一种物资没有容量时用维修物资替换它。`ResourceBarsMixin` 只替换此方法；`client/ResourceBars` 用最多三列、两行，原有容量或当前数量大于零就保留该项，悬浮提示显示精确数量。细条直接用原生矩形绘制，因为 `MyDraw.progressBar` 向内缩四像素，无法绘制一至三像素细条。文字按字体测量值缩短到单元格内。

`ShipLayers.ALL` 在前景 `TracksAndLegs(false)` 通道画履带。`TrackLayersMixin` 在 `Armour` 之前插入一个只画履带的通道；`TrackRenderingMixin` 将六处 `Module.wheels` 读取按选项分流到原通道或新通道，两处 `Module.legs` 读取仍留在原有通道。同一原生绘制代码保留动画与材质，不修改模块列表或物理。`DisplayOptions` 在本机 `display.json` 保存 `tracksBehindArmour`，默认 false，作用于本机所有舰船，不是设计文件属性，也不进入共享战役规则。编辑器工具栏和 MOD 详情打开同一个公共框架设置窗口。

研究修复的场景是**多人征服中，玩家观战其他势力的战斗**。`ClientButtonsMixin` 添加原生工具栏按钮；`SpectatorResearch` 检查 `MultiplayerCampaignCombatIntent.spectate`、当前战斗对象一致性、玩家拥有可控国家，以及 `playerCombatSideIndex == -1`，然后打开原生 `TechScreen(ss)`。原生输入继续调用 `StrategicScreen.backgroundTick`；科技操作仍为 `ss.w.player` 调用 `CampaignWorld.giveCommand`，联机指令沿原生传输和战斗期间执行路径处理。不直接修改本机研究字段、参战权限或网络协议。公共 `CLIENT_TICK_END` 事件把原生关闭目的地转回同一场仍在进行的观战，不覆盖战斗结束、重同步或断线转场。

`StartValues.aiTowns` 独立于人类城镇数，接受 -1 或 0–8，旧配置/冻结规则缺键按 -1 读取，共享载荷版本仍为 1。每张地图容量取原生槽位、人类额外定居点、AI 城镇三者的最大值。`WorldMap$3` 按势力类型选数量，保留连续 ID，只把人类槽位转成额外城市。显式 AI 数量沿用有界的陆块保护和精确数量校验；默认 AI 保留原生行为。CREATED 检查每个 AI 为一座城市加指定城镇，不发放人类现金/研发点。原生容量早期恢复机制不变。Acbric 0.3.6 起由房主分发这些玩法规则。

同一 dev.11 JAR 在 1.2.15.3、API 0.3.6 和 0.3.7 上分别通过 809 项。资源绘制使用记录画布，履带验证通道/分流，观战指令使用捕获网络传输层。完整世界生成、GPU 观感和真实多人会话尚未验证。复现与证据见[测试手册](TESTING.zh-CN.md)。

## 历史第一阶段：征服开局设置

2026-09-26。dev.2 已实现首个功能，两版游戏各通过 71 项定向检查；完整地图生成、初始资产、GUI 和联机待验收。本报告保留静态定位，不把签名或局部测试视为完整兼容保证。

## 已确认的含义

用户已明确选择：每个人类势力开局拥有的城市、城镇和现金；AI 保持原版数量与现金规则。不是全地图总数，也不改变势力数量。修改地图生成仍会改变随机过程，因此不保证 AI 坐标/资产与原版同种子逐项一致。

金钱采用“初始建筑/舰船配置结束，玩家进入地图时可用的现金”。三项独立使用 -1 表示原版。当前自定义范围：城市 1–4、城镇 0–8、现金 0–1000000。

## 原版调用链与定位

参考为维护者私有保存的 Steam 1.2.15.2 反编译快照。下表行号仅属于该快照，随反编译工具和版本改变；游戏源码与研究二进制不进入仓库，方法/类名用于定位接入点。

| 位置 | 行号附近 | 职责 |
|---|---:|---|
| `asplit-A/com/zarkonnen/airships/GameSetupScreen.java` | 137、167、188 | 开局界面输入、`startGame()` 和创建 `CampaignWorld`；随后切换到 `WorldGenScreen`。 |
| 同文件 | 195、257、329、341 | 绘制开局页、设置滚动区域、`getHeight()`、设置内容 `draw()`。新增控件必须增加滚动高度，不覆盖原生“开始”按钮。 |
| `asplit-B/com/zarkonnen/airships/WorldGenScreen.java` | 32、43 | 只负责生成进度显示；`input()` 驱动 `w.map.doSetup()`。不是开局设置界面。 |
| `asplit-B/com/zarkonnen/airships/WorldMap.java` | 237、255 | 私有 `SetupStage[]`；按 `size.empires * (1 + size.townsPerEmpire)` 分配 `approxCityDist`。 |
| 同文件 | 285–381 | 主城生成阶段，原版每次创建一个势力和一座非城镇城市；`setupInfos` 前几项对应人类玩家。 |
| 同文件 | 383–419 | 城镇阶段，轮流为势力生成城镇；找不到位置时会跳过，配置数量不天然等于实际落地数量。 |
| 同文件 | 435–451 | 收集城镇并选取祭坛，直接按 `max(2, empires/3+1)` 访问列表；允许零城镇或太少城镇需要保护边界。 |
| 同文件 | 660–682、3389 | 领地距离缓存按城市 ID 索引；`getCity()` 会建立城市缓存。不能生成结束后随意加删领地。 |
| 同文件 | 700–710 | “Drawing borders”阶段先添加难度预算，再调用 `CitySetup.setup`，最后追加最终现金。 |
| 同文件 | 1417–1418 | `setupPlayer()` 之后马上首次自动存档；开局最终余额必须早于该存档落定。 |
| 同文件 | 3956、4170、4194 | 阶段驱动、新地图构造、原始预算 `2500 + random(500)`；另加科技阶段预算。 |
| `asplit-A/com/zarkonnen/airships/MapSize.java` | 12–22 | 地图尺寸、势力数、每势力城镇数是 Loadable 的 final 字段；默认城镇数 2。 |
| `asplit-A/com/zarkonnen/airships/CitySetup.java` | 32–44 | 城市/城镇数量影响资产分配预算，不能跳过配置流程只设置余额。 |
| `asplit-B/com/zarkonnen/airships/StrategicLobbyScreen.java` | 832、933 | 联机参数发送和新战役构造；仅加入单人开局 UI 不等于实现房主设置广播。 |

主线：`GameSetupScreen.startGame → CampaignWorld/WorldMap 构造 → WorldGenScreen → WorldMap.doSetup 多阶段 → setupPlayer → 首次自动存档 → StrategicScreen`。

## 旧 MOD 对照

对应参考文件为 `acbric-starting-cities.jar`，MOD ID `acbric_starting_cities`，版本 0.3.1，内置默认 2 城市/3 城镇。SHA256：`4916572246d4781841713ef7601b4e8c08dadf6724ab72de8bd7be151a023165`。另一份 City Upgrade MOD 实现已有城市内的建筑升级链，不属于此开局功能。旧二进制与反编译材料保留在仓库外，本项目实现独立维护。

- `MapSizeMixin` 与开局按钮钩子把全局 `townsPerEmpire` 向上调整到至少 `towns + cities - 1`；不向下降，影响 AI，可能污染下一局。
- `StrategicScreenMixin` 在战略界面构造后把人类城镇转为城市，跳过祭坛，收入至少 30 或翻倍。此时初始建筑/舰船及首次自动存档已经完成；再次进入战略界面可能再次补转换。
- 旧 UI 固定屏幕坐标、加减按钮，无上限，双语文字并列。联机钩子为可选方法名，配置使用本地静态值，没有共享规则广播/一致性保证。
- 旧城镇数并非精确结果：全局容量只增不减，祭坛跳过及原生选址失败也会导致实际数量不符。旧 MOD 没有现金设置。

## 实际接入与边界

1. `conquest/` 保存玩法/配置，`mixin/` 保存适配，框架本体未改。
2. 单人 GameSetupScreen 设置列表底部增加入口及滚动高度；框架设置页编辑三个字段。没有另加大厅界面，MOD 详情也可编辑。
3. Acbric 生成前冻结规则；第一次 doSetup 创建本地图独立 MapSize 容量副本，保留地图名称/大小/势力数。所有使用 townsPerEmpire 的原生距离、巢穴 ID 和生成阶段自然使用同一容量。全局 Loadable 不被修改。
4. WorldMap$3 原生城镇阶段按槽位跳过多余 AI/玩家位置，在原生收入调用处把前几个玩家槽位设置为城市，使用原生城市收入，早于土地/建筑生成。PR #1 改为在原生放置返回时给实际新建的定居点连续编号，避免空 ID 导致原生领土描边提前结束；默认无跳过槽位时保持原生 ID。仅影响新地图，不重排已有存档。
5. WorldMap$4 将祭坛数限制为可用城镇数。空间不足时提前失败；CREATED 再核对人类领地数，然后设置现金，早于首次自动存档。不挂战略界面构造或加载/恢复事件。
6. 配置/UI/共享规则复用公开 API。为在 WorldMap 反序列化早期恢复原生容量，额外保存 `arcStartingLayout={version:1,slots:N}` 技术字段；它不是玩法配置，不读本机候选值。原生 OutPipe/JSON 重建及磁盘保存均有定向检查。
7. 第一版只面向新战役；旧档缺少 ARC 规则由框架拒绝，不自动接纳。最初联机要求手动对齐本地设置；Acbric 0.3.6 起由房主广播快照，客户端校验并采用。完整生成与续局待验收。

未新增框架通用接口。旧 Starting Cities 已在 metadata 中声明冲突；其他世界生成 MOD 不保证兼容。详细操作和验收见 README / TESTING 双语文档。

## AI 舰队（ConstructionStrategy）

2026-09-27。游戏里说的「AI 舰队」就是 `ConstructionStrategy`——AI Fleet Creator 工具产出的那个类。它按等级保存 `shipNames`、`landshipNames`、`buildingNames` 与升级序列，还带 `techs`、`charges`/`requiredCharge`、`minDifficultyLevel`/`maxDifficultyLevel` 和 `enabled` 开关。`Loadable.all(ConstructionStrategy.class)` 就是当前实际加载的目录；舰队包自带一个 `ConstructionStrategy/` 数据目录，所以这份列表会随启用的 MOD 变化。

原生在哪里分配：

| 位置 | 说明 |
|---|---|
| `WorldMap$2.run(int, WorldMap)` | 每次调用创建一个势力的 `SetupStage`。它构造 `ArrayList<ConstructionStrategy> strategies = ConstructionStrategy.forCharge(arms.getActiveCharge(), map.difficulty)`，再把 `strategies.get(map.r.nextInt(strategies.size()))` 交给 `Empire` 构造器。 |
| `ConstructionStrategy.forCharge(Charge, DifficultyLevel)` | 对目录做三趟过滤，每趟都要求 `enabled` 与难度区间；第一趟还要求军徽图案匹配（`charges.contains(charge)`，或 `requiredCharge == charge`），第二趟接受 `requiredCharge` 为空的舰队；结果为空时原生 `nextInt(0)` 会直接抛异常。 |
| `Empire.constructionStrategy` | `public final`，在构造器里赋值——势力一旦创建就无法再换舰队。 |
| `AIConstructionUtils`、`CityAI`、`StrategicAI` | 运行期读这个字段决定 AI 造什么、研究什么。 |

`run` 里一共有四个 `ArrayList.get`：城市名（偏移 312）、舰队列表（420）、科技选项（786）、英雄（1340）。它们是逐字节相同的 `INVOKEVIRTUAL java/util/ArrayList.get:(I)Ljava/lang/Object;`，所以 `@Redirect` 必须靠 `ordinal` 定位；ARC 用 `ordinal = 1`，并在处理函数里做类型守卫，将来序号漂移只会退回原版行为，而不会把别的列表当成舰队。仓库内的探针会解析发行版类文件，同时断言数量（四个）和位置（唯一被 `checkcast ConstructionStrategy` 跟随的是第 1 个）。

舰队设置与开局设置分开存放，但一起随战役固化：Acbric 的共享规则对每个 MOD ID 只允许注册一次，因此 ARC 把两者合并成同一份 JSON 载荷。规则版本保持 1，缺少 `fleets` 键的载荷按「全部允许」读取，此前保存的战役仍可加载。

## 已完成的验证

JDK 21 / Gradle 8.13 离线构建成功。dev.1/dev.2 历史证据由维护者私有保存，dev.3 原始基线为两版各 71 项。PR #1 合并后的连续 ID/描边回归在 API dev.25、两版游戏上各通过 407 项；用户确认缺色修复实机正常。复现入口、测试替身、合成归属网格及完整地形/道路/初始资产/GPU/联机覆盖边界见[测试手册](TESTING.zh-CN.md)，结果写入 `build/runtime-tests/<tag>/summary.json`。
