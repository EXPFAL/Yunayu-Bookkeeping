# Sprint 0 脚手架计划（Yunayu-Bookkeeping）

> 对齐文档：`docs/PRD.md`（PRD v1）
> 分支：develop
> MVP 范围红线（来自 PRD 第四节）：仅 P0 三项（3 秒极速记账 / 学期预算看板 / 学业关联标签）+ P1 自然语言记账。P2（课程表联动、兼职/奖学金追踪）与 PRD 第二节明确砍掉的功能一律不在本计划中出现。
>
> 本计划只定义工程结构与接口草图，不包含任何实现代码。

---

## 1. 最小可编译 Compose + Room 工程结构

### 1.1 架构原则

- Clean Architecture + MVVM 三层隔离：UI / Domain / Data。
- 依赖方向只能向内：`:app` → `:ui` → `:domain` ← `:data`；`:domain` 是纯 Kotlin 模块，不依赖 Android 框架，不依赖任何其他模块。
- `:domain` 只定义 Repository 接口与 UseCase；`:data` 实现接口（Room + DataStore）；`:ui` 只面向接口编程，经 Hilt 注入具体实现。

### 1.2 模块划分

| 模块 | 职责 | 关键依赖 |
| --- | --- | --- |
| `:app` | 组装层：Application（Hilt 入口）、MainActivity、模块组装与导航根 | `:ui` `:data` `:domain`、Hilt |
| `:domain` | 纯 Kotlin：领域模型、Repository 接口、UseCase、预算引擎接口 | 仅 Kotlin stdlib + Coroutines |
| `:data` | Room 数据库 + DAO + Entity、RepositoryImpl、DataStore 偏好 | Room（KSP）、DataStore、Hilt |
| `:ui` | Compose 屏幕、ViewModel、主题 | Compose BOM、lifecycle、Hilt、`:domain` |

### 1.3 目录树草图

```
Yunayu-Bookkeeping/
├── app/                          # :app 组装层
│   └── src/main/kotlin/com/expfal/yunayu/app/
│       ├── YunayuApplication.kt  # @HiltAndroidApp
│       └── MainActivity.kt       # 空 Activity，仅 setContent 欢迎页
├── domain/                       # :domain 纯 Kotlin，无 Android 依赖
│   └── src/main/kotlin/com/expfal/yunayu/domain/
│       ├── model/                # Transaction / Tag / Semester 等领域模型
│       ├── repository/           # Repository 接口
│       └── usecase/              # UseCase（含预算引擎接口）
├── data/                         # :data Room + DataStore
│   └── src/main/kotlin/com/expfal/yunayu/data/
│       ├── local/entity/         # Room Entity
│       ├── local/dao/            # DAO
│       ├── local/YunayuDatabase.kt
│       ├── repository/           # RepositoryImpl
│       └── di/                   # Hilt Module
├── ui/                           # :ui Compose
│   └── src/main/kotlin/com/expfal/yunayu/ui/
│       ├── screen/               # 各功能屏幕
│       ├── viewmodel/            # 各功能 ViewModel
│       ├── theme/                # Material3 主题（深色跟随系统）
│       └── navigation/
├── gradle/libs.versions.toml     # 版本目录，唯一版本来源
└── docs/                         # PRD / SCAFFOLD
```

### 1.4 最小可编译验证标准

Sprint 0 完成的判定条件（全部满足才算脚手架通过）：

1. `./gradlew assembleDebug` 成功产出 APK，无编译错误、无 warning 阻塞。
2. `:app` 空 `MainActivity` + Compose 欢迎页可启动（显示应用名即可）。
3. `:data` 空数据库可建表：`YunayuDatabase` 包含 `tags` / `transactions` / `semesters` 表，首次启动 Room 建表成功（Instrumented 或 `createFromAsset`/内存库冒烟验证）。
4. Hilt 依赖图可编译：`:app` 能注入 `:data` 提供的一个 Repository 实现（编译通过即可）。
5. `ktlintCheck` 通过。
6. `:domain` 模块 `dependencies` 中无任何 Android/AndroidX 依赖（纯 Kotlin 验证）。

---

## 2. 包名与模块划分

- applicationId：`com.expfal.yunayu.app`（沿用项目历史包名前缀 `com.expfal.yunayu`）
- 各模块包结构：

