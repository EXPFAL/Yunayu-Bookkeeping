# Yunayu-Bookkeeping 产品需求文档（PRD v1）

定位：个人独占 · 学生向 Android 原生记账应用
三大核心诉求：学生财务自律 / 学业生活融合 / 极简高效记录

## 一、功能矩阵

### P0（MVP 必做）
1. 3秒极速记账
   - 痛点：课间/食堂排队无法打开复杂表单
   - 方案：首页悬浮按钮 + 数字键盘直输金额 → 自动匹配最近常用分类
   - 技术要点：Compose 自定义键盘；Room 查询最近7天高频分类缓存
2. 每月预算看板
   - 痛点：生活费按月分配易超支
   - 方案：设置每月预算 → 自动拆解周可用额度 → 进度条预警
   - 技术要点：DataStore 预算配置、(剩余÷剩余天数)×7、Flow 实时计算
3. 学业关联标签
   - 痛点：买书/打印/培训等支出难归类，与生活费混淆
   - 方案：内置「学习/社交/生活/娱乐」四大类 + 自定义子标签（教材/考证/实习）
   - 技术要点：标签树结构存 Room；支持拖拽排序

### P1（MVP 验证通过后做）
4. 自然语言记账
   - 方案：输入"昨天图书馆买咖啡28"→ 解析为 {时间:昨日, 金额:28, 类别:学习-饮品}
   - 技术要点：在线 OpenAI 兼容 API 完成自然语言→交易字段解析（经决策调整：端侧 LiteRT-LM 路线真机实测 NO-GO 后转在线，详见 SCAFFOLD §13）
   - 取舍：换取模型能力与交付速度，放弃离线，引入记账数据上云与按量计费
   - 未来离线/端侧选项：llama.cpp + GBNF 约束解码（详见 SCAFFOLD §13）
5. 消费复盘周报
   - 方案：每周日自动生成 Top3 支出类别 + 预算执行率 + 异常消费提醒
   - 技术要点：Room 聚合查询；Markdown 渲染；系统通知推送
6. 收入记录与持有资金
   - 方案：QuickAdd 数字模式切换收/支；首页「持有资金」卡片 = 累计收入 − 累计支出（负值「已超支」）；最近记录收入「+」展示
   - 技术要点：AddTransactionUseCase 默认参数 type=EXPENSE 兼容既有调用；持有资金 DAO 单查询净结余
   - 口径：收入计入持有资金、不计入月支出统计；不引入账户/期初余额
7. 收支管理界面（已随本次迭代交付）
   - 方案：首页新增「收支管理」入口，进入全屏管理页；筛选 = 标签多选（跨父子类组合）+ 时间快捷项（全部/近7天/近30天/本月）+ 备注关键词模糊搜索，三条件组合即时刷新；单条删除 + 二次确认 + Snackbar 反馈
   - 技术要点：combine → 备注防抖(300ms) → flatMapLatest 订阅 observeFiltered；DeleteTransactionUseCase 删除成功后置 FAILED，复用报告页手动重试
   - 口径：本期仅删除、不含编辑、不预留编辑入口；± 为展示符号，不改 amountCents 存储口径
   - 验收要点：标签多选跨父子类生效；时间快捷项即时过滤；备注关键词模糊命中；删除需二次确认且成功/失败均有反馈
8. 收入分类推荐（已随本次迭代交付）
   - 方案：分类推荐按收支方向分别统计，收入记账展示收入语境标签；收入历史为空回退根标签补足；不新增收入专属根类（P3 增强）
   - 技术要点：getRecentFrequentTags SQL 加 type 过滤（参数绑定，复用 (occurred_at,type) 复合索引，零 schema 变更）；TagRepository.getRecentUsedTags / GetRecentCategoriesUseCase 逐层加 type（无默认值）；QuickAddViewModel setType 联动刷新（refreshJob 取消重放防竞态、NL 模式刷新不触碰 selectedTagId/nlTagId）
   - 验收要点：切换收/支方向即时刷新推荐；收入历史为空回退根标签；收入不展示支出语境标签

