# GeoTowny Spigot

GeoTowny 的 Spigot 26.x 重写工程。旧 Fabric 工程保留在
`https://github.com/dian-ZD/geotowny`，仅作为迁移参考与行为基线。

## 状态

**P8：验收 5/6 完成（MIG-806 旧快照对照未收口）。**

| 验收项 | 结果 |
|---|---|
| 构建 | `clean build` 成功，约 15 MB；Fabric/Minecraft 引用数 0 |
| 单元测试 | 146 项 / 1 跳过 / 0 失败 / 0 错误 |
| 冒烟 | 服务级建城→圈地→建国→宣战→占领→解散通过；真实玩家链路待补测 |
| 迁移演练 | 2 城镇 / 1 国家 / 3 居民 / 3 领地导入，一致性 0 错误 |
| 压力测试 | 5,000 城镇 / 10,000 领地，日结算 2,733 ms（阈值 30 s） |
| 运行时 | Paper 26.3：旧数据、`verify` 0 项、命令/Web/`/currency` 正常，0 异常 |
| 回归对照 | 23 场景：21 通过、2 部分（S-12 实时战役、S-20 玩家登录） |

已知未收口：旧 Fabric 的 23 个行为快照从未实际录制，无法逐字段回放；实体货币已实现
服务/持久化，PDC 纸币实机存取待补测；200 在线规模、实时战役赔偿链、玩家 Web 登录成功链路需真实环境补测。

已完成内容：

- 领域模型、`Money`（long 分）、JSON/SQLite 存储与迁移、一致性检查
- 城镇/国家/领地/居民/经济/战争/市场/GUI/Web 全部模块
- 实体货币：8 面额、2% 手续费、PDC 防复制 ID、发行量持久化、`/currency`
- P8 验收测试：服务级冒烟、旧数据迁移演练、离线压力测试
- `docs/P8_ACCEPTANCE_REPORT.md`：6 项验收结果与 23 场景证据矩阵

下一步：录制旧 Fabric 行为快照并重放以关闭 MIG-806；在真实玩家/在线环境补测
实体货币 PDC、Campaign、Web 登录与 GUI 交互。

尚未接入：旧快照逐字段对照。

## 构建要求

| 项 | 要求 |
|---|---|
| JDK | **25**（Minecraft 26.x / Spigot 26.x 最低要求） |
| Gradle | 使用仓库自带 Wrapper（9.5） |
| 目标服务端 | Spigot 26.1 ~ 26.3，主目标 26.3 |

```bash
./gradlew clean build
```

产物：`build/libs/GeoTowny-3.0.0-SNAPSHOT.jar`（已 shade Gson 与 SQLite JDBC）。

> 本地如果只有 JDK 17，Gradle 会因 `options.release = 25` 失败。请切换到 JDK 25，
> 或设置 `JAVA_HOME` 指向 JDK 25 后再执行。

> PlaceholderAPI 编译依赖为 `me.clip:placeholderapi:2.12.3`。运行时请使用 Hangar/Modrinth
> 的官方发行包；Maven 产物未 shade bStats，且 2.11.6 无法在 Paper 26.3 加载。

## 部署

1. 将产物放入服务端 `plugins/`。
2. 启动后生成 `plugins/GeoTowny/config.yml`。
3. 按需修改配置，执行 `/geotowny reload`。

## 目录结构

```
src/main/java/com/geotowny/
├─ GeoTownyPlugin.java        插件入口与生命周期
├─ Log.java                   日志适配（保留 {} 占位符）
├─ command/                   命令层
├─ config/                    配置视图与迁移器
└─ world/                     世界键映射

src/main/resources/
├─ plugin.yml
└─ config.yml
```

后续阶段将按 `docs/MIGRATION_PLAN.md` 增加：

```
domain/          纯 Java 领域模型与服务
application/     用例与事务边界
infrastructure/  JSON / SQLite / Web
platform/        Bukkit 监听、GUI、粒子、BossBar
```

## 文档

- [迁移设计](docs/MIGRATION_PLAN.md)
- [功能矩阵](docs/FEATURE_MATRIX.md)
- [决策记录](docs/DECISIONS.md)
- [测试与行为基线](docs/BEHAVIOR_BASELINE.md)

## 与旧工程的关系

- 旧工程：Fabric 1.21.8 服务端模组，24,543 行，已冻结。
- 本工程：独立仓库/目录，不引用旧代码，按迁移文档逐步搬迁。
- 数据兼容：P2 提供 `LegacyDataImporter`，读取旧 `config/geotowny/` 下的 JSON 与 SQLite。
- 行为兼容：按 `BEHAVIOR_BASELINE.md` 的 23 个场景做新旧对照。

## 许可证

MIT，见上游仓库 `LICENSE`。