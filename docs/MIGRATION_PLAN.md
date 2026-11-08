# GeoTowny 迁移设计文档：Fabric 1.21.8 → Spigot 26.3

| 项 | 内容 |
|---|---|
| 文档版本 | v1.0 |
| 编写日期 | 2026-11-08 |
| 源项目 | GeoTowny v2.3.2（Fabric 1.21.8，Java 21） |
| 目标平台 | Spigot 26.3（最低兼容 26.1），Java 25 |
| 迁移范围 | 全量功能迁移 + 玩法逻辑重构 |
| 文档状态 | 已决策（D-01~D-05 于 2026-11-08 关闭） |

---

## 1. 目标与非目标

### 1.1 目标

1. 将 GeoTowny 从 Fabric 服务端模组迁移为 Spigot 插件，支持 Minecraft 26.1+（主目标 26.3）。
2. 保持现有玩法能力不丢失：城镇、国家、领地、外交、战争、侵略、意识形态、市场、签到、兑换码、实体货币、Web 面板。
3. 在迁移过程中消除已有的结构性混乱：双向引用、多套时间轴、非事务领域操作、胖类、金额精度。
4. 建立可回归的测试基线，结束"零测试"状态。

### 1.2 非目标

- 不迁移模组专属商品与模组联动（FarmersDelight / RusticDelight / Enderscape / Fishing101）。
- 不使用 NMS / CraftBukkit 内部类。
- 不新增玩法系统；玩法调整仅限"合并重复机制、明确规则"。
- 不保证 26.1.0/26.1.1 的运行时兼容，仅保证 API 层面可编译（无 26.2+ 专属 API）。

---

## 2. 现状评估

### 2.1 规模与耦合

| 指标 | 数值 |
|---|---|
| Java 文件 | 76 |
| 总行数 | 24,543 |
| 引用 `net.minecraft` 的文件 | 39 |
| 引用 `net.fabricmc` 的文件 | 18 |
| 引用 `ScreenHandler` 的文件 | 11 |
| 无任何平台 import 的纯逻辑文件 | 29 |
| 测试文件 | 0 |
| 自定义网络包 | 0 |

### 2.2 模块画像

| 模块 | 文件数 | 行数 | 平台耦合 | 迁移策略 |
|---|---|---|---|---|
| `web` | 3 | 5,189 | 中 | 保留 HTTP，抽离业务；HTML 外置 |
| `command` | 9 | 4,425 | 高 | 重写命令层 |
| `market` | 12 | 3,016 | 高 | GUI 重写，定价逻辑复用 |
| `war` | 4 | 1,744 | 高 | 状态机重构后迁移 |
| `data` | 13 | 1,251 | 无 | 原样复用 |
| `protection` | 4 | 1,161 | 高 | 拆分监听器后重写 |
| `geopolitics` | 4 | 1,020 | 低 | 逻辑复用，接口适配 |
| `redeem` | 4 | 999 | 中高 | GUI 重写 |
| `storage` | 2 | 720 | 低 | 路径与序列化适配 |
| `api` | 1 | 701 | 高 | 改为 Bukkit 服务/事件 |
| `claim` | 3 | 675 | 中 | 逻辑复用，粒子重写 |
| `checkin` | 4 | 557 | 中高 | GUI 重写 |
| `economy` | 1 | 514 | 低 | 逻辑复用 + 金额改造 |
| `currency` | 2 | 455 | 高 | NBT → ItemMeta |
| `task` | 2 | 436 | 中 | Scheduler → BukkitRunnable |
| `placeholder` | 1 | 334 | 高 | 改用 PlaceholderAPI |
| `config` | 1 | 294 | 低 | 重构为分组 + 版本化 |
| `log` | 1 | 230 | 低 | 复用 |
| `util` | 2 | 156 | 无 | 原样复用 |
| `diplomacy` | 1 | 72 | 无 | 原样复用 |

### 2.3 可直接复用的文件（29 个）

```
com/geotowny/data/Alliance.java
com/geotowny/data/Ideology.java
com/geotowny/data/Nation.java
com/geotowny/data/NationRank.java
com/geotowny/data/PlotType.java
com/geotowny/data/RelationType.java
com/geotowny/data/Resident.java
com/geotowny/data/Town.java
com/geotowny/data/TownBlock.java
com/geotowny/data/TownLog.java
com/geotowny/data/TownRank.java
com/geotowny/data/WarState.java
com/geotowny/data/WorldLocation.java
com/geotowny/claim/ClaimManager.java
com/geotowny/diplomacy/DiplomacyManager.java
com/geotowny/geopolitics/FestivalManager.java
com/geotowny/geopolitics/IdeologyManager.java
com/geotowny/geopolitics/InfluenceManager.java
com/geotowny/market/Listing.java
com/geotowny/market/MarketItem.java
com/geotowny/market/ShopItems.java（需裁剪模组商品）
com/geotowny/task/KillDropManager.java
com/geotowny/war/InvasionData.java
com/geotowny/war/WarManager.java
com/geotowny/checkin/CheckInData.java
com/geotowny/util/PinyinUtil.java
com/geotowny/util/Result.java
com/geotowny/web/ChatBuffer.java
com/geotowny/web/WebPageContent.java（建议外置为资源）
```

### 2.4 必须重写的文件

| 文件 | 原因 |
|---|---|
| `GeoTownyMod.java` | 入口改为 `JavaPlugin` |
| `command/CommandRegistry.java` | Brigadier 回调改为命令注册 |
| `command/*Command.java`（8 个） | 命令框架重写 |
| `protection/EventListener.java` | Fabric 事件 → Bukkit 事件，且需拆分 |
| `protection/TabListManager.java` | Tab/聊天 API 替换 |
| `claim/VisualManager.java`、`BorderVisualManager.java` | 粒子 API 替换 |
| `market/*ScreenHandler.java`（2 个） | 容器 GUI → InventoryHolder |
| `checkin/CheckInScreenHandler.java` | 同上 |
| `redeem/RedeemAdminScreenHandler.java` | 同上 |
| `command/TownMemberScreenHandler.java` | 同上 |
| `placeholder/GeoPlaceholders.java` | PlaceholderAPI 替换 |
| `currency/PhysicalCurrencyManager.java` | NBT → ItemMeta/PDC |
| `api/GeoTownyAPI.java` | `World`/`BlockPos` → Bukkit `World`/`Location` |
| `storage/JsonStorage.java`、`DatabaseStorage.java`、`config/GeoConfig.java` | `FabricLoader` 路径替换 |
| `GeoTowny.java` | 生命周期与调度器适配 |
| `task/Scheduler.java` | `ServerTickEvents` → `BukkitRunnable` |
| `web/WebServer.java` | 主线程调度与 MC 物品序列化 |

