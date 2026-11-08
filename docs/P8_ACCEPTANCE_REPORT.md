# P8 验收报告

| 项 | 内容 |
|---|---|
| 日期 | 2026-11-08 |
| 环境 | JDK 25.0.5 + Gradle 9.5 + Paper 26.3-244 |
| 数据 | 测试服旧数据：城镇 1 / 国家 1 / 居民 3 / 领地 1 |
| 产物 | `build/libs/GeoTowny-3.0.0-SNAPSHOT.jar`（约 15 MB） |

## 1. 验收结论

| ID | 任务 | 结论 | 说明 |
|---|---|---|---|
| MIG-801 | 全量编译 | 通过 | `clean build` / `shadowJar` 成功；源码无 Fabric/Minecraft 引用 |
| MIG-802 | 单元测试 | 通过 | 146 项，失败 0；压力测试默认跳过 1 项 |
| MIG-803 | 冒烟测试 | 通过（服务级） | 建城→圈地→建国→宣战→占领→解散 服务链路通过；未用真实玩家驱动 |
| MIG-804 | 数据迁移演练 | 通过 | 2 城镇 / 1 国家 / 3 居民 / 3 领地全量导入，一致性 0 错误 |
| MIG-805 | 压力测试 | 通过（离线） | 5,000 城镇 / 10,000 领地，日结算 2,733 ms；200 在线未模拟 |
| MIG-806 | 行为快照对照 | 部分通过 | 23 场景：21 通过、2 部分；旧快照未实际录制 |

**P8 整体：5/6 完成，MIG-806 未完全收口。**

## 2. 构建与测试

```bash
JAVA_HOME=/workspace/.tools/jdk25 ./gradlew clean build
```

- `shadowJar` 成功，产物约 15 MB。
- 源码检查：`net.minecraft` / `net.fabricmc` / `com.mojang` / `fabric` 引用数 **0**。
- 测试：**146 tests / 1 skipped / 0 failures / 0 errors**。
- 跳过项：`P8StressTest`（需 `P8_STRESS=1` 显式开启）。

P8 新增测试：

| 测试 | 结果 |
|---|---|
| `P8SmokeTest` | 2/2 |
| `P8MigrationRehearsalTest` | 1/1 |
| `P8StressTest` | 1/1（显式开启） |

## 3. MIG-803 服务级冒烟链路

`P8SmokeTest#buildClaimNationWarOccupyDissolveChain` 执行并断言：

1. Alice 建城、圈地相邻区块，城镇领地数变为 2。
2. Alice / Bob 分别建国。
3. A 国向 B 国宣战，扣除宣战费并写入 `AT_WAR`。
4. Campaign（SIEGE）成功：B 城转入 A 国，B 国进入流亡，Bob 降为居民。
5. 占领时 B 城国库转入占领国。
6. 解散 A 国：成员城镇解除国家归属。
7. 解散 A 城：城镇与领地删除，Alice 离镇。

`P8SmokeTest#townWithoutMayorDissolvesTreasuryIntoNation` 覆盖 S-23：
无市长城镇解散时国库归国家。

运行时回归：Paper 启动、旧数据加载、`/geotowny verify`、`/geo info`、
`/geo list towns`、`/nation influence`、`/geo web status` 正常，0 异常。

## 4. MIG-804 数据迁移演练

`P8MigrationRehearsalTest` 构造旧格式数据：

- 2 个城镇 JSON
- 1 个国家 JSON
- 3 条旧 residents 表记录（含旧 `balance`）
- 3 个领地
- `alliances.json`

经 `LegacyDataImporter` 导入后：

- 城镇 2、国家 1、居民 3、领地 3。
- Alice 余额由旧 `5000.0` 迁移为 `Money(5000.00)`。
- `ConsistencyChecker`：0 错误、0 警告。

## 5. MIG-805 压力测试

```bash
P8_STRESS=1 JAVA_HOME=/workspace/.tools/jdk25 \
  ./gradlew test --tests "com.geotowny.p8.P8StressTest"
```

结果：

```text
P8_STRESS towns=5000 claims=10000 settlement_ms=2733
```

