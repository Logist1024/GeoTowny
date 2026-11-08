# GeoTowny 功能矩阵（MIG-001）

| 项 | 内容 |
|---|---|
| 文档版本 | v1.0 |
| 基线版本 | GeoTowny v2.3.2（Fabric 1.21.8） |
| 用途 | 冻结迁移前的功能范围，作为 P3~P7 的逐项验收依据 |
| 状态定义 | 见下表 |

## 状态定义

| 标记 | 含义 | 判定标准 |
|---|---|---|
| 已实现 | 有完整入口与真实副作用 | 命令/事件能改变持久化状态或发送可见效果 |
| 半实现 | 有入口，但副作用不完整 | 缺校验、缺扣费、缺状态推进、缺清理 |
| 仅展示 | 只有文字/日志/占位符，无实际效果 | 计算了倍率但没有事件层消费 |
| 未接线 | 代码存在但没有调用方 | 死方法或死配置 |
| 待移除 | 依赖模组或不可迁移 | 引用了外部模组物品/API |

统计（按代码证据）：

| 状态 | 数量 |
|---|---|
| 已实现功能点 | 约 150 |
| 半实现 | 18 |
| 仅展示 | 5 |
| 未接线（死方法/死配置） | 16 |
| 待移除 | 5 类模组商品 + 3 个模组联动 |

---

## 1. 模块矩阵

| 模块 | 主要能力 | 状态 | 证据 | 迁移影响 |
|---|---|---|---|---|
| 城镇 | 建城/解散/改名/合并/分裂/圈地/居民/职位/国库/税率 | 已实现 | `command/TownCommand.java` | 命令层重写；merge/split 下沉服务 |
| 国家 | 建国/成员城镇/首都/国王/税率/国库 | 已实现 | `command/NationCommand.java` | 同上 |
| 外交 | 同盟/敌对/中立/贸易/互不侵犯/附庸 | 半实现 | `diplomacy/DiplomacyManager.java:40` 附庸只写关系 | 附庸需定义实际机制或降级为关系 |
| 领地 | 区块声明/相邻校验/上限/个人地块/信任 | 已实现 | `claim/ClaimManager.java` | 逻辑可复用 |
| 地块类型 | DEFAULT/SHOP/FARM/EMBASSY/FORT/BANK/ARENA/JAIL/INN/WILDS/CAPITAL_CORE | 半实现 | `data/PlotType.java`；JAIL/WILDS/CAPITAL_CORE 无实际逻辑 | 明确保留哪些类型 |
| 保护 | 破坏/放置/交互/PVP | 已实现 | `protection/ProtectionManager.java` | 事件语义需逐条对照 |
| 领地日志 | 进出/破坏/容器/火焰/怪物/PVP | 已实现 | `protection/EventListener.java` | 拆分监听器 |
| 反刷 | 末地门/重力方块/鞘翅 | 已实现 | `protection/EventListener.java:76` | 独立监听器 |
| 边界粒子 | 常驻外边界渲染 | 已实现 | `claim/BorderVisualManager.java` | 粒子 API 替换 |
| Tab/聊天 | `[国][城]` 前缀、TAB mod 让权 | 已实现 | `protection/TabListManager.java` | Spigot 权限/前缀 API 替换 |
| 占位符 | 20+ 变量 | 已实现 | `placeholder/GeoPlaceholders.java` | 改 PlaceholderAPI |
| 经济 | 三级资金流/税收/维护费/破产 | 已实现 | `economy/EconomyManager.java` | 金额改 long，事务化 |
| 动态市场 | 供需定价/随机波动/额度/统计 | 已实现 | `market/MarketManager.java` | 定价逻辑复用，GUI 重写 |
| 自由市场 | 挂单/购买/取消/过期 | 已实现 | `market/FreeMarketManager.java` | 物品序列化替换 |
| 签到 | 连续签到/奖励/断签 | 半实现 | `checkin/CheckInManager.java`；`checkInStreakTimeoutHours` 未接线 | 明确断签口径 |
| 兑换码 | GEO/物品/指令/次数/随机生成 | 已实现 | `redeem/RedeemManager.java` | 物品序列化替换 |
| 实体货币 | 8 种面额/2% 手续费/防复制 ID | 已实现 | `currency/PhysicalCurrencyManager.java` | NBT → PDC |
| 战争 | 宣战/停战/攻城/占领/赔偿/流亡 | 已实现 | `war/WarManager.java` | 状态机重构 |
| 侵略 | 读条/复活/BossBar/赔偿/占领 | 已实现 | `war/InvasionManager.java` | 与战争合并 |
| 意识形态 | 8 种国体 + 20+ 开关 | 半实现 | 见第 7 节 | 先裁剪再实现 |
| 影响力 | 每日增长/排名/科技上限 | 已实现 | `geopolitics/InfluenceManager.java` | 逻辑复用 |
| 节日 | 税收/影响力/市场价格倍率 | 半实现 | 经验倍率无消费方 | 经验加成需接线或删除 |
| 联盟 | 创建/邀请/加入/退出/解散/防御/贸易 | 已实现 | `geopolitics/AllianceManager.java` | 逻辑复用 |
| 在线奖励 | 按现实时间发放 | 已实现 | `task/Scheduler.java:178` | 现实时间，保留 |
| Web | 登录/面板/28 个 API/聊天 | 已实现 | `web/WebServer.java` | 抽离业务与主线程调度 |