---

## 3. 技术决策

### 3.1 版本与构建

| 项 | 决策 |
|---|---|
| 编译目标 | `org.spigotmc:spigot-api:26.3-R0.1-SNAPSHOT` |
| 最低兼容 | 26.1（`plugin.yml` 的 `api-version` 按 26.1 填写，需实测 Spigot 26.x 的取值格式） |
| Java | 25（26.x 全系最低要求） |
| 构建 | Gradle + Shadow |
| 主类 | `com.geotowny.GeoTownyPlugin extends JavaPlugin` |
| 数据目录 | `plugins/GeoTowny/` |

> 版本核对来源：PaperMC Fill v3 API（26.1.2 于 2026-07-26 EOL，26.3 为当前 SUPPORTED）；Spigot Nexus `spigot-api` 已发布 26.1 ~ 26.3 的 `R0.1-SNAPSHOT`。

### 3.2 依赖策略

| 依赖 | 作用域 | 说明 |
|---|---|---|
| `spigot-api` | `compileOnly` | 平台 API |
| `gson` | `implementation` + shade | Spigot 不保证向插件暴露 Gson |
| `sqlite-jdbc` | `implementation` + shade | 沿用现有 SQLite 存储 |
| `PlaceholderAPI` | `compileOnly`，`softdepend` | 占位符可选集成 |
| `CommandAPI` | `compileOnly` + shade | 命令层，保留 Brigadier 风格参数与补全 |
| `JUnit 5` / `AssertJ` / `MockBukkit` | `testImplementation` | 领域与轻量集成测试 |

> CommandAPI 与 ACF 二选一。原代码基于 Brigadier 的 `literal/argument`，CommandAPI 的参数类型与补全模型更接近，迁移成本更低，故推荐 CommandAPI。

### 3.3 关键 API 映射

| Fabric 1.21.8 | Spigot 26.x |
|---|---|
| `DedicatedServerModInitializer` | `JavaPlugin#onEnable` |
| `ServerLifecycleEvents.SERVER_STARTING/STOPPING` | `onEnable/onDisable` |
| `ServerTickEvents.END_SERVER_TICK` | `BukkitRunnable#runTaskTimer(plugin, 0, 1)` |
| `CommandRegistrationCallback` + Brigadier | `CommandAPI` 或 `PluginCommand` + `TabCompleter` |
| `PlayerBlockBreakEvents.BEFORE` | `BlockBreakEvent` |
| `UseBlockCallback` | `PlayerInteractEvent`（区分 `LEFT_CLICK`/`RIGHT_CLICK` 与 hand） |
| `AttackEntityCallback` | `EntityDamageByEntityEvent` |
| `ServerLivingEntityEvents.AFTER_DEATH` | `EntityDeathEvent` |
| `ServerPlayerEvents.AFTER_RESPAWN` | `PlayerRespawnEvent` |
| `ServerPlayConnectionEvents.JOIN/DISCONNECT` | `PlayerJoinEvent` / `PlayerQuitEvent` |
| `ServerEntityEvents.ENTITY_LOAD` | `CreatureSpawnEvent` / `EntitiesLoadEvent`（按需） |
| `ServerMessageEvents.ALLOW_CHAT_MESSAGE` | `AsyncPlayerChatEvent`（Spigot） |
| `GenericContainerScreenHandler` | `Bukkit.createInventory(InventoryHolder, size, title)` |
| `ScreenHandler#onSlotClick` | `InventoryClickEvent`（判断 `getClickedInventory`） |
| `ServerBossBar` | `Bukkit.createBossBar` |
| `DustParticleEffect` | `Particle.DUST` + `Particle.DustOptions` |
| `Text` / `MutableText` / `Formatting` | Spigot 文本 API（优先 `ChatColor` 或平台提供的 Adventure，需实测 26.3） |
| `Player#sendMessage(Text, boolean)` | `Player#sendMessage(String)` |
| `FabricLoader#getConfigDir` | `JavaPlugin#getDataFolder` |
| `eu.pb4:placeholder-api` | `me.clip.placeholderapi` |
| `NbtCompound` / `DataComponentTypes` | `ItemMeta` / `PersistentDataContainer` |
| `ServerPlayerEntity` | `Player` |
| `MinecraftServer#getPlayerManager` | `Bukkit.getOnlinePlayers()` |
| `ServerWorld` / `RegistryKey<World>` | `World`（按名字查找） |

---

## 4. 玩法重构设计

> 本章是"改掉混乱逻辑"的核心。所有重构都在迁移过程中同步落地，不另开一次大重写。

### 4.1 聚合边界与单一真相源

定义三个聚合，跨聚合只允许存 ID：

| 聚合根 | 内部实体 | 对外引用 |
|---|---|---|
| `Town` | `TownBlock`（领地） | `nationId`、`mayorId`、`residentIds` 仅作索引 |
| `Nation` | 外交关系、联盟引用 | `townIds`、`kingId`、`capitalTownId` |
| `Resident` | 唯一城镇的职位、国家职位 | `townId`、`nationRank` |

规则：

1. 双向集合**只在一侧持久化**，另一侧为内存索引，启动时重建。
2. 所有写操作必须经过领域服务，服务内部同时维护索引。
3. 新增 `ConsistencyChecker`：启动时与 `/geo admin verify` 时检查
   - 孤儿 `TownBlock`（town 不存在）
   - `Town.claimBlockKeys` 与 `TownBlock.townUuid` 不一致
   - `mayorUuid` 无对应 `Resident` 或 `townRank`
   - `kingUuid` 与 `nationRank` 不一致
   - `capitalTownUuid` 不存在或不属于该国
   - `Resident.townUuids` 与 `Town.residentUuids` 不一致
4. 删除 `TownCommand.executeTownMerge` 中的手动快照回滚，由服务层事务替代。

### 4.2 统一时间轴

引入 `GameClock`：

```java
public interface GameClock {
    long nowMillis();
    long dayIndex();          // 用于"每日"任务
    long periodMillis(Duration d);
}
```

- 两种实现：`RealDayClock`（默认）、`MinecraftDayClock`（实验开关，默认关闭）。
- 配置项 `time.dayDefinition: REAL | MINECRAFT`，默认 `REAL`；正式部署只允许 `REAL`。
- 所有周期任务实现 `SettlementTask`：`lastRunDay` 持久化，`runIfDue()` 幂等，重启不重复结算。
- 需要保留差异的（如在线奖励必须按现实时间）单独标注为 `REALTIME`，不参与"每日"定义。