| 模块 | 包 | 内容 |
| --- | --- | --- |
| `:app` | `com.expfal.yunayu.app` | Application、MainActivity |
| `:domain` | `com.expfal.yunayu.domain.model` | 领域模型（Transaction、Tag、Semester、BudgetSnapshot 等） |
|  | `com.expfal.yunayu.domain.repository` | TransactionRepository、TagRepository、SemesterRepository 接口 |
|  | `com.expfal.yunayu.domain.usecase` | UseCase（动词+名词命名，如 AddTransactionUseCase）、SemesterBudgetEngine 接口 |
| `:data` | `com.expfal.yunayu.data.local.entity` | TransactionEntity、TagEntity、SemesterEntity |
|  | `com.expfal.yunayu.data.local.dao` | TransactionDao、TagDao、SemesterDao |
|  | `com.expfal.yunayu.data.local` | YunayuDatabase |
|  | `com.expfal.yunayu.data.repository` | 各 RepositoryImpl |
|  | `com.expfal.yunayu.data.di` | Hilt @Module / @Provides |
| `:ui` | `com.expfal.yunayu.ui.screen.{feature}` | {Feature}Screen.kt |
|  | `com.expfal.yunayu.ui.viewmodel` | {Feature}ViewModel |
|  | `com.expfal.yunayu.ui.theme` | Material3 主题，深色模式默认跟随系统 |
|  | `com.expfal.yunayu.ui.navigation` | 导航图 |

命名规范沿用既定约定：ViewModel={Feature}ViewModel；Screen={Feature}Screen.kt；Repository={Feature}Repository/{Feature}RepositoryImpl；Entity={Feature}Entity；UseCase={Verb}{Noun}UseCase。

---

## 3. 依赖版本清单

以下组合已经调研核实，直接采用。所有版本集中在 `gradle/libs.versions.toml`，代码中一律经 `alias(libs.xxx)` / `implementation(libs.xxx)` 引用，不允许散落硬编码版本号。仓库源：Google Maven + Maven Central。

### 3.1 构建工具链

| 项 | 版本 | 兼容性依据 |
| --- | --- | --- |
| AGP | 8.5.2 | AGP 8.5 官方要求 Gradle ≥ 8.7、JDK 17，最高支持 API 34 |
| Gradle wrapper | 8.7 | 满足 AGP 8.5 的最低要求 |
| JDK | 17 | AGP 8.5 官方要求；**注意：本机当前仅有 JDK 26，脚手架执行前必须先安装 JDK 17，并在 `gradle.properties` 配置 `org.gradle.java.home` 指向 JDK 17。这是已知阻塞项，见第 6 节** |
| compileSdk / targetSdk | 34 | AGP 8.5 最高支持 API 34 |
| minSdk | 26 | 满足 Room/DataStore/Compose 要求，覆盖主流学生机 |

### 3.2 Kotlin 与 Compose

| 项 | 版本 | 说明 |
| --- | --- | --- |
| Kotlin | 2.0.10 | — |
| KSP | 2.0.10-1.0.24 | KSP 版本前缀必须与 Kotlin 版本严格一致（2.0.10-*） |
| Compose BOM | 2024.08.00 | Compose 依赖一律由 BOM 管理，不写单个版本号 |
| Compose Compiler | `org.jetbrains.kotlin.plugin.compose` 插件 | 与 Kotlin 同版本（2.0.10）；不再使用 `composeOptions` 块 |

### 3.3 AndroidX 与 DI

| 项 | 版本 | 说明 |
| --- | --- | --- |
| Room | 2.6.1 | 编译器用 `ksp(room-compiler)`，**禁用 kapt**；Entity 必须有 migration 策略 |
| Hilt | 2.51.1 | 编译器用 ksp |
| DataStore | 1.1.1 | 偏好设置（如首次启动标记、提醒开关） |
| core-ktx | 1.13.1 | — |
| activity-compose | 1.9.1 | — |
| lifecycle | 2.8.4 | ViewModel + compose 集成 |
| ktlint | 0.50.0 | 代码风格门禁 |

### 3.4 备选组合（本次不采用）

