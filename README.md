<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" height="96" alt="云屿记账图标" />
</p>

<h1 align="center">云屿记账 · Yunayu Bookkeeping</h1>

<p align="center">个人独占 · 学生向 Android 原生记账应用</p>

<p align="center">
  <a href="https://developer.android.com/"><img src="https://img.shields.io/badge/Android-API%2026%2B-3DDC84?logo=android&logoColor=white&style=flat-square" alt="Android API 26+" /></a>
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white&style=flat-square" alt="Kotlin" /></a>
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Architecture-Clean%20Architecture%20%2B%20MVVM-7A4F4F?style=flat-square" alt="Clean Architecture + MVVM" />
  <a href="./LICENSE"><img src="https://img.shields.io/badge/License-MIT-yellow?style=flat-square" alt="MIT License" /></a>
</p>

## 简介

云屿记账是一款**个人独占、学生向**的 Android 原生记账应用，围绕三个诉求设计：**学生财务自律**、**学业生活融合**、**极简高效记录**。

数据全部存放在本机（Room + DataStore），单机运行、无云同步、无多用户体系——它是一件私人工具，而不是通用记账平台。

记账时不必打开复杂表单：首页悬浮按钮 + 自绘数字键盘即可 3 秒落一笔，或直接输入「昨天图书馆买咖啡 28」由自然语言解析成时间、金额与标签。

> 自然语言记账与 AI 报告需要自备 OpenAI 兼容 API key，解析时交易文本会发送到该服务；除此之外的全部功能**无需联网、无需配置**即可使用。

## 功能特性

- **极速记账** — 首页悬浮按钮 + 自绘数字键盘，3 秒落一笔；按收支方向分别统计最近高频分类并自动匹配。
- **自然语言记账** — 输入「昨天图书馆买咖啡 28」即解析出时间 / 金额 / 标签；未命中已有标签时直通「未分类」，不打断记账流程。
- **预算与资金** — 月度预算自动拆解为周可用额度（剩余 ÷ 剩余天数 × 7）并给进度预警；首页「持有资金」= 期初余额 + 累计收入 − 累计支出，负值提示已超支。
- **账户与转账** — 预置微信 / 支付宝 / 银行卡账户，支持期初余额与账户管理；账户间转账独立于收支统计，不污染预算与报告口径。
- **学业关联标签** — 学习 / 社交 / 生活 / 娱乐四大根类 + 独立收入根，内置种子子标签；可自定义子标签、长按拖拽排序、改名、删除（删除前展示影响面并二次确认），并提供标签整合合并。
- **记录管理与整理** — 收支管理页支持标签多选 + 时间快捷项（全部 / 近 7 天 / 近 30 天 / 本月）+ 备注关键词模糊搜索三条件组合筛选、单条删除；「整理」入口对未分类记录生成分批分类建议，逐条接受 / 修改 / 拒绝后一次应用。
- **报告与分析** — 周报 / 月报 / 年报，打开应用自动补生成；Canvas 自绘饼图（Top5 + 其他）、分类下钻、本地规则洞察（无 API 也不空白）；默认每周日 10:00 提醒复盘。
- **界面与体验** — Material 3 樱粉 + 奶油暖调主题（深色跟随系统）、冷启动 SplashScreen、侧栏收纳低频入口、标签树折叠组件，以及键盘遮挡、滚动位置保持等细节打磨。

## 界面一览

| 界面 | 用途 |
| --- | --- |
| 首页 | 月度预算看板置顶、持有资金卡片、最近记录列表，右侧垂直居中悬浮「记一笔」按钮 |
| 记一笔 | 「手动记」数字键盘直输与「自动记」自然语言输入双模式，可切换支出 / 收入 / 转账 |
| 收支管理 | 标签多选 + 时间快捷项 + 备注模糊搜索组合筛选；单条删除；进入编辑与「整理」 |
| 编辑交易 | 修改金额、类型、备注、标签、账户（时间字段暂不可改） |
| 管理标签 | 根标签只读分区，子标签增 / 改 / 删与长按拖拽排序，含标签整合入口 |
| 整理 | 对「未分类」记录分批生成分类建议，逐条接受 / 修改 / 拒绝后单事务批量应用 |
| 分析报告 | 周 / 月 / 年切换，预算执行与分类开销，Canvas 自绘饼图，分类可下钻至收支管理 |
| API 管理 | 配置自然语言记账与 AI 报告所用的 OpenAI 兼容服务，含连通性测试 |
| 管理账户 | 账户列表（名称 + 交易数）新增 / 改名 / 删除，可设期初余额 |