迁移映射：

| 现有逻辑 | 位置 | 改造 |
|---|---|---|
| 每日税收/破产/影响力/市场重置 | `Scheduler.dailyTasks` | 统一到 `DailySettlementService`，按 `GameClock` |
| 破产宽限期/废弃/任期/流亡 | 多处 `System.currentTimeMillis()` | 改为 `GameClock.dayIndex()` 差值 |
| 签到超时/在线奖励 | 现实时间 | 保留 `REALTIME`，文档标注 |

### 4.3 金额改造

- 新增值对象 `Money(long cents)`，运算全部为 `long`。
- 现有 `double` 字段迁移：`Math.round(value * 100)` → `long`，只做一次。
- 展示层统一 `Money#format()`，配置 `currencyName`。
- 新增 `ledger` 表（可选但推荐）：`id, ts, type, from_id, to_id, amount_cents, balance_after, ref`，用于审计与问题排查。
- 删除 `Resident/Town/Nation` 三份重复的 `round()`。

### 4.4 战争 / 侵略 / 攻城的合并（已决策：方案 A）

现状是三套机制并存且都修改 `Town.nationUuid` 与首都：

- `WarManager`：`siegePoints` → `occupyTown` → `triggerExile`
- `InvasionManager`：读条 → `occupyTown` → `processCompensation`
- `Nation`：`siegePoints`、`siegeTargetNationUuid`、`siegeDeadline`

推荐方案 A：合并为 `WarService` + `Campaign` 子状态。

状态机：

```
NONE
  └─ declareWar ──> DECLARED
DECLARED
  ├─ startCampaign(SIEGE) ──> SIEGE
  ├─ startCampaign(INVASION) ──> INVASION
  └─ makePeace ──> PEACE
SIEGE / INVASION
  ├─ 达成条件 ──> OCCUPIED
  ├─ 超时/停战 ──> DECLARED / PEACE
  └─ 失败赔偿 ──> DECLARED
OCCUPIED
  ├─ annex ──> ANNEXED
  └─ exile ──> EXILED
```

转换守卫：

| 转换 | 前置条件 | 副作用（单事务） |
|---|---|---|
| `declareWar` | 双方非同盟、非交战、支付宣战费 | 双方关系置 `ENEMY`、记录日志 |
| `startSiege` | `DECLARED`、目标城镇在线/有效 | 创建 `Campaign`，写截止时间 |
| `startInvasion` | `DECLARED`、防守方在线比例达标、扣费 | 创建 `Campaign`，注册 BossBar |
| `occupy` | 点数达标或读条完成 | 转移 `Town` 归属、处理首都/国王、发补偿 |
| `annex` | 占领完成 | 国家吞并、居民 rank 处理 |
| `exile` | 国家无城镇 | 写入流亡字段、启动复国计时 |

过渡方案 B（若暂不合并）：保留两个 Manager，但规定"任何 `Town.nationUuid` 的写入只能由 `WarService.transferTown()` 执行"，两个 Manager 都调用它。

### 4.5 服务层与事务边界

新增：

```
TownService        创建/解散/改名/合并/分裂/转移市长
NationService      建国/解散/加入/退出/首都变更/国王变更
EconomyService     转账/税收/维护费/破产/结算
WarService         宣战/停战/攻城/侵略/占领/流亡/复国
ClaimService       圈地/退地/地块类型/出售
MarketService      动态市场/自由市场/额度
```

要求：

1. 每个公开方法是一个事务边界：校验 → 变更 → 持久化 → 发领域事件。
2. 命令层、Web 层、Scheduler 只能调用服务，不得直接改模型字段。
3. 持久化失败时回滚内存变更（`UnitOfWork` 模式：先记录变更，再统一提交）。

### 4.6 胖类拆分

| 现有 | 拆分后 |
|---|---|
| `EventListener`（618 行） | `ProtectionListener`、`TownLogListener`、`AntiExploitListener`、`WarCombatListener` |
| `TabListManager` | `TabListService`、`ChatFormatService` |
| `WebServer`（2,116 行） | `WebHttpServer`（路由/会话）、`WebApiHandler`（调用服务） |
| `WebPageContent`（2,985 行） | `src/main/resources/web/*.html` + 简单模板 |
| `GeoConfig`（294 行） | `config.yml` + 分组配置类 + `ConfigMigrator` |

### 4.7 配置与权限

配置：

- 改用 `config.yml`（Spigot 原生）+ 分组：`town:`、`nation:`、`economy:`、`war:`、`market:`、`protection:`、`web:`。
- 增加 `config-version`，启动时执行迁移链。
- 删除硬编码 `webUrl`（`GeoConfig.java:253`）。
- 所有数值增加范围校验，非法值回退默认并告警。

权限：

```
geotowny.player
geotowny.town.create / claim / unclaim / invite / kick / set / deposit / withdraw / rank / merge / split
geotowny.nation.create / join / leave / diplomacy / set / rank / vote
geotowny.plot.*
geotowny.shop.use / shop.admin
geotowny.fm.use
geotowny.checkin
geotowny.redeem.use
geotowny.currency.*
geotowny.web.login
geotowny.admin.reload / verify / economy / war
```

- 默认 `geotowny.player` 授予所有玩家；管理节点默认 OP。
- 替换现有 `hasPermissionLevel(2)` 判断。

---

## 5. 分阶段任务

### P0 基线与决策

| ID | 任务 | 产出 | 状态 | 验收标准 |
|---|---|---|---|---|
| MIG-001 | 冻结功能清单 | `docs/FEATURE_MATRIX.md` | 已完成 | 每个命令、每个配置项都有状态 |
| MIG-002 | 玩法决策评审 | `docs/DECISIONS.md` | 已完成（D-01~D-05） | 5 项决策全部有结论 |
| MIG-003 | 领域层测试基线 | `docs/BEHAVIOR_BASELINE.md` 第 3、4 节 | 部分完成（JDK 17 阻塞） | `./gradlew test` 通过，核心服务覆盖率 ≥ 60% |
| MIG-004 | 建立旧版行为快照 | `docs/BEHAVIOR_BASELINE.md` 第 5 节 | 部分完成（缺测试服） | 23 个场景可新旧对照 |

P0 补充说明：