较新组合 AGP 8.7.3 + Kotlin 2.0.21 + Compose BOM 2024.10.00 本次不采用，理由：求稳优先，优先保证已知可编译组合快速落地，后续迭代再评估升级。

---

## 4. 学业标签体系初始数据模型草图（仅 Room Entity schema，不写实现）

对应 PRD P0-3「学业关联标签」：内置「学习 / 社交 / 生活 / 娱乐」四大类根节点 + 自定义子标签（教材 / 考证 / 实习等），标签树存 Room，支持拖拽排序。

### 4.1 表 `tags`

> Schema v2 已重建本表，详见 §7。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | Long, PK, autoGenerate | 主键 |
| name | String | 标签名 |
| parentId | Long? | null = 根节点；四大类「学习/社交/生活/娱乐」种子化为根节点 |
| sortOrder | Int | 拖拽排序，同级递增 |
| icon | String? | emoji 图标 |
| createdAt | Long | 创建时间戳（epochMillis） |
| updatedAt | Long | 更新时间戳（epochMillis） |

### 4.2 Entity 草图

> Schema v2 已重建本表，详见 §7。

```kotlin
@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "parent_id") val parentId: Long?,   // null = 根节点
    @ColumnInfo(name = "sort_order") val sortOrder: Int,   // 同级递增，支持拖拽排序
    @ColumnInfo(name = "icon") val icon: String?,          // emoji
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
```

### 4.3 交易 ↔ 标签关联（MVP 决策）

- **方案 A（本计划默认，简化）**：单标签外键 `TransactionEntity.tagId: Long?`，一笔交易最多挂一个学业标签。
- 方案 B（多对多关联表）留待后续迭代，不在 MVP 落地。
- 决策待用户确认，见第 6 节上报事项 (c)。

### 4.4 DAO 需求点（仅列需求，不写实现）

- 按 `parentId` 查子节点，返回 `Flow<List<TagEntity>>`（根节点查询传 `parentId = null`）。
- 批量更新 `sortOrder`（拖拽排序后整层重写）。
- 种子化：首次建库插入四大类根节点（学习/社交/生活/娱乐）。

---

## 5. 学期预算引擎接口签名草图（仅 interface，不写实现）

对应 PRD P0-2「学期预算看板」：按学期设总预算 → 自动拆解周/月可用额度 → 进度条预警；考试周/寒暑假自动切换预算策略。

```kotlin
data class Semester(
    id: Long,
    name: String,
    startDate: LocalDate,
    endDate: LocalDate,
    totalBudgetCents: Long,
    examWeekRanges: List<DateRange>,
    vacationRanges: List<DateRange>,
)

enum class BudgetPhase { NORMAL, EXAM_WEEK, VACATION }

data class BudgetSnapshot(
    totalBudgetCents: Long,
    spentCents: Long,
    remainingCents: Long,
    remainingDays: Int,
    weeklyQuotaCents: Long,
    monthlyQuotaCents: Long,
    phase: BudgetPhase,
)

interface SemesterBudgetEngine {
    fun observeBudgetSnapshot(semesterId: Long, today: LocalDate): Flow<BudgetSnapshot>
    fun calcWeeklyQuota(remainingCents: Long, remainingDays: Int, phase: BudgetPhase): Long
    fun calcMonthlyQuota(remainingCents: Long, remainingDays: Int, phase: BudgetPhase): Long
    fun resolvePhase(semester: Semester, date: LocalDate): BudgetPhase
}
```

要点说明：

- **核心算法**（来自 PRD）：周额度 = (剩余总额 ÷ 剩余天数) × 7；月额度按同一日均值 × 30 推导。
- **金额单位**：一律用分（`Long`），避免浮点误差；展示层再格式化为元。
- **不持久化额度**：weeklyQuota / monthlyQuota 不落库，由 `Flow.combine`（学期信息 + 已花费聚合）实时推导，保证数据单一事实来源。
- `DateRange` 为 domain 层值对象草图（起止日期），具体字段随实现确定。
- 文案遵循 PRD 温和提醒原则（如"本周还剩 ¥320"），引擎只产出数据不产出文案。

---

## 6. 上报事项（需用户决策）