### P2（暂缓，日常使用率验证通过前不开发）
9. 课程表联动（iCal 解析；上课日/周末/假期维度分析）
10. 兼职/奖学金追踪（收入标签组 + 攒钱目标进度条）
11. NL 备注兜底
   - 方案：NL 链路每条记录必有备注（Prompt 强指令 + 本地启发式剥离兜底，≤8 字）
   - 技术要点：NlPromptBuilder 强 note 指令 + NlNoteFallback 本地剥离（日期→金额→标签短语→填充词→截断≤8→≥2字校验），仅 LLM 未输出 note 时生效
12. 月度/年度报告
   - 方案：打开应用补生成上月月报/1 月补上年年报（封顶 2 份）；报告页列表/详情/失败手动重试
   - 技术要点：LLM ≤500 字分析走现有 OpenAI 兼容 API 通道；reports 表 + 幂等补生成
13. 记录展示优化（已随本次迭代交付）
   - 方案：最近记录行新增备注次级行（时间下方、非空才显示、最多 2 行省略）；金额统一方向化符号——收入「+金额」主色、支出「-金额」常规色（ASCII 符号）
   - 技术要点：RecentTransaction 加 note（默认 null）；首页与收支管理页共用公共行 TransactionRow；formatSignedCents / formatTime 上提 ui/util 公共纯函数
   - 口径：± 仅展示层，amountCents 恒为正数不改存储；备注非空且非空白才显示
14. 标签选择折叠优化（已随本次迭代交付）
   - 方案：父类分组头可点击展开/收起 + 箭头指示 + 分组分隔线；根标签自身行（self 行）独立可点选；子类默认折叠、仅展开时渲染
   - 技术要点：共享折叠树组件 TagTreeList（rememberSaveable 展开集合）；快速记账「更多分类」弹层改用之；数字与 NL 模式共用
   - 迭代增强（已随本次迭代交付）：父类分组头 titleSmall→titleMedium(16sp)、颜色 onSurface→onSurfaceVariant，与子类 bodyMedium(14sp)/onSurface 形成字号+颜色双重层级（选中态主色+Check、分隔线、子类 32dp 缩进不变）；上滑后展开/收起滚动位置跳变修复——显式 rememberLazyListState + toggle 前记录 firstVisibleItemIndex/offset + LaunchedEffect(expandedRootIds) scrollToItem 恢复 + heightIn(max=420.dp) 稳定 ModalBottomSheet 尺寸，QuickAdd 单选与收支管理多选两场景共用
15. 无匹配标签应对（已随本次迭代交付）
   > 已被本次迭代（PRD §17「NL 未命中直通未分类」）取代，相关组件已删除。
   - 方案：未分类兜底（tagId 可空落库现状固化 + 显式用例）+ 弹层新建标签（QuickAddSheet TagPickerSheet「新建标签」入口 → QuickAddNewTag 表单：命名+选根类+创建即选中落库）+ AI 辅助决策（NL 未匹配短语自动建议卡、数字模式新建表单「AI 推荐所属根类」仅预填）
   - 技术要点：新建走 TagRepository.addSubTag，重名 DuplicateTagNameException→「同名标签已存在」不崩；AI 复用 NLTransactionParser.generate 接缝，新增 domain 纯函数链 TagSuggestionPromptBuilder / TagSuggestionOutputParser + SuggestNewTagUseCase（任何失败返回 null 静默降级、20s 超时）；AI 仅建议、创建必须用户确认、绝不自动落库
   - 口径：NL 建议确认后「创建并使用」一次完成创建+选中+落库，拒绝降级未分类/手动；refreshSuggestedTags 增 preselectTagId 防新建后选中被异步覆盖
   - 验收要点：无匹配标签仍可正常记账（未分类）；重名新建不崩溃且提示清晰；AI 建议失败静默降级不阻塞记账

### 标签体系与 AI 治理迭代（已随本次迭代交付）
16. 独立收入标签体系（已随本次迭代交付）
   - 方案：预置「收入」根类 + 6 精简子标签（生活费/还款/AA收款/理财收益/兼职经营/其他收入），收入与支出两套体系完全隔离；收入根在标签管理页自然展示且只读
   - 技术要点：运行时幂等种子化（EnsureIncomeTagsUseCase 挂 Application.onCreate 第三协程，仿 ensureReports；不走 migration、不改 seedCallback——后者仅新建库生效）；addRootTag 白名单 + 内存判重（SQLite 唯一索引对 NULL parent 不生效的空洞）；收支隔离——分类推荐回退 type 感知、QuickAdd 选择层按 type 过滤（收入仅收入体系、支出四根类不变）、收支管理页筛选树保持全量
   - 验收要点：首启与存量库均补齐收入根与种子子标签；收入记账仅见收入体系标签；收入根只读、不可改名/删除/拖拽