- 功能矩阵核查出 15 个零引用配置项与 6 个无调用方法，已列入 `FEATURE_MATRIX.md` 7.3 节，迁移时不得原样保留。
- MIG-003 的实际执行需要 JDK 21+；MIG-004 的录制需要 Fabric 1.21.8 测试服。

### P1 插件骨架（已完成）

工程位置：`/workspace/geotowny-spigot`（独立工程，与旧仓库分离）。

| ID | 任务 | 涉及文件 | 状态 | 验收结果 |
|---|---|---|---|---|
| MIG-101 | Gradle + Shadow + Java 25 | `build.gradle`、`settings.gradle`、wrapper | 完成 | `./gradlew clean build` 成功；产物 `GeoTowny-3.0.0-SNAPSHOT.jar`（14 MB） |
| MIG-102 | `plugin.yml` + 权限节点 | `plugin.yml` | 完成 | Paper 26.3 build 244 加载成功，`/geotowny` 可用 |
| MIG-103 | 主类与生命周期 | `GeoTownyPlugin` | 完成 | 启停日志正常，`data/` 与 `backup/` 创建 |
| MIG-104 | 日志适配 | `Log` | 完成 | `{}` 占位符单测 4/4 通过 |
| MIG-105 | 配置加载与迁移器 | `PluginConfig`、`ConfigMigrator`、`config.yml` | 完成 | 首次生成 v1 配置；`reload` 生效；旧 JSON 导入归入 P2 |
| MIG-106 | 数据目录与 `worldKey` 映射 | `WorldRegistry` | 完成 | 默认三世界映射；未映射回退；单测 3/3 通过 |

P1 验证记录：

| 项 | 结果 |
|---|---|
| 构建环境 | Temurin JDK 25.0.5 + Gradle 9.5.0 |
| 构建产物 | `build/libs/GeoTowny-3.0.0-SNAPSHOT.jar`，14 MB；Gson 已重定位，Spigot API 未打包 |
| 运行时 | Paper 26.3 build 244，启动 `Done (14.896s)` |
| 命令 | `/geotowny version`、`reload`、`verify` 均正常 |
| 关停 | `onDisable` 日志正常 |
| 单元测试 | 7/7 通过（Log 4 + WorldRegistry 3） |

### P2 数据层（已完成）

| ID | 任务 | 涉及文件 | 状态 | 验收结果 |
|---|---|---|---|---|
| MIG-201 | 搬迁纯逻辑模型 | `domain/model/*`（13 个） | 部分完成 | 模型与枚举已迁；依赖 `GeoTowny` 单例的 Manager 在 P3 迁移 |
| MIG-202 | JSON 存储适配 | `storage/JsonStore`、`MoneyAdapter` | 完成 | 读写 `plugins/GeoTowny/data`；旧 double 金额可读；损坏文件自动备份 |
| MIG-203 | SQLite 适配 + 版本号 | `storage/SqliteStore` | 完成 | `PRAGMA user_version=1`；旧表自动加 `balance_cents` 并回填 |
| MIG-204 | Schema 迁移与折叠 | `StorageMigrator`、`LegacyDataImporter` | 完成 | 金额转分、多城镇折叠、plotType 重映射、首次备份 |
| MIG-205 | 一致性检查器 | `consistency/ConsistencyChecker` | 完成 | 检出并修复孤儿领地、双向引用、市长/国王、首都、负余额 |
| MIG-206 | 金额 `long` 迁移 | `Money`、`Town/Nation/Resident/TownBlock` | 完成 | 内部以分存储；序列化 `{"cents":...}`；旧数据误差 ≤ 0.01 |

P2 验证记录：

| 项 | 结果 |
|---|---|
| 构建 | JDK 25 + Gradle 9.5，`clean build` 成功，产物 14 MB |
| 单元测试 | 37/37 通过 |
| 端到端 | Paper 26.3 + 旧格式数据：城镇 1 / 国家 1 / 居民 3 / 领地 2 |
| 折叠 | u1 保留 t1，丢弃 t2（D-01） |
| 规范化 | plotType 2、居民脱离 2、孤儿领地 1 |
| 一致性 | verify 1 项 → repair 1 项 → 残留 0 |
| 存储 | JSON 金额为 `{"cents":10000}`；SQLite `town_uuids=NULL`、`balance_cents` 正确、`user_version=1` |
| 备份 | `backup/initial-<ts>/` 含 data、residents.db、alliances.json |

### P3 领域服务与命令（进行中）

| ID | 任务 | 涉及文件 | 状态 | 验收结果 |
|---|---|---|---|---|
| MIG-301 | `TownService` | `service/TownService` | 完成 | 建城/解散/改名/成员/国库/市长/设置/合并/分裂，单测通过 |
| MIG-302 | `NationService` | `service/NationService` | 完成 | 建国/解散/成员/首都/国王/国库/国体/外交/宣战入口，单测通过 |
| MIG-303 | `ClaimService` | `service/ClaimService` | 完成 | 圈地/退地/相邻/上限/费用，单测通过 |
| MIG-304 | `EconomyService` | `service/EconomyService`、`task/SettlementScheduler` | 完成 | 税收链/维护费/破产/幂等每日结算，单测通过 |
| MIG-305 | 命令框架接入 | `command/TownCommand` | 完成 | 原生 Bukkit 命令 + 补全；`/town`、`/t` 可用 |
| MIG-306 | 迁移 `/town` | `command/TownCommand` | 部分完成 | 已迁 21 个子命令；`rank`、`force-merge` 待迁（`select/migrate` 按 D-01/D-05 删除） |
| MIG-307 | 迁移 `/nation` | `command/NationCommand` | 部分完成 | 已迁 16 个子命令；战役/侵略/附庸/吞并在 P6 |
| MIG-308 | 迁移 `/plot`、`/resident`、`/geo` | `PlotCommand`、`ResidentCommand`、`GeoCommand` | 完成 | 地块/转账/统计/排行/管理命令已迁 |
| MIG-309 | 权限节点接入 | 全部命令 | 完成 | `/town`、`/nation`、`/plot`、`/resident`、`/geo` 均按节点校验 |
| MIG-310 | 删除命令层直改模型 | 全部命令 | 部分完成 | 主要写操作已下沉服务；`set spawn` 仍直接写模型 |

P3 阶段验证记录：