---

## 2. 命令矩阵

> 权限列中"OP2"表示现有 `hasPermissionLevel(2)`；"角色"表示运行时判断市长/国王/议员；"无"表示无任何权限校验（迁移必须补）。

### 2.1 `/town`（别名 `/t`）

| 子命令 | 作用 | 状态 | 权限 | 迁移方式 |
|---|---|---|---|---|
| （根） | 多城镇选择/信息 | 已实现 | 玩家 | CommandAPI |
| `new <name>` | 建城 | 已实现 | 玩家 | 命令 + TownService |
| `select <name>` | 切换活动城镇 | 已实现 | 本镇成员 | 命令 |
| `delete` | 解散城镇 | 已实现 | 市长 | 命令 + TownService |
| `rename <name>` | 改名 | 已实现 | 市长 | 命令 + 服务 |
| `claim` / `unclaim` | 圈地/退地 | 已实现 | 市长/议员 | ClaimService |
| `migrate` | 部落制迁徙中心 | 半实现 | 市长 | 仅改 home/spawn，不迁领地 |
| `spawn [name]` | 传送城镇出生点 | 已实现 | 成员/公开 | 传送适配 |
| `merge <town>` / `merge accept` / `merge decline` | 城镇合并 | 已实现 | 市长 | 233 行方法下沉 |
| `force-merge <src> <dst>` | 强制合并 | 已实现 | OP2 | 权限节点 |
| `split <name>` | 分裂城镇 | 已实现 | 市长 | 153 行方法下沉 |
| `invite <player>` / `accept` / `decline` | 邀请加入 | 已实现 | 市长/议员 | 命令 |
| `join <name>` / `leave` | 加入/退出 | 已实现 | 玩家 | 命令 |
| `kick <player>` | 踢出 | 已实现 | 市长 | 命令 |
| `deposit <amt>` / `withdraw <amt>` | 国库存取 | 已实现 | 市长 | EconomyService |
| `list` / `members` / `info [name]` | 查询 | 已实现 | 无 | 命令 |
| `logs [filter]` | 领地日志分页 | 已实现 | 成员 | 命令 |
| `set board <text>` | 公告板 | 已实现 | 市长 | 命令 |
| `set taxes <amt>` | 居民税 | 已实现 | 市长 | 命令 |
| `set open <bool>` | 开放加入 | 已实现 | 市长 | 命令 |
| `set public <bool>` | 出生点公开 | 已实现 | 市长 | 命令 |
| `set mayor <player>` | 转让市长 | 已实现 | 市长 | TownService |
| `set spawn` | 设置出生点 | 已实现 | 市长 | 命令 |
| `set code <code>` | 城镇代码 | 已实现 | 市长 | 命令 |
| `set border` / `on` / `off` / `color` / `height` / `interval` | 边界粒子设置 | 已实现 | 市长 | 命令 |
| `set rank add/remove <player> <rank>` | 职位管理 | 已实现 | 市长 | 命令 |

### 2.2 `/nation`（别名 `/n`）