## 技术栈

> 版本以 [`gradle/libs.versions.toml`](gradle/libs.versions.toml) 为唯一来源，全项目禁止在模块脚本中硬编码版本号。

| 项 | 内容 |
| --- | --- |
| 语言 / 构建 | Kotlin 2.3.21 · Gradle 8.13 · AGP 8.13.2 |
| UI | Jetpack Compose（BOM 2024.08.00）· Material 3 |
| 依赖注入 | Hilt 2.57.2（编译器走 KSP） |
| 本地存储 | Room 2.8.4（KSP）· DataStore Preferences 1.1.1 |
| 异步 / 后台 | Kotlin Coroutines & Flow 1.8.1 · WorkManager 2.9.1 |
| 其他 | androidx.core:core-splashscreen 1.0.1 |
| 测试 | JUnit 5 · kotlinx-coroutines-test · JaCoCo |
| 代码规范 | ktlint 0.50.0 |
| 运行环境 | minSdk 26 · compileSdk / targetSdk 34 · JVM 17 |

## 架构

Clean Architecture + MVVM，四个 Gradle 模块，依赖方向只能向内：

```text
:app  (组装层：Hilt 入口 / MainActivity / 周报通知 Worker)
 ├──▶ :ui       Compose 屏幕 + ViewModel + 品牌主题 ──▶ :domain
 ├──▶ :data     Room + DataStore + RepositoryImpl ───▶ :domain
 └──▶ :domain   领域模型 / Repository 接口 / UseCase（纯 Kotlin，零 Android 依赖）
```

- `:domain` 是纯 Kotlin JVM 模块，只依赖 Kotlin stdlib 与 Coroutines，不含任何 Android / AndroidX 依赖。
- `:domain` 只定义 Repository 接口与 UseCase；`:data` 用 Room + DataStore 实现接口；`:ui` 只面向接口编程，具体实现经 Hilt 注入。
- 金额一律以「分」（`Long`）存储，绝不用浮点数；正负号是展示层概念，不改动存储口径。
- 数据库使用版本化 Room 迁移并导出 schema，不使用破坏性迁移。

### 构建踩坑：Kotlin 2.3 元数据与注解处理器

Dagger / Hilt 的编译器声明了旧版 `kotlin-metadata-jvm`，Room 又传递引入另一个版本，二者都无法读取 Kotlin 2.3 产出的高版本元数据（构建时报 `maximum supported version is 2.2.0`）。

根构建脚本因此在所有模块统一强制该库与 Kotlin 同版本：

```kotlin
configurations.configureEach {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-metadata-jvm:$kotlinMetadataVersion")
    }
}
```

新增注解处理器或 KSP 依赖时须复核这条 `force`——新处理器可能依赖不同版本的 `kotlin-metadata-jvm`。

## 快速开始

**环境要求**

- JDK 17（本项目 `compileOptions` 与 `jvmTarget` 均为 17）
- Android SDK Platform 34
- Windows 下使用 `gradlew.bat`，macOS / Linux 使用 `./gradlew`

**构建**

```bash
git clone https://github.com/EXPFAL/Yunayu-Bookkeeping.git
cd Yunayu-Bookkeeping

# 在项目根目录创建 local.properties，指向本机 Android SDK
# sdk.dir=C\:\\Users\\<你>\\AppData\\Local\\Android\\Sdk

./gradlew.bat assembleDebug      # 产物：app/build/outputs/apk/debug/
```

> `gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl` 指向腾讯云 Gradle 镜像（与官方同包，附 `distributionSha256Sum` 校验）。海外网络可自行替换为官方源。

### 可选：配置自然语言记账与 AI 报告

不配置也能正常编译和使用其余全部功能，只是「自动记」与 AI 报告不可用。有两条配置路径：