| 项 | 结果 |
|---|---|
| 单元测试 | 最近通过：NationService 5/5、PlotService 5/5、ResidentService 2/2；此前完整套件 61/61 |
| 运行时 | Paper 26.3 加载旧数据：城镇 1 / 国家 1 / 居民 3 / 领地 1 |
| 命令 | `/town`、`/nation`、`/plot`、`/resident`、`/geo` 全部注册；`/geo info`、`/geo list towns`、`/geo top wealth`、`/resident list` 输出正常 |
| 控制台保护 | 仅玩家命令在控制台正确提示 |
| 每日结算 | 首次启动初始化第 20765 天并写入 `data/settlement.json`，未重复结算 |
| 命令 | `/town list`、`/town info`、`/town logs`、`/town merge`、`/town set border`、`/geotowny verify` 均注册正常 |
| 控制台保护 | `/town new`、`/town logs`、`/town merge`、`/town set border` 正确提示"只有玩家可以执行" |
| 构建 | 14 MB jar，`clean build` 成功 |

### P4 平台交互（完成）

| ID | 任务 | 涉及文件 | 状态 | 说明 |
|---|---|---|---|---|
| MIG-401 | 保护监听 | `listener/ProtectionListener`、`service/ProtectionService` | 完成（已验证） | 破坏/放置/交互/PVP，规则对齐旧版 |
| MIG-402 | 领地日志 | `ProtectionListener` + `TownLogService` | 完成（已验证） | BREAK/PLACE/OPEN_CHEST/PVP |
| MIG-403 | 反刷监听 | `listener/AntiExploitListener`、`service/AntiExploitService` | 完成（已验证） | 禁止凋零生成；清除掉落/背包鞘翅；末地门/重力方块重复刷取告警 |
| MIG-404 | 边界粒子 | `visual/BorderVisualManager` | 完成（简化，已验证） | 每 5 秒外边界，固定 Y=64+height，单镇上限 256 |
| MIG-405 | Tab/聊天 | `tab/TabListManager` | 完成（静态验证） | `[国][城]` 前缀，40 tick 刷新 |
| MIG-406 | PlaceholderAPI | `placeholder/GeoPlaceholders` | 完成（已验证） | 升级 2.12.3；全局变量支持无玩家解析 |
| MIG-407 | 调度器 | `task/SettlementScheduler` | 完成 | 现实日幂等结算；TAB/粒子独立调度 |

P4 验证记录（2026-11-08，JDK 25.0.5 + Gradle 9.5 + Paper 26.3-244）：

| 项 | 结果 |
|---|---|
| 编译 | `compileJava`、`compileTestJava` 成功，无 Fabric import |
| 完整构建 | `clean build` 成功，产物 14 MB（已 shade Gson 与 SQLite JDBC） |
| 单元测试 | 完整套件 84/84 通过；`ProtectionServiceTest` 4/4、`AntiExploitServiceTest` 7/7 |
| 旧数据 | 城镇 1 / 国家 1 / 居民 3 / 领地 1；`balance_cents` 为 500000 / 12550 / 25025；迁移备份已生成 |
| 命令 | `/geo info`、`/geo top wealth`、`/geo list towns`、`/geo list nations`、`/town list` 输出正常 |
| 一致性 | `/geotowny verify`：共 0 项（错误 0，警告 0） |
| 监听与任务 | 保护、Tab、反刷监听注册无异常；Tab（2 秒）、粒子（5 秒）、反刷清理（5 秒）任务持续运行，0 异常堆栈 |
| 反刷-凋零 | 控制台 `summon minecraft:wither` 输出“已阻止凋零生成”；随后实体查询 `Test failed` |
| 反刷-鞘翅 | 鞘翅掉落物生成被拦截，输出“已阻止鞘翅掉落物生成” |
| 反刷-重力方块 | 同一位置 1 秒内两次生成沙子掉落物，输出“检测到可能的重力方块刷取” |
| 反刷-末地门 | 同一位置重复生成末影之眼，输出“检测到可能的末地传送门刷取” |
| PlaceholderAPI | 2.12.3 官方发行包加载成功；`papi list` 显示 `geotowny`，`papi info` 状态 Registered |
| 占位符解析 | `papi parse --null %geotowny_version%` → `3.0.0-SNAPSHOT`；`%geotowny_currency%` → `Geo` |
| 聊天事件 | Paper 26.3 `ChatProcessor` 在存在监听器时仍触发 `AsyncPlayerChatEvent` 并使用 `getFormat()`；`TabListManager` 已注册 |
| 粒子调用 | `Particle.DUST` + `DustOptions` 编译通过；1 个领地实际进入渲染循环且无异常 |

残余风险（未虚报）：

1. 聊天前缀路径已通过 Paper 26.3 字节码静态确认，但**尚未用真实玩家确认最终聊天显示**；若实机发现格式未生效，需在 Paper 专用分支改用 `AsyncChatEvent`。
2. 反刷的“清除在线玩家背包鞘翅”任务已正常运行，但当前无玩家在线，**未实测实际清空背包**；规则分类与冷却逻辑由单元测试覆盖。
3. PlaceholderAPI 的玩家相关变量（余额/城镇/国家）未用真实玩家验证；全局变量与扩展注册已验证。

补充：PlaceholderAPI **2.11.6 无法在 Paper 26.3 加载**（版本号 `build` 段触发
`NumberFormatException`），已升级到 **2.12.3**。运行时必须使用 Hangar/Modrinth 的官方发行包；
Maven 上的 `placeholderapi-2.12.3.jar` 未 shade bStats，不能直接作为服务端插件使用。

### P5 GUI（完成）

| ID | 任务 | 涉及文件 | 状态 | 验收结果 |
|---|---|---|---|---|
| MIG-501 | 商店 GUI | `gui/ShopGui`、`command/ShopCommand`、`service/MarketService`、`storage/MarketStore` | 完成（已验证） | 54 格分页；左/右键买/卖；Shift 16 倍；动态价格、额度、持久化 |
| MIG-502 | 自由市场 GUI | `gui/FreeMarketGui`、`command/FreeMarketCommand`、`service/FreeMarketService`、`storage/FreeMarketStore`、`domain/market/Listing` | 完成（服务/存储/命令已验证） | 挂单/购买/取消、我的挂单、分页；GUI 点击待玩家 |
| MIG-503 | 签到 GUI | `gui/CheckInGui`、`command/CheckInCommand`、`service/CheckInService`、`storage/CheckInStore` | 完成（服务已验证） | 7 天日历、领取、连续/断签、奖励 |
| MIG-504 | 兑换码管理 GUI | `gui/RedeemGui`、`command/RedeemCommand`、`service/RedeemService`、`storage/RedeemStore` | 完成（命令/存储已验证） | 列表/生成/删除/兑换；GEO/ITEM/COMMAND |
| MIG-505 | 城镇成员 GUI | `gui/TownMemberGui`、`/town members` | 完成（已接入） | 成员列表、分页、详情；GUI 点击待玩家 |
| MIG-506 | 物品序列化 | `util/ItemCodec`、`util/ItemDelivery`、`/geotowny verify` 自检 | 完成（运行自检） | Base64 完整 ItemStack；旧 NBT 挂单/物品码安全丢弃 |