| 子命令 | 作用 | 状态 | 权限 | 备注 |
|---|---|---|---|---|
| （根） | 国家信息 | 已实现 | 无 | |
| `new <name>` | 建国 | 已实现 | 市长 | 未使用 `nationMinTowns`（死配置） |
| `add <town>` / `kick <town>` | 城镇加入/踢出 | 已实现 | 国王 | |
| `leave` | 城镇退出国家 | 已实现 | 市长 | 联邦制可自由退出 |
| `deposit` / `withdraw` | 国库存取 | 已实现 | 国王 | |
| `ally <nation>` / `enemy` / `peace` / `war` / `relation <n> <type>` | 外交 | 已实现 | 国王 | 民主制宣战需公投 |
| `invade <town>` | 发动侵略 | 已实现 | 国王 | 与战争重叠 |
| `ceasefire` | 停战 | 已实现 | 国王 | |
| `vassal <nation>` | 附庸 | 半实现 | 国王 | 只写关系，无附庸机制 |
| `accept` / `decline` | 处理待确认请求 | 已实现 | 目标国王 | 覆盖 add/ally/peace/vassal/annex/merge/relation |
| `list` / `info` / `spawn` | 查询/传送 | 已实现 | 无 | |
| `set king/capital/taxes/board/ideology/code` | 国家设置 | 已实现 | 国王 | ideology 变更触发异端惩罚 |
| `rename <name>` | 改名 | 已实现 | 国王 | |
| `force-merge <src> <dst>` | 强制合并 | 已实现 | OP2 | |
| `annex <nation>` | 帝国吞并 | 已实现 | 国王 | 未清理被吞并国国王 rank（缺陷） |
| `merge <nation>` | 国家合并 | 已实现 | 国王 | |
| `warvote <n> <yes|no>` | 民主制宣战公投 | 已实现 | 本国成员 | |
| `impeach` / `impeachvote <v>` | 弹劾 | 已实现 | 本国成员 | |
| `stimulus <town>` | 共和制刺激金 | 已实现 | 国王 | |
| `knight <player> <title>` | 君主制册封 | 半实现 | 国王 | 头衔无任何效果 |
| `clergy <player> <title>` | 神权制神职 | 已实现 | 国王 | 享受 10% 税收减免 |
| `oracle <type> <n>` | 神谕任务 | 半实现 | 国王 | `updateOracleProgress` 无调用 |
| `warehouse deposit/withdraw <item> <n>` | 公共仓库 | 半实现 | 会员 | 不校验/不扣除背包物品 |
| `rank add/remove <player> <rank>` | 国家职位 | 已实现 | 国王 | |

### 2.3 `/plot`（别名 `/p`）

| 子命令 | 状态 | 权限 | 备注 |
|---|---|---|---|
| `claim` / `unclaim` | 已实现 | 本镇成员/地块主 | |
| `set <type>` | 半实现 | 市长/地块主 | 部分类型无逻辑 |
| `fs [price]` / `nfs` | 已实现 | 市长/地块主 | |
| `buy` | 已实现 | 玩家 | |
| `perms add/remove <player>` / `perms` | 已实现 | 地块主 | 信任列表 |
| `info` | 已实现 | 无 | |

### 2.4 `/resident`（别名 `/res`）

| 子命令 | 状态 | 权限 |
|---|---|---|
| （根）/ `info [player]` | 已实现 | 无 |
| `pay <player> <amt>` | 已实现 | 玩家 |
| `list` | 已实现 | 无 |

### 2.5 `/geo`

| 子命令 | 状态 | 权限 | 备注 |
|---|---|---|---|
| `info` | 已实现 | 无 | 全局统计 |
| `save` / `reload` | 已实现 | OP2 | `reload` 只重载配置 |
| `list towns` / `list nations` | 已实现 | 无 | |
| `influence` | 已实现 | 无 | 影响力排行 |
| `war list` | 已实现 | 无 | |
| `top wealth/influence/size/online/towns` | 已实现 | 无 | |
| `festival` | 半实现 | 无 | 经验倍率仅展示 |
| `version` | 已实现 | 无 | 硬编码版本字符串 |
| `admin pay/take/setbalance` | 已实现 | OP2 | |
| `web password set/clear` | 已实现 | 玩家 | |

### 2.6 `/shop`

| 子命令 | 状态 | 权限 |
|---|---|---|
| （根）/ `page <n>` | 已实现 | 玩家 |
| `prices [kw]` / `quota` | 已实现 | 玩家 |
| `buy <item> <n>` / `sell <item> <n>` | 已实现 | 玩家 |
| `reload` | 已实现 | OP2 |
| `adjust <item> <mult>` / `resetprice` / `resetvolume` | 已实现 | OP2 |

### 2.7 `/market`

| 子命令 | 状态 |
|---|---|
| （根）打开商店 | 已实现 |
| `stats` / `trend <item>` / `help` | 已实现 |

### 2.8 `/fm`（自由市场）

| 子命令 | 状态 | 权限 |
|---|---|---|
| （根）/ `page <n>` / `mine` | 已实现 | 玩家 |
| `sell <item> <qty> <price>` | 已实现 | 玩家 |
| `buy <listing_id> <qty>` / `cancel <id>` | 已实现 | 玩家 |
| `search <kw>` | 已实现 | 玩家 |
| `save` | 已实现 | OP2 |

### 2.9 `/alliance`（别名 `/al`）

| 子命令 | 状态 |
|---|---|
| （根）/ `info [name]` / `list` | 已实现 |
| `new <name>` / `invite <nation>` / `accept` / `decline` / `leave` / `disband` | 已实现 |
| `set defensive/trade <flag>` / `set board <text>` | 已实现 |

