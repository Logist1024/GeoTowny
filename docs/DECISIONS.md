# GeoTowny 迁移决策记录（MIG-002）

| 项 | 内容 |
|---|---|
| 文档版本 | v1.1 |
| 状态 | 已决策（D-01 ~ D-05，2026-11-08） |
| 决策人 | 项目所有者 |
| 关联文档 | `MIGRATION_PLAN.md`、`FEATURE_MATRIX.md` |
| 规则 | 每项决策关闭后，在本文末尾追加"决策结论"并注明日期 |

---

## 决策总览

| ID | 决策 | 影响模块 | 阻塞阶段 | 结论 |
|---|---|---|---|---|
| D-01 | 一人是否可加入多个城镇/国家 | 数据模型、税收、命令、Web | P2/P3 | 单城镇 |
| D-02 | "一天"的定义 | 税收、破产、任期、市场、签到 | P3/P6 | 统一现实日 |
| D-03 | 战争与侵略是否合并 | WarService、命令、BossBar | P6 | 合并为 Campaign |
| D-04 | 城镇解散/被占领后的资产归属 | 经济、占领、解散 | P3/P6 | 解散退市长，占领归占领国 |
| D-05 | 意识形态机制范围 | IdeologyManager、命令 | P6 | 保留 4 个核心 |

---

## D-01 一人是否可加入多个城镇/国家

### 现状证据

- `Resident` 同时持有 `townUuid`（活动城镇）、`townUuids`（全部城镇）、`townRanks`（按城镇职位），见 `data/Resident.java:20-23`。
- 一个居民可被多个城镇同时征税：`EconomyManager.processTownDailyTaxes` 遍历 `town.getResidentUuids()`，见 `economy/EconomyManager.java:105`。
- 一人只能建一个国家：`NationCommand.newNation` 检查 `r.isKing()`，见 `command/NationCommand.java:235`。
- `/town select` 与 Web `/api/player/switch-town` 都支持切换活动城镇。

### 问题

多城镇导致：税收重复、职位语义混乱（同一人在 A 镇是市长、B 镇是居民）、国家归属只能按活动城镇推断、命令上下文不稳定、Web 面板需要"切换城镇"。

### 选项

| 选项 | 描述 | 优点 | 缺点 |
|---|---|---|---|
| A 保留多城镇，明确规则 | 保留 `townUuids`；税收只按活动城镇；职位仅活动城镇生效 | 不破坏现有玩家数据与玩法 | 规则仍复杂，跨镇行为需大量边界处理 |
| B 单城镇（推荐） | 一个居民只属于一个城镇；保留字段用于迁移映射 | 模型最简单，税收/职位/国家归属唯一 | 需要数据迁移：多城镇玩家必须选择保留哪一个 |
| C 保留多城镇且全部征税 | 现状 | 无需改动 | 玩家被重复征税，数值不可控 |

### 推荐

**B：单城镇。** 理由：

1. 迁移期是唯一能低成本收敛模型的窗口。
2. 多城镇与"国家/城镇/居民"三层结构冲突，是当前大量一致性补偿代码的根源之一。
3. Web 面板的"切换城镇"与命令上下文可一并删除，减少迁移面。

### 迁移影响

- `Resident.townUuids` 保留为兼容字段，加载时折叠为 `townUuid`。
- 迁移脚本输出"多城镇玩家清单"，由服主决定保留规则（默认保留活动城镇）。
- `/town select`、`/api/player/switch-town` 删除或改为只读展示。
- 税收、职位、Tab 前缀逻辑简化。

---

## D-02 "一天"的定义

### 现状证据

| 逻辑 | 位置 | 口径 |
|---|---|---|
| 每日税收/影响力/市场重置/日志清理 | `task/Scheduler.java:17`、`Scheduler.java:56` | 24,000 tick（约 20 分钟） |
| 破产宽限期 | `economy/EconomyManager.java:21` | 现实毫秒天 |
| 城镇废弃 | `Scheduler.java:257` | 现实毫秒天 |
| 民主制任期 | `Nation.setTermDuration` | 现实毫秒 |
| 流亡期 | `war/WarManager.java:34` | 硬编码 7 天 |
| 签到断签 | `checkin/CheckInManager` | 与配置 `checkInStreakTimeoutHours` 不一致 |
| 在线奖励 | `task/Scheduler.java:181` | 现实分钟 |
| 节日 | `FestivalManager.isFestival` | 现实日期 |

### 问题

同一"天"有至少四套口径。按 24,000 tick 结算时，默认税率 100/城镇/天意味着每 20 分钟征收一次，实际一天征收 72 次，经济必然失控。