P5 验证记录（2026-11-08）：

| 项 | 结果 |
|---|---|
| 构建 | `clean build` 成功，产物约 15 MB |
| 单元测试 | 完整套件 112/112；`ItemCodecTest` 3/3、`FreeMarketServiceTest` 4/4、`CheckInServiceTest` 6/6、`RedeemServiceTest` 5/5、`MarketServiceTest` 8/8、`ShopCatalogTest` 2/2 |
| 服务加载 | Paper 26.3：市场 92 项、自由市场 0 挂单、签到 0 玩家、兑换码 0 个 |
| 命令 | 控制台 `/fm`、`/checkin`、`/town members` 正确提示仅玩家；`/redeem` 显示用法 |
| 兑换码 | `TESTGEO` 创建/列表/删除成功；随机码 `2FBT6DTE`（100.00，2 次）生成并持久化 |
| 序列化 | `/geotowny verify` 输出“物品序列化自检: 通过” |
| 数据文件 | `freemarket.json`、`checkins.json`、`redeem_codes.json`、`market.json` 均生成 |
| 一致性 | `/geotowny verify` 0 项；启动与命令执行期间 0 异常堆栈 |
| 商店联动 | 商店槽位 50 的“自由市场”按钮已改为打开 `FreeMarketGui` |

说明与残余风险：

1. P5 的 GUI、服务、命令、JSON 存储均已接入；金额继续使用 `Money(long cents)`。
2. 当前无真实玩家环境，以下交互路径尚未实机验证：GUI 点击/分页/Shift 交易、自由市场实际买卖与取消、签到 GUI 领取、兑换码 ITEM/COMMAND 发放、城镇成员 GUI 点击。
3. 签到/自由市场/兑换码使用新 JSON 路径（`data/checkins.json`、`data/freemarket.json`、`data/redeem_codes.json`）；旧 Fabric 的 `checkins.db`、`freemarket.json`、`redeem_codes.json` 未自动导入。
4. 旧 NBT 挂单或物品兑换码缺少新 `itemData` 时会被安全丢弃，不阻断启动。
5. 新增配置：`market.shop.*`、`market.free-market.*`、`checkin.*`、`redeem.enabled`。

### P6 战争 / 侵略 / 意识形态（完成）

| ID | 任务 | 涉及文件 | 状态 | 验收结果 |
|---|---|---|---|---|
| MIG-601 | `WarService` + 状态机 | `domain/war/Campaign`、`service/WarService`、`listener/CampaignListener` | 完成（单测） | 宣战/停战/公投、Campaign 状态守卫、死亡/复活计数 |
| MIG-602 | `Campaign` 合并 | `WarService#startCampaign`、`/nation campaign|invade|siege` | 完成 | 攻城与侵略共用 Campaign、占领、赔偿、流亡流程 |
| MIG-603 | BossBar 与复活 | `WarService` BossBar/待复活任务 | 完成（代码/编译） | 读条 BossBar、旁观者延迟复活、失败赔偿链 |
| MIG-604 | 占领与流亡 | `WarService` 占领/首都/流亡/复国/吞并 | 完成（投降/吞并单测） | 城镇国库归占领国、市长降级、国王 rank 清理 |
| MIG-605 | 意识形态机制接线 | `service/IdeologyService`、`NationService` | 完成（单测） | 民主公投/弹劾/任期、帝国吞并/行省税、联邦自由退出、神权圣战/异端 |
| MIG-606 | 节日/影响力/击杀掉落 | `FestivalService`、`InfluenceService`、`KillDropService`、`SettlementScheduler` | 完成（单测） | 日结算、税收/市场倍率、影响力增长、击杀掉落 |

P6 验证记录（2026-11-08）：

| 项 | 结果 |
|---|---|
| 构建 | `clean build` 成功，产物约 15 MB |
| 单元测试 | 完整套件 135/135；P6 新增 23 项：`CampaignTest` 5/5、`WarServiceTest` 4/4、`IdeologyServiceTest` 6/6、`InfluenceServiceTest` 3/3、`KillDropServiceTest` 2/2、`FestivalServiceTest` 3/3 |
| 运行时 | Paper 26.3 启动无异常；旧数据仍为城镇 1 / 国家 1 / 居民 3 / 领地 1 |
| 影响力 | 控制台 `/nation influence` 输出国家影响力与稳定度 |
| 命令守卫 | `/nation war|campaign|invade|siege|surrender|restore|warvote|impeach|annex` 控制台均正确提示仅玩家 |
| 一致性 | `/geotowny verify` 0 项；物品序列化自检通过 |
| 配置 | 新增 `war.campaign.*`、`war.kill-drop.*`、`festival.*`、`influence.*` |

说明与残余风险：

1. Campaign 为实时战斗会话，服务器重启后自动取消（与旧侵略系统一致），不持久化。
2. 当前无真实玩家环境：Campaign 实战、BossBar 显示、旁观者复活、城堡占领/复国/吞并的全链路尚未实机验证；状态机、宣战、投降、吞并与日结算已由单元测试覆盖。
3. 攻城点数来自击杀与方块破坏监听器，已注册但未用真实玩家验证。
4. 仅民主/帝国/联邦/神权 4 个核心国体保留专属机制；其余国体保留称呼与数值倍率，选择时显式告警。
5. 战争 PVP/保护放行由既有保护服务按 `AT_WAR` 关系处理；未额外改动反刷规则。

### P7 Web（完成）

| ID | 任务 | 涉及文件 | 状态 | 验收结果 |
|---|---|---|---|---|
| MIG-701 | HTTP 服务抽离 | `web/WebService`、`web/WebAssetManager` | 完成 | 独立启停，`bind/port/thread-pool-size` 可配置 |
| MIG-702 | 会话与认证保留 | `web/WebService`、`web/WebSecurity` | 完成（部分运行验证） | 一次性链接、密码登录、Cookie 会话、登录限流、CSRF |
| MIG-703 | 业务调用改服务 | `web/WebService` | 完成 | 通过 Town/Nation/Resident/Market/FreeMarket/TownLog 服务读写 |
| MIG-704 | 主线程调度 | `web/WebService#runSync` | 完成 | `callSyncMethod` 调度，5 秒超时，HTTP 线程不直接碰 Bukkit |
| MIG-705 | HTML 外置 | `resources/web/*`、`web/WebAssetManager` | 完成 | 首次启动复制到 `plugins/GeoTowny/web`，改界面无需重编译 |
| MIG-706 | 安全加固 | `web/WebService` | 完成 | 删除硬编码域名、Origin/CSRF 校验、CSP、默认绑定 127.0.0.1、无 CORS `*` |