### 2.10 `/checkin`（别名 `/ci`）

| 子命令 | 状态 |
|---|---|
| （根）打开签到 GUI | 已实现 |
| `info` | 已实现 |

### 2.11 `/redeem`

| 子命令 | 状态 | 权限 |
|---|---|---|
| （根）用法 | 已实现 | 玩家 |
| `<code>` | 已实现 | 玩家 |
| `create <code> geo/item/command ...` | 已实现 | OP2 |
| `list` / `delete <code>` / `setmax <code> <n>` | 已实现 | OP2 |
| `generate geo/item/command ...` | 已实现 | OP2 |
| `gui` | 已实现 | OP2 |

### 2.12 `/currency`

| 子命令 | 状态 |
|---|---|
| （根）/ `info` / `denominations` | 已实现 |
| `withdraw <denom>` / `deposit <denom> <count>` | 已实现 |

---

## 3. GUI 矩阵

| GUI | 入口 | 状态 | 槽位/交互 | 迁移方式 |
|---|---|---|---|---|
| 商店 | `/shop` | 已实现 | 54 格；商品 0~44；上一页 45；信息 49；自由市场 50；下一页 53；左/右/Shift 买卖 | InventoryHolder + ClickEvent |
| 自由市场 | `/fm` | 已实现 | 挂单列表、我的挂单、购买/取消 | 同上 |
| 签到 | `/checkin` | 已实现 | 7 天日历、领取 | 同上 |
| 兑换码管理 | `/redeem gui` | 已实现 | 列表/生成/删除 | 同上 |
| 城镇成员 | `/town` 多城镇场景 | 已实现 | 成员列表/操作 | 同上 |

---

## 4. Web 端点矩阵

共 28 个端点（`web/WebServer.java`）。

| 分类 | 端点 | 状态 | 备注 |
|---|---|---|---|
| 页面 | `/` | 已实现 | 内嵌 HTML（2985 行） |
| 认证 | `/api/auth/logout`、`/api/auth/password` | 已实现 | 一次性链接 + 密码登录，限流 |
| 信息 | `/api/info`、`/api/player`、`/api/inventory` | 已实现 | |
| 聊天 | `/api/chat`、`/api/sendchat` | 已实现 | ChatBuffer |
| 城镇 | `/api/towns`、`/api/town/detail`、`/api/town/settings`、`/api/town/treasury`、`/api/town/join`、`/api/town/logs` | 已实现 | 直接改模型，需改服务 |
| 国家 | `/api/nations`、`/api/nation/detail`、`/api/nation/settings`、`/api/nation/treasury` | 已实现 | 同上 |
| 玩家 | `/api/player/transfer`、`/api/player/switch-town` | 已实现 | 转账/切换活动城镇 |
| 市场 | `/api/market`、`/api/market/buy`、`/api/market/sell` | 已实现 | |
| 自由市场 | `/api/freemarket`、`/buy`、`/cancel`、`/list` | 已实现 | |
| 地图 | `/api/map/` | 已实现 | 需确认输出格式 |

---

## 5. 事件监听矩阵

| Fabric 回调 | 用途 | 迁移到 Bukkit |
|---|---|---|
| `PlayerBlockBreakEvents.BEFORE` | 破坏保护 + 日志 + 攻城点数 | `BlockBreakEvent` |
| `UseBlockCallback` | 放置/交互/点火/桶/容器/门/按钮/拉杆日志 | `PlayerInteractEvent` |
| `AttackEntityCallback` | PVP 日志 + 侵略击杀 | `EntityDamageByEntityEvent` |
| `ServerLivingEntityEvents.AFTER_DEATH` | 玩家/怪物击杀日志、掉落 | `EntityDeathEvent` |
| `ServerPlayerEvents.AFTER_RESPAWN` | 侵略复活计时 | `PlayerRespawnEvent` |
| `ServerEntityEvents.ENTITY_LOAD` | 怪物生成日志 | `CreatureSpawnEvent` |
| `ServerMessageEvents.ALLOW_CHAT_MESSAGE` | 聊天前缀 | `AsyncPlayerChatEvent` |
| `ServerPlayConnectionEvents.JOIN` | 居民创建、破产提示、Tab 刷新 | `PlayerJoinEvent` |
| `ServerPlayConnectionEvents.DISCONNECT` | 清理临时状态 | `PlayerQuitEvent` |
| `ServerLifecycleEvents.SERVER_STARTING/STARTED` | 数据加载 | `onEnable` |
| `ServerLifecycleEvents.SERVER_STOPPING` | 保存/关闭 | `onDisable` |
| `ServerTickEvents.END_SERVER_TICK` ×7 | 调度、粒子、Tab、侵略、Web | `BukkitRunnable` |

