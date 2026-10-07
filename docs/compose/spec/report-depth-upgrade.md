---
feature: report-depth-upgrade
status: delivered
updated: 2026-10-07
branch: cursor/v1-1-0-backup-ci-d984
commits: 1d3b48d..dfe5f17
---

# 分析报告升级：本地事实核 + 按需 AI 深读

## Report

**What was built** — 分析报告从「单期快照 + 洞察卡片」升级为可感知的复盘页：近 6 期序列、环比与分类增减取数层（`ReportSeriesCalculator` / `LoadReportSeriesUseCase`）；事实评分叙事（`ReportFactScorer` / `ReportNarrativeBuilder`）与规则建议（`ReportAdviceBuilder`）写入 `STORY`/`ADVICE`；AI 深读降级为按需按钮（`DeepReadReportUseCase`，复用 NL 通道）。视觉主次重塑后详情首屏为 Hero 数字区（周期标签 + 净结余大字 + ±% 环比徽章）→ 本期故事段 → 行动建议条 → 竖柱趋势图（本期高亮、金额在柱上）→ 环比对比条 → 分类增减 mini 条 → 交互饼图与折叠细节。零 schema 变更。

**Verification** — `./gradlew :ui:ktlintCheck :ui:testDebugUnitTest :domain:test :app:assembleDebug` 全绿；`:data:testDebugUnitTest` 中 `CompletionRequesterTest` 2 例失败为 PRE-EXISTING（与本变更无关，本次 diff 不触及 `data/nlparse`）。独立复审确认 S2.4.1 三处规格缺口（Hero 周期标签、环比 ±%、柱上金额）已闭合，调用签名一致。

**Journey log** — 功能按规格落地后用户反馈「和上一版区别不大」：根因是新模块复用了旧 UI 语汇（InsightCard / DetailLine）。体验换代要改「脸」而不只是加模块。material-icons-extended 不在依赖里，趋势/灵感图标改用 core 的 KeyboardArrow 与 Star。规格里 Hero 的周期标签/±%/柱上金额比 T8 验收句更严，实现时以规格为准。

## [S1] Problem

现有「分析报告」在 1.2.0 已具备结构化概览、交互饼图与本地规则洞察（`LocalInsightBuilder` + `LifestyleHeuristics`），但产品感知仍偏简陋：

1. **缺时间纵深与对比**：只呈现单期快照。`Report.prevIncomeCents` / `prevExpenseCents` 已落库却不展示；无近 N 期轨迹、无分类增减；`LocalInsightKind.TREND` 闲置。
2. **缺叙事与可读性**：洞察是并列卡片，无主次、无因果、无「本期故事」，像规则说明书而非复盘。
3. **缺可行动建议**：洞察多描述「是什么」，很少推「怎么办」（预算怎么调、哪类可压、下期目标）。
4. **视觉太素**：除饼图外缺少趋势/对比类图形，信息密度低。

旧报告 AI（`ReportPromptBuilder` + `ApiReportAnalyzer` + 备注抽样，已在 `34dd22d` 整条移除）之所以繁杂，根因是把**叙事生成**与**取数、网络失败态、密钥、重试**捆在同一条强依赖链上：为一段 ≤500 字解读扛了整条 OpenAI 兼容链路。直接复活旧路径会重蹈覆辙。

## [S2] Design

### 2.1 架构原则

- **本地事实核优先**：报告生成、叙事、建议全部本地完成，无网络即可 `SUCCESS`。
- **AI 降级为按需润色（Phase 2）**：仅在用户显式点击「深度解读」且已配置 API 时调用；输入为本地已算好的结构化事实 JSON；失败/超时静默，不影响报告主体。
- **可测可控**：叙事由显著性评分选句合成，纯函数可单测；不引入流式、对话、自动后台生成。

### 2.2 分层