P7 验证记录（2026-11-08）：

| 项 | 结果 |
|---|---|
| 构建 | `clean build` 成功，产物约 15 MB |
| 单元测试 | 完整套件 139/139；`WebSecurityTest` 2/2、`ChatBufferTest` 1/1、`WebAssetsTest` 1/1 |
| 页面 | `/`、`/app.js`、`/style.css` 均返回 200 |
| 公共信息 | `/api/info` 返回 200（在线/居民/城镇/国家统计） |
| 认证拦截 | `/api/player` 401；错误密码登录 401；恶意 Origin 403 |
| 单城镇约束 | `/api/player/switch-town` 返回 410 |
| 安全头 | 已返回 CSP、`X-Frame-Options: DENY`、`X-Content-Type-Options: nosniff`、`Referrer-Policy: no-referrer` |
| 资源外置 | `plugins/GeoTowny/web` 下已生成 `index.html`、`app.js`、`style.css` |
| 命令 | `/geo web status` 正常；`/geo web login`、`/geo web password set` 控制台正确提示仅玩家 |
| 一致性 | `/geotowny verify` 0 项；物品序列化自检通过 |
| 异常 | 启动与请求期间 0 异常堆栈 |

说明与残余风险：

1. 当前无真实玩家登录：密码登录成功、一次性链接换会话、带 CSRF 的已认证 POST 尚未端到端实测；`WebSecurity` 哈希/令牌与页面资源已单测，未登录/错误登录/恶意 Origin 已运行验证。
2. 会话保存在内存，服务器重启后需重新登录；会话有效期配置为 720 小时。
3. 旧数据中裸露的 `password_hash` 不是新格式，无法直接登录；需在游戏内重新执行 `/geo web password set <密码>`。
4. `/api/map/` 需配置 `web.map-url` 才启用；未配置时返回 503，当前未做真实地图代理验证。
5. 默认绑定 `127.0.0.1`，公网部署应由反向代理提供 HTTPS；`web.url` 使用 https 时 Cookie 自动追加 `Secure`。
6. 未设置 `web.url` 时一律使用本机地址，不存在硬编码生产域名。

### P8 验收（5/6 完成，MIG-806 部分通过）

| ID | 任务 | 状态 | 验收结果 |
|---|---|---|---|
| MIG-801 | 全量编译 | 完成 | `clean build` / `shadowJar` 成功；Fabric/Minecraft 引用数 0；产物约 15 MB |
| MIG-802 | 单元测试 | 完成 | 146 tests / 1 skipped / 0 failures / 0 errors |
| MIG-803 | 冒烟测试 | 完成（服务级） | 建城→圈地→建国→宣战→占领→解散通过；真实玩家驱动待补测 |
| MIG-804 | 数据迁移演练 | 完成 | 2 城镇 / 1 国家 / 3 居民 / 3 领地导入，一致性 0 错误 |
| MIG-805 | 压力测试 | 完成（离线） | 5,000 城镇 / 10,000 领地，日结算 2,733 ms；200 在线未模拟 |
| MIG-806 | 回归对照 | 部分通过 | 23 场景：21 通过、2 部分；旧快照未录制 |

详细证据见 [`P8_ACCEPTANCE_REPORT.md`](P8_ACCEPTANCE_REPORT.md)。

P8 验证记录（2026-11-08）：

| 项 | 结果 |
|---|---|
| 构建 | `clean build` 成功，`shadowJar` 约 15 MB |
| 测试 | 完整套件 146 项；`P8SmokeTest` 2/2、`P8MigrationRehearsalTest` 1/1、`P8StressTest` 1/1（显式开启）、`PhysicalCurrencyTest` 3/3 |
| 运行时 | Paper 26.3：旧数据加载、`/geotowny verify` 0 项、`/geo info`、`/geo list towns`、`/nation influence`、`/geo web status`、`/currency denominations|issued|reset` 正常，0 异常 |
| 压力 | `P8_STRESS towns=5000 claims=10000 settlement_ms=2733`，阈值 30,000 ms |
| 迁移 | 旧格式全量导入后一致性 0 错误，金额仍为 `Money(long cents)` |
| 快照 | `docs/snapshots/` 不存在；旧快照从未实际录制，无法逐字段回放 |

未收口项：

1. MIG-806：需在旧 Fabric 服务端录制 23 个快照后重放对比。
2. S-19 实体货币 PDC 实机：8 面额、2% 手续费、防复制 ID、发行量持久化已实现；纸币创建/背包存取需真实玩家补测。
3. S-12 实时战役赔偿链、S-20 玩家登录成功链路、MIG-805 200 在线规模均需真实玩家/在线环境补测。

---

## 6. 数据迁移方案

### 6.1 路径映射

| 旧路径 | 新路径 |
|---|---|
| `config/geotowny.json` | `plugins/GeoTowny/config.yml` |
| `config/geotowny/data/towns/*.json` | `plugins/GeoTowny/data/towns/*.json` |
| `config/geotowny/data/nations/*.json` | `plugins/GeoTowny/data/nations/*.json` |
| `config/geotowny/data/townblocks.json` | `plugins/GeoTowny/data/townblocks.json` |
| `config/geotowny/residents.db` | `plugins/GeoTowny/residents.db` |
| `config/geotowny/freemarket.json` | `plugins/GeoTowny/data/freemarket.json` |
| `config/geotowny/market.json` | `plugins/GeoTowny/data/market.json` |
| `config/geotowny/redeem_codes.json` | `plugins/GeoTowny/data/redeem_codes.json` |
| `config/geotowny/logs/*.json` | `plugins/GeoTowny/data/logs/*.json` |

### 6.2 世界键映射

现有数据用 `minecraft:overworld`、`minecraft:the_nether`、`minecraft:the_end`。Bukkit 默认世界名为 `world`、`world_nether`、`world_the_end`。

方案：