---

## 6. 存储矩阵

| 数据 | 现有后端 | 表/文件 | 迁移动作 |
|---|---|---|---|
| 城镇 | JSON | `data/towns/<uuid>.json` | 原结构 + schemaVersion |
| 国家 | JSON | `data/nations/<uuid>.json` | 同上 |
| 领地 | JSON | `data/townblocks.json` | worldKey 映射 |
| 居民 | SQLite | `residents` | 保留表，加版本号 |
| 密码 | SQLite | `residents.password_salt/hash` | 保留，SHA-256 迭代 |
| 签到 | SQLite | 复用 residents 表字段？需核对 | 迁移前核查 |
| 自由市场 | JSON | `freemarket.json` | 物品序列化替换 |
| 动态市场 | JSON | `market.json` | 保留 |
| 兑换码 | JSON | `redeem_codes.json` | 物品序列化替换 |
| 领地日志 | JSON | `logs/<town>.json` | 保留 |
| 联盟 | JSON | `alliances`（需核对路径） | 保留 |
| 实体货币发行量 | 内存 | `issuedCounts` | 重启丢失，已知缺陷 |

---

## 7. 半实现 / 未接线 / 死配置清单

### 7.1 半实现

| 功能 | 证据 | 具体缺陷 |
|---|---|---|
| 附庸 | `diplomacy/DiplomacyManager.java:40` | 只写 VASSAL/OVERLORD 关系，无税收、战争跟随、保护义务 |
| 部落迁徙 | `geopolitics/IdeologyManager.java:359` | 只改 homeBlockKey/spawn，旧中心与领地未处理 |
| 神谕任务 | `IdeologyManager.java:376`、`updateOracleProgress` 无调用 | 可发布，进度永远为 0，无法完成 |
| 公共仓库 | `IdeologyManager.java:332` | 只改计数，不校验/不扣除玩家背包物品，可凭空产出 |
| 骑士册封 | `IdeologyManager.java:266` | 只存头衔，无任何数值效果 |
| 城镇自治 | `IdeologyManager.isTownAutonomous` 无调用 | 联邦制自治未生效 |
| 节日经验 | `FestivalManager.getExpMultiplier` 无调用 | 倍率仅展示 |
| 签到断签 | `checkInStreakTimeoutHours` 0 引用 | 实际规则与配置不一致 |
| 地块类型 | JAIL/WILDS/CAPITAL_CORE | 枚举存在，无对应逻辑 |
| 帝国吞并 | `IdeologyManager.annexNation` | 未清理被吞并国国王的 `nationRank`，留下脏数据 |
| 首都核心 | `capitalCoreCritical` 0 引用 | 配置无效 |
| 战败割地 | `warTerritoryLossPercent` 0 引用 | 配置无效 |

### 7.2 无调用的方法（死方法）

| 方法 | 位置 |
|---|---|
| `isHereditary()` | `data/Ideology.java`（世袭传承） |
| `isCollectiveLabor()` | `data/Ideology.java`（集体劳动） |
| `isBloodOath()` | `data/Ideology.java`（血盟结义） |
| `isTownAutonomous()` | `geopolitics/IdeologyManager.java:77` |
| `updateOracleProgress()` | `geopolitics/IdeologyManager.java:393` |
| `getExpMultiplier()` | `geopolitics/FestivalManager.java:68` |

### 7.3 零引用配置项（死配置）

以下 15 项在 `GeoConfig` 中定义，但全项目无任何读取点：

| 配置项 | 默认值 | 预期用途 |
|---|---|---|
| `capitalCoreCritical` | true | 首都核心丢失亡国 |
| `checkInStreakTimeoutHours` | 24 | 签到断签超时 |
| `exileDays` | 7 | 流亡期（实际硬编码 7 天） |
| `ideologyEnabled` | true | 意识形态总开关 |
| `nationMinTowns` | 2 | 建国最低城镇数（实现中未校验） |
| `nationUpkeepPerTown` | 10 | 国家维护费 |
| `shopVirtualItemsOnly` | false | 虚拟商品开关 |
| `siegeDurationMinutes` | 120 | 攻城时长（硬编码 2 小时） |
| `siegePointPerBreak` | 5 | 破坏得点（使用硬编码常量） |
| `siegePointPerKill` | 20 | 击杀得点（使用硬编码常量） |
| `siegePointThreshold` | 100 | 占领阈值（硬编码 100） |
| `techEnabled` | true | 科技系统总开关 |
| `visualEnabled` | true | 领土可视化总开关 |
| `warTerritoryLossPercent` | 0.2 | 战败割地比例 |
| `webLoginExpiryHours` | 24 | Web 登录有效期（实际硬编码 30 天） |

