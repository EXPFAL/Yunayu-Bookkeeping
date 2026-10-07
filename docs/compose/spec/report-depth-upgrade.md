---
feature: report-depth-upgrade
status: implemented
updated: 2026-10-07
branch: cursor/v1-1-0-backup-ci-d984
commits:
---

# 分析报告升级：本地事实核 + 按需 AI 深读

## Report

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

`LocalInsightBuilder` 保留为细节事实源；UI：故事 → 建议 → 趋势/环比/增减榜 → 概览/饼图 → 折叠细节。

### 2.5 规则建议器

建议来自规则推导，不调用模型。每期 0–2 条，宁缺毋滥：

| 类型 | 触发示例 |
|---|---|
| 预算节奏 | 消耗显著快于日历进度 |
| 分类压缩 | 某分类环比大增或占比失衡 |
| 结构平衡 | 净结余为负 |
| 数据质量 | 未分类偏多 |

### 2.6 可视化层

- **近 N 期支出趋势条**（Canvas 小条；净结余双列二期再加）
- **环比差**：收入/支出/净结余 +/- 数字
- **分类增减榜**：Top 增 / Top 减
- **保留**：概览四行、交互饼图、分类下钻
- **叙事卡 / 建议卡 / 折叠细节洞察**

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
- 周报通知恢复
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