- 数据规模：5,000 城镇、10,000 领地、1 国家。
- 日结算：影响力增长 + 稳定度回归 + 逐城镇持久化，**2,733 ms**。
- 阈值：30,000 ms。
- 限制：未模拟 200 在线玩家；平台在线人数相关开销需在真实服压测补测。

## 6. MIG-806 行为快照对照矩阵

> 历史快照目录 `docs/snapshots/` 不存在；`BEHAVIOR_BASELINE.md` 明确记录旧快照因环境阻塞尚未录制。
> 因此无法做旧/新逐字段回放，本表给出 23 个场景的新工程证据映射。

| 编号 | 场景 | 证据 | 结论 |
|---|---|---|---|
| S-01 | 建城扣费 | `TownServiceTest`、`P8SmokeTest` | 通过 |
| S-02 | 圈地相邻规则 | `ClaimServiceTest`、`P8SmokeTest` | 通过 |
| S-03 | 居民加入/退出（单城镇） | `ResidentTest`、`TownServiceTest` | 通过 |
| S-04 | 建国 | `NationServiceTest` | 通过 |
| S-05 | 城镇加入国家 | `NationServiceTest` | 通过 |
| S-06 | 每日税收（现实日） | `EconomyServiceTest` | 通过 |
| S-07 | 破产解散 | `EconomyServiceTest`、`TownServiceTest` | 通过 |
| S-08 | 城镇合并 | `TownMergeSplitTest` | 通过 |
| S-09 | 城镇分裂 | `TownMergeSplitTest` | 通过 |
| S-10 | 宣战/停战 | `WarServiceTest` | 通过 |
| S-11 | 攻城占领（Campaign） | `CampaignTest`、`P8SmokeTest` | 通过（服务级） |
| S-12 | 侵略成功/失败（Campaign） | `CampaignTest`、`WarServiceTest` | 部分：未覆盖完整实时赔偿链 |
| S-13 | 民主制公投/弹劾 | `IdeologyServiceTest` | 通过 |
| S-14 | 帝国吞并 | `WarServiceTest` | 通过 |
| S-15 | 动态市场买卖 | `MarketServiceTest` | 通过 |
| S-16 | 自由市场挂单/购买 | `FreeMarketServiceTest` | 通过（服务/存储） |
| S-17 | 签到 | `CheckInServiceTest` | 通过 |
| S-18 | 兑换码 | `RedeemServiceTest` | 通过 |
| S-19 | 实体货币存取 | `PhysicalCurrencyTest`、`/currency` 运行验证 | 通过（服务/持久化）；PDC 纸币实机待补测 |
| S-20 | Web 登录与面板操作 | `WebSecurityTest`、`WebAssetsTest`、HTTP 冒烟 | 部分：未做真实玩家登录成功链路 |
| S-21 | 多城镇折叠迁移 | `LegacyDataImporterTest`、`P8MigrationRehearsalTest` | 通过 |
| S-22 | 占领国库归属 | `P8SmokeTest` | 通过 |
| S-23 | 无市长解散 | `P8SmokeTest` | 通过 |

统计：**21 通过、2 部分、0 阻塞**。

## 7. 未收口项

1. **MIG-806 旧快照缺失**：需先在旧 Fabric 服务端录制 23 个快照，才能进行逐字段对照。
2. **S-19 PDC 纸币实机验证**：8 面额、2% 手续费、发行量持久化与命令已实现；纸币创建/背包存取需真实玩家补测。
3. **S-12 实时战役赔偿链**：Campaign 状态机与宣战/投降/吞并已测，但 BossBar/实时死亡/赔偿全链路需玩家环境。
4. **S-20 玩家登录成功链路**：无真实玩家，密码登录成功、一次性链接换会话、CSRF POST 未端到端实测。
5. **MIG-805 在线规模**：未模拟 200 在线，需真实服压测。

## 8. 结论

P8 的编译、测试、服务级冒烟、迁移演练和离线压力测试均通过；历史行为快照对照是主要
未收口项。实体货币已从“未迁移”转为“服务级实现”。建议下一步：

1. 录制旧 Fabric 23 个快照并重放，关闭 MIG-806。
2. 在真实玩家环境补测实体货币 PDC、Campaign、Web 登录与 GUI 交互。
3. 真实在线规模补做 200 在线压力测试。