| 层 | 职责 | 网络 | 新增/改造 |
|---|---|---|---|
| 取数层 | 近 N 期序列、环比、分类增减 | 无 | `ReportSeriesCalculator` + `LoadReportSeriesUseCase` |
| 事实评分器 | 候选事实按显著性打分排序 | 无 | `ReportFactScorer`（固定权重 + 环比幅度；极值二值新鲜度） |
| 叙事合成器 | Top 事实 →「总览→发现→建议」短文 | 无 | `ReportNarrativeBuilder` → `LocalInsightKind.STORY` |
| 规则建议器 | 预算节奏 / 分类上限 / 结余等 | 无 | `ReportAdviceBuilder` → `LocalInsightKind.ADVICE` |
| 可视化层 | 支出趋势条、环比差、分类增减榜、保留饼图 | 无 | `ReportScreen` / `ReportViewModel` |
| AI 深读（Phase 2） | 事实 JSON → 连贯复盘 | 有 | `DeepReadReportUseCase` + 薄 prompt |

### 2.3 时间纵深口径

- **序列长度 N = 6**：周报取近 6 个 ISO 周，月报取近 6 个自然月；年报暂不扩展。
- **环比**：本期 vs 上期的收入、支出、净结余变化。
- **分类增减榜**：按金额变化绝对值取 Top 3 增 / Top 3 减（含新增、消失分类）。
- **序列来源**：对流水按窗口实时聚合（`getWindowTotals` / `getExpenseByCategory`），**不新增表、不改 reports schema**。
- **序列不落库**：打开详情 / 选中报告时由 `LoadReportSeriesUseCase` **现算**；生成报告时亦可同算供评分，但不依赖 DB。刷新报告即刷新序列。

### 2.4 事实评分与叙事合成

**候选事实**（均来自本地已算结果）：环比、结构、预算、异常、生活模式、数据质量。

**评分（v1）**：固定类型权重 × 环比/占比幅度归一化；「新鲜度」仅二值（是否近 6 期支出极值）。取 Top 3–5 条。

**叙事结构**（本地短文，目标 80–180 字）：

1. 总览句：本期核心数字与一句定性
2. 主线发现：1–2 条最高分事实
3. 收尾建议：与主线相关的 1 条（若有）

**落库**：叙事写入 `LocalInsightKind.STORY`（一条）；建议写入 `LocalInsightKind.ADVICE`（0–2 条）；与既有细节洞察一并序列化进 `local_insights`。`analysisText` **专供 AI 深读**，不存本地叙事。

`LocalInsightBuilder` 保留为细节事实源。叙事**内容口径不变**；呈现改为大字号故事段（非 InsightCard）。

### 2.4.1 视觉主次重塑（v2 修订）

体验目标：打开详情即换代，不再与旧版「卡片 + 键值行」同构。数据口径零变更。

**信息层级（自上而下）**

1. **Hero 数字区**：净结余大字号主数字；收入 / 支出副行；环比徽章（净结余或支出 ±%）；周期标签。不用 InsightCard。
2. **本期故事**：`bodyLarge` 正文段落，无卡片描边；左侧 3dp 主色竖条或引号装饰；与 Hero 连成「导语」。
3. **建议条**：0–2 条行动提示（图标位 + 短标题 + 一行说明），样式为浅底圆角条，区别于洞察卡。
4. **近 N 期支出柱状图**：竖柱（非进度条），本期高亮；柱下短期键、柱上或侧金额；高度 ≥ 96dp。
5. **环比对比条**：收入 / 支出 / 净结余 三行；每行「标签 | 上期→本期 | ± 数值与迷你 delta 条」；涨跌用语义色（支出升/结余降偏警示色）。
6. **分类增减**：Top 增 / Top 减；每条「名称 + 金额差 + mini 横条长度按 |Δ| 归一」。
7. **支出分类占比**：保留交互饼图与列表、分类下钻。
8. **折叠细节洞察**：保持折叠；深度解读保持在末尾可选区。

**明确不做**：改叙事措辞算法、改取数、新 schema、动画转场库。

### 2.5 规则建议器

建议来自规则推导，不调用模型。每期 0–2 条，宁缺毋滥：