| # | 事项 | 状态 / 建议 |
| --- | --- | --- |
| (a) | **JDK 阻塞项**：本机当前仅有 JDK 26，AGP 8.5.2 官方要求 JDK 17。脚手架执行前必须先安装 JDK 17，并在 `gradle.properties` 配置 `org.gradle.java.home` 指向 JDK 17，否则无法构建 | 已解决：本机已安装 JDK 17（`C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot`）；`org.gradle.java.home` 已从仓库 `gradle.properties` 移除，改由用户级 `~/.gradle/gradle.properties` 生效，不污染仓库 |
| (b) | **Gradle wrapper jar 获取**：本机 Gradle CLI 未安装，且历史执行中曾出现网络不可达。`gradle-wrapper.jar` 需在线下载或手工放置到 `gradle/wrapper/`，执行脚手架前需确认网络可用或离线提供 jar | 已解决：`gradle-wrapper.jar` 已就绪；`distributionUrl` 保留腾讯云镜像（官方源在本机曾超时），并补充 `distributionSha256Sum` 校验发行包完整性 |
| (c) | **交易↔标签关联方案**：方案 A 单标签外键（`TransactionEntity.tagId: Long?`，简化，本计划默认）vs 方案 B 多对多关联表（更灵活，留待后续）。本计划按方案 A 推进，未经确认不切换到方案 B | 默认方案 A，待用户确认 |
| (d) | **仓库历史**：旧代码归档分支 `archive/v1-final` 在本地已不存在；用户已确认以 `git init` 重建仓库，当前 develop 分支仅含 `docs/PRD.md`（commit 07249e2），不再追溯旧历史 | 已确认，仅备案 |

---

## 7. Schema v2 增强记录

> 触发时机：零用户数据增强窗口。Schema 版本 1 → 2，通过显式 `MIGRATION_1_2` 迁移，禁止 fallbackToDestructiveMigration。

### 7.1 变更内容

1. **tags 表重建**
   - 新增自引用外键 `parent_id → tags(id)`，`ON DELETE CASCADE`：删除父标签级联删除整棵子树；子树上交易的 `tag_id` 经既有外键 `ON DELETE SET NULL` 自动置空。
   - 新增唯一索引 `(parent_id, name)`：同父节点下不允许重名。
   - 删除冗余单列索引 `index_tags_parent_id`（已被 `(parent_id, sort_order)` 复合索引最左前缀覆盖）。
2. **transactions 表**：新增复合索引 `(occurred_at, type)`，服务预算聚合查询形态。
3. **新增 date_ranges 子表**：持久化考试周 / 假期区间，修复 `SemesterRepositoryImpl` 此前静默丢弃 `examWeekRanges` / `vacationRanges` 的契约漂移。字段：`id`(PK) / `semester_id`(FK→semesters, CASCADE, 带索引) / `range_type`(EXAM_WEEK|VACATION) / `start_date` / `end_date`（ISO LocalDate 文本，snake_case 命名与现有表一致）。

### 7.2 关键决策理由

- **CASCADE 语义**：标签树删除父节点即删除整棵子树，避免孤儿子树残留；交易与标签为弱关联（`SET NULL`），标签删除后交易保留、仅置空 `tag_id`，符合「交易不可因标签清理而丢失」原则。
- **(parent_id, name) 唯一的 NULL 根节点注意点**：SQLite 唯一索引中多个 `NULL parent_id` 可共存（NULL 互不相等），因此根节点（`parent_id IS NULL`）重名不会被该唯一索引拦截，需在仓储层写入前显式同名校验并返回明确错误（当前无 add 入口，已在 `TagRepositoryImpl` 留 TODO 注释）。
- **date_ranges 子表而非 JSON 列**：区间需要按 `semester_id` 关联查询、级联删除、以及未来按日期范围做预算阶段判定，独立子表 + 外键比 JSON 列更利于索引与关系完整性，也避免 JSON 解析开销与 Room 类型映射负担。
- **删除入口安全规则（硬性）**：任何标签/学期删除入口必须先查询并展示影响面（子树节点数、将被置空的交易笔数、将级联的区间数），并经用户二次确认；删除须经仓储层封装，不走 DAO 直删。

### 7.3 Migration(1, 2)

`MIGRATION_1_2`（定义于 `YunayuDatabase`，经 `DatabaseModule` 注册）分三步：