### 7.4 待移除

| 项 | 证据 |
|---|---|
| FarmersDelight 商品 | `market/ShopItems.java:137` 起 |
| RusticDelight 商品 | `market/ShopItems.java:161` 起 |
| Enderscape 商品 | `market/ShopItems.java` 分类常量 |
| Fishing101 商品 | `market/ShopItems.java` 分类常量 |
| TAB mod 检测 | `protection/TabListManager.java:41` |
| Placeholder API (PB4) | `placeholder/GeoPlaceholders.java` |

---

## 8. 配置矩阵

> 迁移后改为 `config.yml`，保留字段名语义，但分组与校验重做。标注"死"的项在迁移时必须决定：接线或删除。

### 8.1 城镇

| 字段 | 默认值 | 状态 |
|---|---|---|
| `townCreationCost` | 1000.0 | 已用 |
| `claimCost` | 50.0 | 已用 |
| `upkeepPerBlock` | 1.0 | 已用 |
| `baseClaimBlocks` | 16 | 已用 |
| `claimBlocksPerResident` | 8 | 已用 |
| `maxTownBlocks` | 1024 | 已用 |
| `townBankruptcyDays` | 7 | 已用 |

### 8.2 国家

| 字段 | 默认值 | 状态 |
|---|---|---|
| `nationCreationCost` | 100000.0 | 已用 |
| `nationMinTowns` | 2 | 死 |
| `nationUpkeepPerTown` | 10.0 | 死 |
| `nationMaxTowns` | 50 | 已用 |

### 8.3 地块

| 字段 | 默认值 | 状态 |
|---|---|---|
| `shopPlotDailyIncome` | 5.0 | 已用 |
| `farmPlotUpkeepDiscount` | 0.5 | 已用 |
| `bankPlotInterestRate` | 0.001 | 已用 |
| `innPlotDailyIncome` | 2.0 | 已用 |
| `arenaPlotDailyIncome` | 1.0 | 已用 |
| `fortPlotDefenseBonus` | 0.05 | 已用 |
| `fortPlotUpkeepExtra` | 2.0 | 已用 |
| `embassyPlotUpkeep` | 3.0 | 已用 |
| `capitalCoreCritical` | true | 死 |

### 8.4 经济与地缘政治

| 字段 | 默认值 | 状态 |
|---|---|---|
| `startingBalance` | 100.0 | 已用 |
| `maxDailyTax` | 100.0 | 已用 |
| `currencyName` | "Geo" | 已用 |
| `influenceEnabled` | true | 已用 |
| `techEnabled` | true | 死 |
| `ideologyEnabled` | true | 死 |
| `influenceDailyGrowth` | 1.0 | 已用 |
| `autoSaveIntervalSeconds` | 300 | 已用 |

### 8.5 战争与侵略

| 字段 | 默认值 | 状态 |
|---|---|---|
| `warEnabled` | true | 已用 |
| `warDeclareCost` | 2000.0 | 已用 |
| `warTerritoryLossPercent` | 0.2 | 死 |
| `siegePointThreshold` | 100 | 死（硬编码 100） |
| `siegePointPerKill` | 20 | 死（硬编码常量） |
| `siegePointPerBreak` | 5 | 死（硬编码常量） |
| `siegeDurationMinutes` | 120 | 死（硬编码 2h） |
| `exileDays` | 7 | 死（硬编码 7d） |
| `invasionEnabled` | true | 已用 |
| `invasionCost` | 5000.0 | 已用 |
| `invasionBaseRespawnSeconds` | 10 | 已用 |
| `invasionRespawnIncreasePercent` | 0.10 | 已用 |
| `invasionCaptureCountdownSeconds` | 900 | 已用 |
| `invasionFailCountdownSeconds` | 300 | 已用 |
| `invasionCompensationPercent` | 0.70 | 已用 |
| `invasionMaxResidentContributionPercent` | 0.30 | 已用 |
| `warKillDropPercent` | 0.10 | 已用 |
| `warKillDropDecayFactor` | 0.70 | 已用 |
| `invasionMaxDefenderDeaths` | 15 | 已用 |
| `invasionDefenderRatio` | 0.40 | 已用 |
| `killDropEnabled` | true | 已用 |
| `killDropBaseChance` | 0.20 | 已用 |
| `killDropBaseAmount` | 10.0 | 已用 |
| `killDropDecayFactor` | 0.70 | 已用 |

### 8.6 保护与日志