17. NL 未命中直通未分类（已随本次迭代交付）
   - 方案：NL 命中已有标签自动挂载保留不动；未命中直接 tagId=null 落库为「未分类」，零打断（替代上轮建议卡）；移除数字模式新建表单与单条 AI 建议链
   - 技术要点：删除 8 文件（SuggestNewTagUseCase / TagSuggestionPromptBuilder / TagSuggestionOutputParser / TagSuggestion 模型 + 3 测试 + QuickAddNewTag UI）；保留瘦身版 createSubTag / addSubTag 链路与 NLTransactionParser.generate 接缝，供整理复用
   - 验收要点：未命中仍可正常记账且无多余弹窗打断；命中自动挂载不回归
18. 整理入口与批量分类（已随本次迭代交付）
   - 方案：收支管理页顶部「整理 N」按钮；未分类记录分批送 LLM 分类，逐条接受/修改/拒绝，确认后单事务批量应用
   - 技术要点：未分类聚合 tag_id IS NULL 走既有索引；分批 25 条串行（备注优先 + 金额/时间辅助，收入记录强制收入体系白名单）；新建标签走 addSubTag（重名复用改挂载）；批级超时 40s + 重试 1 次、Job 句柄 + 批边界取消 + 可重入续整理；applyTagAssignments 单事务；整理后覆盖窗口报告按去重 occurredAt 循环置 FAILED（ReportRepository 零接口改动）
   - 验收要点：进度 x/y；部分成功不丢数据（失败条目保留未分类可重试）；无 API 明确提示不崩
19. 标签整合机制（已随本次迭代交付）
   - 方案：全标签库适用；FindMergeCandidatesUseCase 预筛 + LLM 批量判定重复对；入口 = 标签管理页「整合」+ 整理完成后 best-effort 检测提示；确认后合并
   - 技术要点：预筛非根/叶子/countA+countB>3/同根优先/上限 30 对；LLM 每 15 对/请求三选判定；仅叶子可合并（tags 子树 CASCADE 语义约束）；TagMergeExecutor 单事务先迁移 transactions.tag_id 再删除冗余标签；合并后报告标脏
   - 验收要点：LLM 仅建议、合并必须用户确认

## 二、明确砍掉的功能（scope 红线，实现任何一项即视为违规）
- 多成员/共享记账（个人使用无需权限体系）
- 商家/项目管理（无 B 端对账需求）
- 人情礼簿/物品收纳（偏离财务主线）
- 多币种/汇率换算（国内校园场景几乎不用）
- 发票助手/财务云盘（无报销场景）
- Web 端/小程序（专注 Android 原生）

## 三、学生专属设计细节
1. 月度预算管理
   - 预算按月滚动，跨月自动按新月重新计算
2. 防冲动消费机制（温和提醒，非强制拦截）
   - 单笔超阈值（如 ¥100）弹确认："这笔属于必要支出吗？"
   - 娱乐类支出达周限额 80% 时，记账按钮变橙色警示
3. 隐私强化
   - 应用锁支持指纹/图案，但不请求通讯录/短信权限
   - 数据库加密密钥绑定设备 Secure KeyStore，卸载即焚
   - 导出文件不含设备标识符
4. 学业友好交互
   - 深色模式默认跟随系统
   - 记账完成震动反馈 ≤ 30ms
   - 文案避免说教感（用"本周还剩¥320"代替"你已超支！"）

## 四、MVP 纪律与验证指标
- MVP 仅实现 P0 三项 + P1 自然语言记账
- 用 2 周验证"3秒记账 + 每月预算"是否真正解决痛点
- 若日常使用率 < 80%，优先优化交互而非加新功能
- 不追求功能完备性

## 五、建议 ISSUE 拆分（供 Sprint 规划参考）
- feat(core): monthly-based budget engine with weekly auto-calculation
- feat(ui): floating quick-add button with recent-category prediction
- feat(ai): online OpenAI-compatible API for natural language transaction parsing
- feat(report): weekly consumption review with anomaly detection
- refactor(data): category schema for student-specific tags