| 类型 | 触发示例 |
|---|---|
| 预算节奏 | 消耗显著快于日历进度 |
| 分类压缩 | 某分类环比大增或占比失衡 |
| 结构平衡 | 净结余为负 |
| 数据质量 | 未分类偏多 |

### 2.6 可视化层

- **Hero 数字区** + **故事段**（见 2.4.1）
- **近 N 期支出柱状图**（Canvas 竖柱，本期高亮；净结余双列二期再加）
- **环比对比条**（上期→本期 + delta 条 + 语义色）
- **分类增减 mini 条**（|Δ| 归一）
- **建议条**（行动提示样式）
- **保留**：交互饼图、分类下钻、折叠细节洞察

### 2.7 AI 深读（Phase 2）

- **触发**：详情内「深度解读」；仅 `NLTransactionParser.isAvailable()` 为真时展示。
- **输入**：薄 prompt + 结构化事实摘要（本期数字、环比、Top 分类、本地故事摘要）。
- **输出**：写入 `analysisText`；可再次生成覆盖。
- **失败**：toast / 静默；不影响报告主体状态机。
- **复用**：`NLTransactionParser.generate`；不新建后端。
- **明确不做**：自动调模型、设置开关页、流式、多轮对话、后台任务。

### 2.8 生成与标脏语义

- `GenerateReportUseCase`：结构化 + 本地叙事 + 建议 → 恒 `SUCCESS`。
- **`analysisText` 策略**：
  - `STALE` 只改 `status`，不动 `analysisText`
  - **结构化重生成成功后清空** `analysisText`（事实变了，旧深读作废）
  - 深读成功单独更新 `analysisText`，不走整单 regenerate
- 标脏仍统一 `STALE`（Add/Delete/Update/整理/合并）。
- `EnsureReportsUseCase` 补生成逻辑不变。

### 2.9 存储与兼容

- **零 schema 变更**：复用 `reports.local_insights`、`analysis_text`、`top_categories`。
- 新增 `LocalInsightKind.STORY` / `ADVICE`；未知 `k` 反序列化时跳过（既有实现）。
- 备份编解码已含 `analysisText` / `localInsights`，兼容。
- 历史行 `analysisText=null` 正常展示本地叙事与现算序列。

### 2.10 测试边界

- `:domain`：序列/环比/增减、评分、叙事、建议 — 纯函数单测
- `:domain`：`GenerateReportUseCase` 含 STORY/ADVICE；重生成清空 analysisText
- `:ui`：`ReportViewModel` 序列加载 / 深读加载态
- AI 深读：接口假件覆盖成功/失败/无配置

## [S3] Out of Scope

- 年度报告序列与同比
- 自动 AI 叙事、AI 开关设置页、流式/对话式解读
- 新数据表、reports schema 迁移
- 账户维度报告、预算自动调整
- 周报通知恢复（**已由** `report-allowance-review` **有意克制恢复**：周日 10:00、额度文案主轴；本规格交付期仍不包含）
- NL 记账 / 标签整理 / 标签合并链路改动
- 多语言、导出 PDF

## Tasks

- [x] T0: 修订规格闭合 STORY/ADVICE 落库、序列现算、analysisText 清空、T6 Phase2
- [x] T1: 近 6 期序列、环比、分类增减纯函数 + 测试
- [x] T2: 事实评分器与叙事合成器
- [x] T3: 规则建议器
- [x] T4: GenerateReportUseCase 编排整合
- [x] T5: 报告详情 UI
- [x] T6: AI 深读按需层（Phase 2，同迭代交付）
- [x] T7: test + ktlintCheck + assembleDebug 三绿
- [x] T8: 视觉主次重塑 — Hero 数字区、故事段、建议条、柱状图、环比对比条、分类 mini 条 — acceptance: 详情首屏不再呈现「卡片+键值行」同构；本期高亮柱、环比语义色、建议条样式可辨；饼图/下钻/折叠细节不回归 (covers: S2.4.1, S2.6)
- [x] T9: 重塑后门禁 — `test` + `ktlintCheck` + `assembleDebug` 三绿 — acceptance: 命令全绿并记录 (covers: S2.10; depends: T8)