### 选项

| 选项 | 描述 | 优点 | 缺点 |
|---|---|---|---|
| A 统一现实日（推荐） | 所有"每日"任务都按服务器本地日期/24h | 可预期、易运营、离线也计时 | 与游戏内昼夜无关 |
| B 统一游戏日 | 所有任务按 24,000 tick | 与游戏节奏一致 | 20 分钟一次税收，数值需整体重做 |
| C 分模块显式化 | 经济/市场用游戏日，破产/任期/废弃用现实日，但集中在 `GameClock` 并写文档 | 兼顾玩法与运营 | 仍是两套口径，需玩家教育 |

### 推荐

**A：统一现实日。** 理由：

1. 当前数值（税收、维护费、破产天数）都是按"现实天"设计的，改口径会破坏平衡。
2. 服务器通常 24/7 运行，现实日更稳定。
3. 实现最简单：`GameClock.REAL`，所有 `SettlementTask` 记录 `lastRunDay` 做幂等。

> 若希望保留"游戏日"节奏，选 C，但必须在配置与帮助文档中显式写明每个模块的口径。

---

## D-03 战争与侵略是否合并

### 现状证据

三套机制同时修改 `Town.nationUuid` 与首都：

| 机制 | 入口 | 占领逻辑 | 位置 |
|---|---|---|---|
| 攻城 | `/nation war` + 击杀/破坏累点 | `WarManager.occupyTown` | `war/WarManager.java:190` |
| 侵略 | `/nation invade` | `InvasionManager.occupyTown` | `war/InvasionManager.java:559` |
| 割地/停战 | `/nation` 投降/议和 | `WarManager.surrender/cedeTown` | `war/WarManager.java:238`、`:90` |

状态字段也分散：`Nation.warState`、`Nation.siegePoints`、`Nation.siegeTargetNationUuid`、`Nation.siegeDeadline`、`InvasionData`。

### 选项

| 选项 | 描述 | 优点 | 缺点 |
|---|---|---|---|
| A 合并为 Campaign（推荐） | `WarService` 统一管理，攻城/侵略只是 `CampaignType` | 单一入口、状态可推理、避免重复占领 | 需要重写两个 Manager 的公共部分 |
| B 双轨但统一入口 | 保留两套实现，所有 `Town` 归属变更只能走 `WarService.transferTown` | 改动小 | 规则仍然重叠，玩家难以理解 |
| C 删除侵略 | 只保留攻城 | 最简单 | 丢失现有玩法 |

### 推荐

**A：合并为 `WarService` + `Campaign`。** 状态机与转换守卫见 `MIGRATION_PLAN.md` 4.4 节。

关键约束：

1. 任何 `Town.nationUuid`、`Town.isCapital` 的写入只能由 `WarService` 执行。
2. 攻城与侵略共享"占领判定、首都变更、国王变更、赔偿、流亡"五个子流程。
3. `Nation` 上的 `siegePoints/siegeTargetNationUuid/siegeDeadline` 迁移到 `Campaign` 实体，不再挂在 `Nation` 上。

---

## D-04 城镇解散/被占领后的资产归属

### 现状证据

- 解散：国库余额退还市长（`EconomyManager.java:279-293`），领地释放，居民移除。
- 被占领：`InvasionManager.occupyTown` 直接转移城镇归属，未见国库处理。
- 国家解散：首都丢失且无其他城镇时自动解散，国王 rank 被清理（`EconomyManager.java:343-359`）。
- 帝国吞并：城镇转行省，但被吞并国国王 rank 未清理（`IdeologyManager.java:96-139`）。

### 选项

| 选项 | 解散时 | 被占领时 | 评价 |
|---|---|---|---|
| A（推荐） | 国库退还市长；居民个人余额不动 | 城镇国库归占领国；居民个人余额不动；原市长降为居民 | 与现有行为最接近，规则可解释 |
| B | 国库收归国家 | 城镇国库归占领国 | 惩罚更重，鼓励参战 |
| C | 国库销毁 | 国库销毁 | 简单但容易造成通缩，玩家挫败感强 |

### 推荐

**A。** 同时补齐两个缺陷：

1. 被占领时明确处理城镇国库与市长 rank。
2. 帝国吞并后清理被吞并国国王的 `nationRank`，避免脏数据。

### 迁移影响

- `EconomyService.dissolveTown` 与 `WarService.occupyTown` 都必须在事务内完成资金、归属、rank 三类变更。
- 需要一组单测覆盖"解散时有国库/无国库/有国家/无国家"、"占领时有国库/有首都"等组合。