1. **tags 重建**：`CREATE TABLE tags_new`（带自引用外键）→ `INSERT SELECT` 迁移数据（保留四大类根节点及子树）→ `DROP TABLE tags` → `RENAME tags_new TO tags` → 重建唯一/复合索引。为防 `DROP tags` 触发既有外键 `SET NULL` 清空 `transactions.tag_id`，迁移前先备份、重建后恢复该关联。
2. **transactions**：`CREATE INDEX IF NOT EXISTS (occurred_at, type)`。
3. **date_ranges**：`CREATE TABLE date_ranges` + `CREATE INDEX (semester_id)`。

导出 schema 见 `data/schemas/com.expfal.yunayu.data.local.YunayuDatabase/2.json`。

### 7.4 v3 已知欠账

以下为 Schema v2 遗留、留待 v3 迁移解决的已知欠账：

1. **`date_ranges.range_type` 无 CHECK 约束**：非法取值无法在 DB 层拦截，目前仅由仓储层映射时丢弃并在日志告警。
2. **区间合法性无约束**：`date_ranges` 的 `start_date` / `end_date` 顺序、跨区间重叠、区间越出学期范围等未在 DB 层校验。
3. **`semesters` 无唯一约束**：同名或同日期区间的重复学期可被写入，需引入唯一索引（如 `name` 或 `(start_date, end_date)`）并在迁移前做数据去重。

---

## 8. 测试基建记录

> 触发时机：全仓零测试 + Schema v2 落地后，为评审认定的最高风险区（SemesterRepository 与 Migration）补齐关键测试。仅新增测试类依赖，未实现任何 PRD 功能逻辑，未改动 PRD.md。

### 8.1 测试栈清单

| 项 | 版本 | 用途 |
| --- | --- | --- |
| JUnit5（junit-jupiter） | 5.10.2 | JVM 单元测试框架，经 `useJUnitPlatform()` 启用 |
| kotlinx-coroutines-test | 1.8.1 | `runTest` 与 Flow 测试（与既有 coroutines 同版） |
| androidx.room:room-testing | 2.6.1 | `MigrationTestHelper`（androidTest 用） |
| androidx.test:core / ext:junit | 1.6.1 / 1.2.1 | instrumented 测试运行器 |
| JaCoCo | 0.8.12 | Gradle 内置 `jacoco` 插件，覆盖率报告 |

### 8.2 模块配置

- `:data`：`testOptions.unitTests.all { it.useJUnitPlatform() }`；`sourceSets.androidTest.assets.srcDir("$projectDir/schemas")` 暴露 Room 导出 schema 给 `MigrationTestHelper`；应用 `jacoco` 插件并自定义 `testCoverage`（`JacocoReport`）报告任务。
- `:domain`：`tasks.test { useJUnitPlatform() }`；应用 `jacoco` 插件，报告用内置 `jacocoTestReport` 任务。
- `:ui`：`testOptions.unitTests.isReturnDefaultValues = true` 使未 mock 的 Android API（如 `android.util.Log`）静默返回默认值而非抛「not mocked」；需要真实 Android 行为的测试应改用 Robolectric（后续引入），避免假绿。
- 暂不设硬性覆盖率门禁，报告可用即可（避免 Scaffold 阶段误杀）。

### 8.3 JVM 单元测试（data/src/test）

- `SemesterRepositoryImplTest`：手写 fake `SemesterDao` / `SemesterDateRangeDao` + `FakeYunayuDatabase`（不引入 mock 库，覆盖 `RoomDatabase` 事务执行器与事务边界方法使 `withTransaction` 可在 JVM 执行），`runTest` 覆盖：save 新增（id=0 走 insert 并写区间）、save 更新（update 返回 1）、save 陈旧 id（update 返回 0 抛 `IllegalStateException`）、区间重写只删 `KNOWN_RANGE_TYPES`、observeAll 组装完整区间、非法日期记录被过滤且不崩溃、未知 rangeType 被过滤。
- `TagRepositoryImplTest`：`getChildren` 映射正确性的基础用例。

运行方式：`./gradlew.bat test`（主命令，覆盖 :domain JVM 模块等全部本地单测）；`testDebugUnitTest` 仅覆盖 Android 模块（:app/:data/:ui）的本地单测，不含 :domain 的 JVM 测试任务。