| 字段 | 默认值 | 状态 |
|---|---|---|
| `protectBlockBreak` | true | 已用 |
| `protectBlockPlace` | true | 已用 |
| `protectInteract` | true | 已用 |
| `protectPVP` | true | 已用 |
| `wildernessPVP` | true | 已用 |
| `allowForeignerActions` | true | 已用 |
| `townLogRetentionDays` | 7 | 已用 |
| `townLogPageSize` | 50 | 已用 |
| `borderNotify` | true | 已用 |
| `logMonsterSpawn` | true | 已用 |
| `logFireSpread` | true | 已用 |
| `useExternalTab` | true | 已用（迁移后删除） |
| `visualEnabled` | true | 死 |

### 8.7 活跃度与节日

| 字段 | 默认值 | 状态 |
|---|---|---|
| `townAbandonedDays` | 14 | 已用 |
| `townAbandonedDissolveDays` | 7 | 已用 |
| `festivalEnabled` | true | 已用 |
| `festivalTaxMultiplier` | 0.5 | 已用 |
| `festivalInfluenceMultiplier` | 2.0 | 已用 |
| `festivalShopPriceMultiplier` | 0.9 | 已用 |
| `festivalExpMultiplier` | 1.5 | 仅展示 |
| `customFestivals` | {} | 已用 |
| `weekendFestival` | false | 已用 |

### 8.8 市场

| 字段 | 默认值 | 状态 |
|---|---|---|
| `shopEnabled` | true | 已用 |
| `shopTradeStep` | 1 | 已用 |
| `shopPriceElasticity` | 0.02 | 已用 |
| `shopPriceCeilingMultiplier` | 5.0 | 死 |
| `shopPriceFloorMultiplier` | 0.1 | 死 |
| `shopVolumeDecay` | 0.98 | 已用 |
| `shopDailyRandomRange` | 0.08 | 已用 |
| `shopSellBuyRatio` | 0.85 | 已用 |
| `shopPlayerDailyBuyLimit` | 512 | 已用 |
| `shopPlayerDailySellLimit` | 512 | 已用 |
| `shopServerDailyBuyLimit` | 8192 | 已用 |
| `shopServerDailySellLimit` | 8192 | 已用 |
| `shopVirtualItemsOnly` | false | 死 |
| `freeMarketEnabled` | true | 已用 |
| `freeMarketTax` | 0.0 | 已用 |
| `freeMarketMaxListingsPerPlayer` | 20 | 已用 |
| `freeMarketMinPrice` | 0.01 | 已用 |
| `freeMarketMaxPrice` | 1000000.0 | 已用 |
| `freeMarketExpireDays` | 0 | 已用 |

### 8.9 签到、兑换、在线奖励、Web

| 字段 | 默认值 | 状态 |
|---|---|---|
| `checkInEnabled` | true | 已用 |
| `checkInBaseReward` | 5.0 | 已用 |
| `checkInStreakTimeoutHours` | 24 | 死 |
| `checkInCycleDays` | 7 | 已用 |
| `checkInStreakRewards` | 7 项数组 | 已用 |
| `redeemEnabled` | true | 已用 |
| `onlineRewardEnabled` | true | 已用 |
| `onlineRewardIntervalMinutes` | 60 | 已用 |
| `onlineRewardAmount` | 200.0 | 已用 |
| `webEnabled` | true | 已用 |
| `webPort` | 8080 | 已用 |
| `webUrl` | `http://t35.sjcmc.cn:36406` | 硬编码生产域名，必须删除 |
| `webLoginExpiryHours` | 24 | 死（实际 30 天） |

---

## 9. 迁移优先级映射

| 优先级 | 功能 | 依据 |
|---|---|---|
| P3（先迁） | 城镇、国家、领地、居民、经济、命令 | 核心玩法，纯逻辑占比高 |
| P4 | 保护、日志、粒子、Tab、占位符 | 平台耦合但规则清晰 |
| P5 | 5 个 GUI + 物品序列化 | UI 重写，业务可复用 |
| P6 | 战争/侵略/意识形态 | 已决策，按 Campaign 与 4 核心国体重构 |
| P7 | Web | 依赖服务层，最后迁 |
| 删除 | 模组商品、模组联动、死配置 | 不可迁移或无效果 |

---

## 10. 验收口径

迁移每一模块时，以本矩阵的"状态"列为基准：

1. 状态为"已实现"的功能，迁移后必须保持同等副作用，并用 MIG-004 的行为快照对照。
2. 状态为"半实现"的功能，迁移前必须由 D-01~D-05 决策给出目标状态：补全、降级为"仅展示"、或删除。
3. 状态为"未接线"的方法与配置，迁移后不得原样保留；要么接线，要么删除，并在 CHANGELOG 记录。
4. 状态为"待移除"的内容，迁移时直接删除并输出清单。