---

## D-05 意识形态机制范围

### 现状证据

8 种国体、20+ 开关。调用点统计：

| 开关 | 调用点 | 状态 |
|---|---|---|
| 世袭传承 `isHereditary` | 0 | 未接线 |
| 宣战公投 `isWarRequiresVote` | 2 | 已实现 |
| 弹劾 `isImpeachment` | 1 | 已实现 |
| 任期 `isTermLimit` | 5 | 已实现 |
| 城镇自治 `isTownAutonomy` | 0（管理方法无调用） | 未接线 |
| 自由退出 `isFreeLeave` | 1 | 已实现 |
| 行省制 `isProvinceSystem` | 6 | 已实现 |
| 公共仓库 `isPublicWarehouse` | 3 | 半实现 |
| 集体劳动 `isCollectiveLabor` | 0 | 未接线 |
| 迁徙 `isMigration` | 1 | 半实现 |
| 血盟 `isBloodOath` | 0 | 未接线 |
| 神谕 `isOracleQuest` | 1 | 半实现 |
| 圣战 `isHolyWar` | 1 | 已实现 |
| 异端惩罚 `isHeresyPenalty` | 1 | 已实现 |
| 神职 `isClergySystem` | 2 | 已实现 |
| 议会否决 `isParliamentVeto` | 1 | 已实现 |
| 经济刺激金 `isEconomicStimulus` | 1 | 已实现 |

### 选项

| 选项 | 描述 | 优点 | 缺点 |
|---|---|---|---|
| A 全实现 | 补齐世袭、集体劳动、血盟、自治、神谕进度、仓库扣物 | 玩法最完整 | 工作量大，且部分机制缺乏设计 |
| B 保留 4 个核心（推荐） | 民主制（公投/弹劾/任期）、帝国制（吞并/行省）、联邦制（自由退出/自治）、神权制（神职减免/圣战） | 机制清晰、可测 | 删除部分国体特色 |
| C 降级为标签 | 国体只影响称呼/颜色，删除所有专属机制 | 最省事 | 丢失核心卖点 |

### 推荐

**B：保留 4 个核心国体，其余降级为"称呼 + 数值倍率"。**

具体建议：

| 国体 | 保留机制 | 删除/降级 |
|---|---|---|
| 民主制 | 宣战公投、弹劾、任期 | — |
| 君主制 | 称呼、费用倍率 | 骑士册封（无效果）删除 |
| 共和制 | 经济刺激金、议会否决 | — |
| 联邦制 | 自由退出、城镇自治（需接线） | — |
| 帝国制 | 吞并、行省税收、行省叛乱 | — |
| 集体制 | 称呼、科技上限 | 公共仓库（需重做）删除或补全 |
| 部落制 | 费用减免、迁徙（补全） | 血盟（无实现）删除 |
| 神权制 | 神职税收减免、圣战、异端惩罚 | 神谕任务（补全或删除） |

### 迁移影响

- `Ideology` 枚举字段从 20+ 收敛为"称呼 + 4~6 个机制开关 + 倍率"。
- 删除的机制同步删除命令：`/nation knight`、`/nation warehouse`（若删除）、`/nation oracle`（若删除）、`/town migrate`（若删除）。
- 每个保留机制必须有单测与行为快照。

---

## 决策结论

### D-01 结论（2026-11-08）

- 选择：**B 单城镇**。
- 理由：多城镇重复征税、职位语义与活动城镇上下文是当前一致性补偿代码的主要来源；迁移期是唯一低成本的收敛窗口。
- 追加约束：
  - `Resident.townUuids` 仅作为迁移兼容字段保留，加载后折叠为 `townUuid`，运行期不再写入。
  - `/town select`、`/api/player/switch-town` 删除。
  - 税收只按居民唯一城镇征收；职位只按唯一城镇解释。
  - 一人一国限制保持不变。
- 影响任务：MIG-204（多城镇折叠迁移）、MIG-301、MIG-304、MIG-310、MIG-703。

### D-02 结论（2026-11-08）

- 选择：**A 统一现实日**。
- 理由：现有税收、维护费、破产天数数值均按现实天设计；统一游戏日会使税率放大 72 倍。
- 追加约束：
  - `GameClock` 默认实现为 `RealDayClock`，所有"每日"任务按服务器本地日期切日。
  - 每个 `SettlementTask` 持久化 `lastRunDay`，保证幂等。
  - `MinecraftDayClock` 仅保留为实验开关，默认关闭，不出现在正式文档。
  - 签到、在线奖励仍按现实时间，但统一走 `GameClock.realtime()`，不再各自使用 `System.currentTimeMillis()` 魔法常量。