### 8.4 Migration 测试（data/src/androidTest，待设备执行）

- `MigrationTest`：`MigrationTestHelper` + `InstrumentationRegistry`，校验 v1 → v2 迁移后 `tags` 数据保留、`transactions.tag_id` 关联恢复、唯一索引 `index_tags_parent_id_name` 存在。
- 本机无模拟器/设备，该测试写好但不执行，接入 CI 后启用。

---

## 9. P0-2 评审修复决策留痕

> 触发时机：三维评审合并清单落地（DatePicker UTC 错位、统一时间源、金额输入收紧、观察链防崩溃、考试周/假期区间配置 UI 等）。

### 9.1 本次已交付

- **月额度展示**：预算看板激活态辅助区展示「本月可花 ¥…」，数据来自快照 `monthlyQuotaCents`。
- **考试周 / 假期区间配置 UI**：学期设置弹层新增两个可折叠区块，支持查看、删除、添加区间（起≤止校验）；编辑模式预填现有区间，UI 成为区间唯一来源。
- **千分位为统一展示规范**：`formatCents` 输出带千分位 + 固定两位小数；Sprint 1 QuickAdd 展示基线同步遵循该规范。

### 9.2 推迟项

- **历史学期查阅与自动归档推迟**：数据保全（`semesters` 全量落库、引擎支持任意 `semesterId` 计算），但历史学期列表/切换 UI 待后续迭代。

### 9.3 硬约束与约定

- **禁止持久化 `date_ranges.id`**：区间采用「先删（仅已知类型 EXAM_WEEK / VACATION）后重写」语义，`date_ranges.id` 不稳定，调用方不得依赖区间主键。
- **DI 装配约定**：domain 用例装配可置于 `:data`，后续新增用例遵循既有装配位置，不在 `:domain` 内自建 DI。
- **transactions 全表 Flow 规模假设**：预算引擎对 `transactions` 全表 Flow 实时聚合，假设单用户万笔量级；超过后再改为按学期聚合查询。
- **门禁主命令**：`./gradlew.bat test`（覆盖 `:domain` 等全部本地单测），辅以 `ktlintCheck` 与 `assembleDebug`。

---

## 10. 月度预算改造记录

> 触发时机：用户实机反馈「学期维度过重」，P0-2 从学期预算看板改造为月度预算看板；Domain/Data 已先行交付，本节记录决策留痕。

### 10.1 学期 → 月度决策

- **用户实机反馈**：学期维度（考试周/寒暑假策略、跨学期归档）对个人学生记账偏重，日常真正关心的是「每月生活费还能花多少」。
- **决策**：删除 `semesters` / `date_ranges` 两表与 `Semester` / `SemesterBudgetEngine` / `BudgetSnapshot` / `BudgetPhase` 等领域模型，改为月度预算单值配置，按自然月滚动、跨月自动重算。

### 10.2 存储与数据口径

- **月度预算**：经 DataStore 单 key（`budget_prefs` / `monthly_budget_cents`）持久化，未设置发射 `0` 由 UI 转译引导态。
- **预算引擎**：`剩余 ÷ 剩余天数 × 7` 实时推导周额度；额度不落库（`Flow.combine` 单一事实来源）。
- **最近列表口径**：`transactions JOIN tags` 取 `tag_name`，`LIMIT` 最近 N 笔按 `occurred_at` 倒序，`distinctUntilChanged` 抑制重复发射。
- **预测补足语义**：`recent` 高频分类不足时以 `roots` 补足、按 `id` 去重后取前 4。

### 10.3 Schema v3 与 MIGRATION_2_3

- schema 版本 2 → 3：`MIGRATION_2_3` 以事务包裹 `DROP TABLE date_ranges` 与 `DROP TABLE semesters`（先删区间子表再删学期主表），`transactions` / `tags` 表不变。
- 导出 schema 见 `data/schemas/com.expfal.yunayu.data.local.YunayuDatabase/3.json`。

### 10.4 约束废止与门禁

- 旧 §9.3「禁止持久化 `date_ranges.id`」约束随 `date_ranges` 表删除而废止。
- 门禁主命令沿用 `./gradlew.bat test`（覆盖 `:domain` 等全部本地单测），辅以 `ktlintCheck` 与 `assembleDebug`。