1. **运行期配置（推荐）** — 在应用内「API 管理」页面填写 Base URL / 模型名 / API Key。配置存放在本机 DataStore，**优先级更高**，为空时才回退到下面的编译期默认值。
2. **编译期默认值（可选）** — 在根目录 `local.properties` 中填写，默认值为 DeepSeek：

   ```properties
   NL_API_BASE_URL=https://api.deepseek.com
   NL_API_MODEL=deepseek-chat
   NL_API_KEY=sk-xxxxxxxx
   ```

> **注意**：编译期写入的 key 会明文进入 `BuildConfig` 与最终 APK，仅供个人本地构建使用。若要公开分发，请留空该字段并改用应用内的「API 管理」配置。

## 项目结构

```text
Yunayu-Bookkeeping/
├── app/        组装层：Application（Hilt 入口）、MainActivity、周报通知 Worker
├── domain/     纯 Kotlin 领域层：模型、Repository 接口、UseCase、预算引擎、NL / 报告纯逻辑
├── data/       数据层：Room Entity / DAO / Database、RepositoryImpl、DataStore、外部 API 适配、Hilt Module
├── ui/         界面层：screen/ 各功能屏幕、component/ 复用组件、theme/ 品牌主题、di/ 用例装配
├── docs/       产品需求（PRD）与工程脚手架 / 决策留痕（SCAFFOLD）
└── gradle/libs.versions.toml   版本目录：全项目唯一版本来源
```

## 测试与代码规范

提交前请跑满三条门禁：

```bash
./gradlew.bat test           # 全部本地 JVM 单测（含 :domain 纯 Kotlin 模块）
./gradlew.bat ktlintCheck    # 代码风格门禁
./gradlew.bat assembleDebug  # 产出 debug APK
```

- 测试栈：JUnit 5 + kotlinx-coroutines-test + JaCoCo 覆盖率报告。
- `:domain` / `:data` / `:ui` 的测试使用**手写 fake**，不引入任何 mock 框架——因此每次扩展接口都必须同步所有 fake，这是刻意保持的约束。
- Room 迁移与 DAO 的 instrumented 测试需要真机或模拟器，当前未接入自动化流程。
- 代码风格由 ktlint 全局门禁约束，所有子模块自动应用。

## 文档索引

- [`docs/PRD.md`](docs/PRD.md) —— 产品需求：定位、功能矩阵、交互口径与明确砍掉的范围。
- [`docs/SCAFFOLD.md`](docs/SCAFFOLD.md) —— 工程脚手架与逐迭代决策留痕：架构、schema 演进、外部模型选型等。

> SCAFFOLD 是一份中文决策日志，包含被后续迭代推翻的历史结论，阅读时请以**靠后的章节**为准。

## 路线与不做的事

这是个人独占工具，不追求通用记账软件的覆盖面。以下方向**明确不做**：

- 多成员 / 共享账本（个人使用，无需权限体系）
- 商户 / 项目管理（无 B 端对账需求）
- 人情礼簿 / 物品收纳（偏离财务主线）
- 多币种 / 汇率换算（国内校园场景几乎不用）
- 发票助手 / 财务云盘（无报销场景）
- Web 端 / 小程序（专注 Android 原生）

暂不计划：课程表联动（iCal 解析）、兼职与奖学金追踪。

## 项目状态与已知限制

- 尚未发布正式版本，仓库不提供 APK 分发，需自行构建。
- 未接入 CI，上述三条门禁需在本地手动执行。
- release 构建未配置签名，且未开启代码压缩（`isMinifyEnabled = false`）。
- 自然语言记账与 AI 报告依赖外部 OpenAI 兼容 API，解析时交易文本会上传；端侧离线模型路线经真机实测后已放弃。
- 编译期写入的 API key 会明文进入 `BuildConfig` 与 APK。
- 转账记录支持录入 / 查看 / 删除，暂不支持编辑。
- 交易编辑不支持修改时间字段。
- 周报通知受系统省电策略影响，触发时间可能延后。

## 致谢

- 预置标签树基于作者半年的真实账单消费结构归纳而成。
- 配色灵感来自作者自选的卡通形象。
- 感谢 Kotlin、Jetpack Compose、Material 3、Room、Hilt、DataStore、WorkManager 等开源项目。

## 许可证

本项目基于 [MIT 许可证](LICENSE)开源。

Copyright (c) 2026 EXPFAL