1. `config.yml` 增 `world-mapping`，默认内置三条映射。
2. 未命中映射的世界保留原 key 并告警一次。
3. `WorldRegistry` 负责双向查询，业务层只使用内部 `worldKey`。
4. 迁移脚本生成映射报告，人工确认后执行。

### 6.3 JSON 版本化

- 每个文件增加 `"schemaVersion": N`，旧文件缺失视为 0。
- 迁移链 `0 → 1`：补 `schemaVersion`、金额字段 `double` → `long`（分）、`worldKey` 规范化。
- 迁移链 `0 → 1` 同时执行 D-01：`Resident.townUuids` 折叠为单一 `townUuid`，输出多城镇玩家清单供服主复核。
- 迁移前整目录备份为 `backup-<timestamp>`。
- 迁移失败自动回滚（先写临时目录，成功后原子替换）。

### 6.4 SQLite 版本化

- 使用 `PRAGMA user_version`。
- 现有 `MIGRATION_CHECKS` 的 `ALTER TABLE` 改为版本化迁移脚本。
- 新增 `ledger` 表（若启用）。

### 6.5 回滚策略

- 只读校验模式：`/geo admin verify` 不写数据。
- 出现异常时保留源数据不动，插件拒绝启动并输出修复建议。
- 数据迁移脚本独立可执行，支持 dry-run。

---

## 7. 测试策略

| 层级 | 工具 | 范围 |
|---|---|---|
| 领域单元测试 | JUnit 5 + AssertJ | 税务、破产、merge、dissolve、占领、时间轴、金额 |
| 服务集成测试 | 内存存储替身 | 事务回滚、幂等结算、一致性修复 |
| 平台集成测试 | MockBukkit（版本兼容需实测）或真实测试服 | 命令、事件、GUI |
| 回归对照 | 旧版录制快照 | 关键场景输出一致 |
| 压力测试 | 自建数据生成器 | 大规模结算与并发保存 |

测试红线：

1. 每个 `*Service` 的公开方法至少有 1 个正常路径 + 1 个失败路径测试。
2. 状态机每个转换至少 1 个守卫测试。
3. 数据迁移必须有 dry-run 测试。
4. 禁止用"编译通过"替代运行时验证。

---

## 8. 风险登记表

| ID | 风险 | 影响 | 缓解 |
|---|---|---|---|
| R-01 | 26.x API 与 1.21 差异 | 高 | 先做最小验证插件，确认命令/GUI/事件/文本 API |
| R-02 | 命令层 4,425 行迁移 | 高 | 用 CommandAPI，按根命令分批交付，逐条对照 |
| R-03 | 战争/侵略语义未定 | 高 | P0 必须完成玩法决策，否则冻结该模块 |
| R-04 | 无测试基线 | 高 | P0 补领域测试，P8 回归对照 |
| R-05 | MockBukkit 滞后 26.x | 中 | 兜底真实测试服 + 脚本化冒烟 |
| R-06 | Gson/SQLite 未正确 shade | 中 | CI 检查 jar 内容，测试服加载验证 |
| R-07 | 模组商品数据残留 | 中 | 迁移时裁剪并输出清单 |
| R-08 | Web 面板安全 | 中 | 保留限流，增加 CSRF/Origin 校验，删除硬编码域名 |
| R-09 | 26.1 已 EOL | 中 | 以 26.3 为主目标，26.1 仅兼容 |
| R-10 | 金额 double→long 转换误差 | 中 | 一次性四舍五入 + 迁移报告 + 对账 |

---

## 9. 决策结论（P0 已关闭）

详细选项、利弊、证据与推荐见 [`DECISIONS.md`](DECISIONS.md)。

| 编号 | 决策 | 结论 |
|---|---|---|
| D-01 | 一人多城镇 | 单城镇；多城镇数据迁移时折叠为活动城镇 |
| D-02 | "一天"的定义 | 统一现实日；`RealDayClock` 默认，游戏日仅实验开关 |
| D-03 | 战争与侵略 | 合并为 `WarService` + `Campaign`，城镇归属变更单一入口 |
| D-04 | 资产归属 | 解散退市长（无市长归国家），占领归占领国，居民余额不动 |
| D-05 | 意识形态范围 | 保留民主/帝国/联邦/神权 4 个核心，其余降级为称呼+倍率 |

决策细节与删除清单见 [`DECISIONS.md`](DECISIONS.md)。

---

## 10. 完成定义（DoD）

一个阶段完成必须同时满足：

1. 编译通过，且 `grep -r "net.minecraft\|net.fabricmc\|eu.pb4"` 在已迁移模块中为 0。
2. 该阶段新增/修改的公开方法有测试。
3. 测试服冒烟通过，日志无异常堆栈。
4. 文档同步更新（功能矩阵、配置说明、命令表）。
5. 数据迁移可重复执行且幂等。

---

## 11. 附录

### 11.1 命令清单（现状）

| 根命令 | 别名 | 子命令规模 |
|---|---|---|
| `/town` | `/t` | 约 40 |
| `/nation` | `/n` | 约 30 |
| `/plot` | `/p` | 约 10 |
| `/resident` | `/res` | 约 8 |
| `/geo` | — | 约 15 |
| `/shop` | — | 约 5 + GUI |
| `/market` | — | 约 10 |
| `/freemarket` | `/fm` | 约 8 + GUI |
| `/alliance` | `/al` | 约 8 |
| `/checkin` | `/ci` | 约 4 + GUI |
| `/redeem` | — | 约 8 + GUI |
| `/currency` | — | 约 6 |

### 11.2 超长方法清单（重构重点）

| 文件 | 行号 | 长度 | 处理 |
|---|---|---|---|
| `command/TownCommand.java` | 1119 | 233 行 | 拆入 `TownService.merge()` |
| `command/TownCommand.java` | 1352 | 153 行 | 拆入 `TownService.split()` |
| `command/TownCommand.java` | 578 | 140 行 | 拆入日志查询服务 |
| `economy/EconomyManager.java` | 275 | 150 行 | 拆入 `TownService.dissolve()` |
| `war/InvasionManager.java` | 63 | 124 行 | 拆入 `WarService.startInvasion()` |
| `war/InvasionManager.java` | 614 | 118 行 | 拆入 `CompensationService` |

### 11.3 现有技术债（来自 README）

- 金额 `double` 精度风险。
- 部分存储同步阻塞主线程。
- 数值平衡未验证。
- 零测试。
- 部分地缘政治加成仅记录日志。
- 中文本地化不完整。

以上均纳入对应阶段处理，不单独开"清理阶段"。