---

## 11. 决策落地映射（2026-11-08）

D-01~D-05 已关闭，结论见 `DECISIONS.md`。本节把决策转成功能矩阵的最终目标状态。

### 11.1 功能目标状态变更

| 功能/命令 | 决策前 | 决策后目标 | 依据 |
|---|---|---|---|
| 一人多城镇 | 已实现 | 删除，单城镇 | D-01 |
| `/town select` | 已实现 | 删除 | D-01 |
| `/api/player/switch-town` | 已实现 | 删除 | D-01 |
| 多城镇重复征税 | 已实现 | 删除 | D-01 |
| `/town migrate` | 半实现 | 删除 | D-05 |
| `/nation knight` | 半实现 | 删除 | D-05 |
| `/nation stimulus` | 已实现 | 删除 | D-05 |
| 议会否决 | 已实现 | 删除 | D-05 |
| `/nation warehouse` | 半实现 | 删除 | D-05 |
| `/nation oracle` | 半实现 | 删除 | D-05 |
| 神谕任务进度 | 未接线 | 删除 | D-05 |
| 血盟结义 | 未接线 | 删除 | D-05 |
| 集体劳动 | 未接线 | 删除 | D-05 |
| 世袭传承 | 未接线 | 删除 | D-05 |
| 城镇自治 | 未接线 | 降级为"国家不强制覆盖城镇设置" | D-05 |
| 附庸义务 | 半实现 | 保留 VASSAL/OVERLORD 关系，不实现义务 | D-05 外围 |
| 节日经验倍率 | 仅展示 | 删除配置 | D-05 |
| 侵略 | 已实现 | 保留，合并进 Campaign | D-03 |
| 攻城 | 已实现 | 保留，合并进 Campaign | D-03 |
| 割地 | 已实现 | 保留，统一走 WarService | D-03 |
| 解散退款 | 已实现 | 保留；无市长时归国家 | D-04 |
| 占领国库 | 未明确 | 归占领国 | D-04 |
| 民主制机制 | 已实现 | 保留 | D-05 |
| 帝国制机制 | 已实现 | 保留，修复国王 rank 清理 | D-05 |
| 联邦自由退出 | 已实现 | 保留 | D-05 |
| 神权神职/圣战/异端 | 已实现 | 保留 | D-05 |

### 11.2 决策后删除清单（迁移时必须执行）

命令：

- `/town select`
- `/town migrate`
- `/nation knight`
- `/nation stimulus`
- `/nation warehouse`
- `/nation oracle`
- `/nation impeach` / `/nation impeachvote` 保留（民主制核心）

Web：

- `/api/player/switch-town`

配置：

- `ideologyEnabled`
- `techEnabled`
- `capitalCoreCritical`（若不做首都核心机制）
- `warTerritoryLossPercent`（若不做割地比例）
- `nationMinTowns`（若不补建国门槛）
- `nationUpkeepPerTown`
- `festivalExpMultiplier`
- `shopVirtualItemsOnly`
- `checkInStreakTimeoutHours`
- `webLoginExpiryHours`
- `siegePointThreshold` / `siegePointPerKill` / `siegePointPerBreak` / `siegeDurationMinutes` / `exileDays`（改为 Campaign 配置或删除）

代码：

- `Ideology.isHereditary()`
- `Ideology.isCollectiveLabor()`
- `Ideology.isBloodOath()`
- `IdeologyManager.isTownAutonomous()`
- `IdeologyManager.updateOracleProgress()`
- `IdeologyManager.grantStimulus()`
- `IdeologyManager.depositWarehouse()` / `withdrawWarehouse()`
- `IdeologyManager.publishOracle()`
- `IdeologyManager.bestowKnight()`
- `FestivalManager.getExpMultiplier()`
- `Resident.townUuids` / `townRanks`（迁移后删除）

### 11.3 决策后必须补的缺陷

| 缺陷 | 处理 | 依据 |
|---|---|---|
| 帝国吞并不清理国王 rank | `annexNation` 事务内清理 | D-04/D-05 |
| 占领国库未处理 | `occupy` 事务内转移 | D-04 |
| 公共仓库凭空产出 | 删除功能 | D-05 |
| 神谕永远无法完成 | 删除功能 | D-05 |
| 攻城配置硬编码 | 迁入 Campaign 配置并接线 | D-03 |
| 流亡期硬编码 | 迁入 Campaign 配置 | D-03 |
| 签到超时与配置不一致 | 按 D-02 统一现实时间 | D-02 |
| Web 登录有效期硬编码 | 删除配置或接线 | D-02 |