- 影响任务：MIG-104、MIG-204、MIG-304、MIG-407、MIG-606。

### D-03 结论（2026-11-08）

- 选择：**A 合并为 Campaign**。
- 理由：三套机制同时修改城镇归属，规则重叠且无法推理；合并后状态机可测试。
- 追加约束：
  - `Town.nationUuid`、`Town.isCapital` 的写入只能由 `WarService` 执行。
  - 攻城与侵略作为 `CampaignType`，共享占领、首都变更、国王变更、赔偿、流亡五个子流程。
  - `Nation` 上的 `siegePoints`、`siegeTargetNationUuid`、`siegeDeadline` 迁移到 `Campaign`，从 `Nation` 删除。
  - 删除 `/nation invade` 与 `/nation war` 的双入口差异，统一为 `/nation war <target>` 后由战役类型决定行为（保留别名兼容一个版本）。
- 影响任务：MIG-601、MIG-602、MIG-603、MIG-604。

### D-04 结论（2026-11-08）

- 选择：**A 解散退市长，占领归占领国**。
- 理由：与现有解散逻辑一致，规则可向玩家解释；居民个人余额不受政治变更影响。
- 追加约束：
  - 解散：城镇国库退还市长；若市长不存在，国库收归所属国家；无国家则销毁并记录日志。
  - 占领：城镇国库归占领国；原市长 `townRank` 降为 `RESIDENT` 或按占领政策移除。
  - 帝国吞并：必须清理被吞并国国王的 `nationRank`，不得留下 KING。
  - 所有资金变更写入 `ledger`（若启用）。
- 影响任务：MIG-304、MIG-602、MIG-604。

### D-05 结论（2026-11-08）

- 选择：**B 保留 4 个核心国体**（民主制、帝国制、联邦制、神权制），其余降级为"称呼 + 数值倍率"。
- 理由：20+ 开关中 6 个无调用、5 个半实现，保留全部会显著放大迁移与测试成本。
- 追加约束：

| 国体 | 保留 | 删除 / 降级 |
|---|---|---|
| 民主制 | 宣战公投、弹劾、任期限制 | — |
| 帝国制 | 吞并、行省税收、行省叛乱、稳定度 | — |
| 联邦制 | 自由退出、城镇自治（按"国家不强制覆盖城镇设置"落地，删除未接线的 `isTownAutonomous`） | — |
| 神权制 | 神职税收减免、圣战加成、异端惩罚 | 神谕任务（进度未接线，删除） |
| 君主制 | 称呼、费用倍率 | 骑士册封（无效果，删除 `/nation knight`） |
| 共和制 | 称呼、费用倍率 | 经济刺激金（删除 `/nation stimulus`）、议会否决（删除） |
| 集体制 | 称呼、科技上限数值 | 公共仓库（删除 `/nation warehouse`） |
| 部落制 | 称呼、费用减免 | 迁徙（删除 `/town migrate`）、血盟（无实现，删除） |

- 同步删除死配置：`ideologyEnabled`、`techEnabled`、`isHereditary`、`isCollectiveLabor`、`isBloodOath`、`isTownAutonomous`、`updateOracleProgress`。
- 影响任务：MIG-301、MIG-306、MIG-307、MIG-605。

---

## 决策对功能矩阵的影响

| 功能/命令 | 决策前状态 | 决策后目标 |
|---|---|---|
| 一人多城镇 | 已实现 | 删除，改为单城镇 |
| `/town select` | 已实现 | 删除 |
| `/api/player/switch-town` | 已实现 | 删除 |
| `/town migrate` | 半实现 | 删除 |
| `/nation knight` | 半实现 | 删除 |
| `/nation stimulus` | 已实现 | 删除 |
| `/nation warehouse` | 半实现 | 删除 |
| `/nation oracle` | 半实现 | 删除 |
| 议会否决 | 已实现 | 删除 |
| 附庸 | 半实现 | 保留关系，不实现附庸义务（在功能矩阵标注） |
| 侵略 | 已实现 | 合并进 Campaign，命令保留 |
| 攻城 | 已实现 | 合并进 Campaign，命令保留 |
| 节日经验倍率 | 仅展示 | 删除配置或接线（默认删除） |

---

## 决策记录格式

决策关闭后，在下方追加：

```
### D-0X 结论（YYYY-MM-DD）

- 选择：X
- 理由：
- 追加约束：
- 影响任务：MIG-xxx
```