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

### 账户体系与主题美化迭代（已随本次迭代交付）
20. 账户体系（已随本次迭代交付）
   - 方案：预置「微信 / 支付宝 / 银行卡」三账户；记账时「未指定 + 各账户」单选；首页「持有资金」按账户分组展示 + 总计；收支管理页新增账户筛选；首页新增「管理账户」全屏（增 / 改名 / 删除）
   - 技术要点：schema v4→v5 新增 accounts 表（name 唯一索引）+ transactions.account_id 可空外键（ON DELETE SET NULL）+ 索引；预置账户双路径种子化（迁移 INSERT OR IGNORE + 启动 EnsureAccountsUseCase 幂等补齐）；DataStore account_prefs 记忆 lastUsedAccountId（预选校验 id ∈ 账户列表，否则回退未指定）
   - 口径：总资金口径恒等——各账户余额之和 + 未指定账户 = 现有净结余（observeHeldCents SQL 零改动）；报告口径与账户无关（删除 / 改名不标脏报告）
   - 验收要点：存量库升级补齐三账户；删除账户后交易归「未指定」（FK SET NULL）；重名 DuplicateAccountNameException 提示不崩；各账户余额求和等于总持有资金
21. 视觉体系（已随本次迭代交付）
   - 方案：以卡通图为主题参考（仅配色 / 风格，非布局），樱粉 + 奶油暖调品牌色，浅 / 深两套主题；卡片大圆角、金额数字加粗等宽
   - 技术要点：Color.kt 浅 / 深品牌色板（浅色 background #FFF8F6、primary 暖棕 #7A4F4F、secondary #E8879C、tertiary #7FA37A；深色 background #2B2326、primary #F2B8C6，整体替换 M3 默认深紫）；Theme.kt 以 M3 默认基底覆品牌 token；HeldFundsCard 品牌渐变背景（浅两色 / 深三色随系统深浅分支）
   - 口径：仅主题层改动，零业务逻辑；error 保留语义红；onSecondary / onTertiary 未覆写（当前全仓零调用）
   - 验收要点：浅 / 深两套主题均可用；金额数字等宽对齐；无业务逻辑 / 测试断言改动

### 标签收敛与发布工程迭代（已随本次迭代交付）
22. 标签父类选项收敛（已随本次迭代交付）
   - 背景：用户原话「不喜欢笼统标签」——父类粒度太粗，点选后记账归类仍不精确，期望只落到具体子标签
   - 方案：TagTreeList 移除根自身可点选 self 行，父类仅作分组头（折叠/展开），不可被选为标签；三宿主（快速记账选择层 / 收支管理筛选层 / 整理选择弹层）同时生效；历史已挂父类标签的交易照常显示，仅不再提供新选择入口
   - 推荐契约：GetRecentCategoriesUseCase 推荐仅叶子——recent 过滤非叶子、支出补足 = 各支出根子标签平铺（排除收入根子树）、收入补足 = 仅收入子标签（不含收入根）、无子根按叶子处理、getChildren 失败降级（recent 原样保留 + 补足为空不崩）；叶子判定基于「拥有子标签」而非 parentId 字段
   - 验收要点：父类仅可折叠/展开、不可选中；三宿主同时生效；历史父类标签交易照常展示；快捷推荐仅返回叶子
   - 测试：GetRecentCategoriesUseCaseTest 全量重写 11 用例
23. 版本号（已随本次迭代交付）
   - versionName 0.1.0 → 3.25，versionCode 保持 1，aapt 验证生效
24. App 图标（已随本次迭代交付）
   - 方案：以用户提供的卡通图（粉色圆脸表情，与品牌主题同源）替换启动器图标——mipmap 全密度 ic_launcher / ic_launcher_round + adaptive icon（background = 品牌浅粉 #FFF8F6 颜色资源、foreground = 提取的表情线条层，因源图脸体与背景同色采用「线条前景 + 浅粉底色」方案）；AndroidManifest 新增 icon / roundIcon 引用；minSdk=26 真机恒走 adaptive
25. 工程卫生（已随本次迭代交付）
   - .gitignore 追加 androidtest_assemble.log / gate_run.log（门禁/构建日志不入库）

### 交易编辑 / 期初余额 / 转账迭代（已随本次迭代交付）
26. 交易编辑（已随本次迭代交付）
   - 方案：收支管理页行点击进编辑弹层；可编辑金额 / 类型 / 备注 / 标签 / 账户，时间不可编辑；保存后覆盖窗口的报告置 FAILED 可手动重试
   - 技术要点：TransactionDao @Update 整行覆盖 + getById 保留 createdAt；UpdateTransactionUseCase 校验金额>0 / 类型合法 / id 非 0；EditTransactionSheet 复用 QuickAdd 形态（金额键盘 / 收支切换 / 备注 / 标签 / 账户 / 保存+取消）；编辑保存后报告标脏复用删除单点循环
   - 口径：编辑仅限金额 / 类型 / 备注 / 标签 / 账户，时间字段本期不可编辑；取消编辑不产生任何写入
27. 账户期初余额（已随本次迭代交付）
   - 方案：账户可设期初余额；持有资金口径 = 含期初（总资金 = Σ账户余额 + 未指定净额 = 期初总和 + 累计净结余）
   - 技术要点：schema v6 accounts 加 initial_balance_cents（DEFAULT 0）；账户余额 = 期初 + 交易净额 + 转账净额；observeHeldCents = 期初总和 + 交易净结余
   - 口径：总资金恒等——各账户余额之和 + 未指定账户 = 期初总和 + 累计净结余；期初余额非负
28. 转账（已随本次迭代交付）
   - 方案：快速记账弹层 TypeToggle 三段（支出 / 收入 / 转账）；转账不计收支统计 / 预算 / 报告 / 推荐，仅影响账户余额（总资金守恒）；转账支持录入 / 查看 / 删除，编辑不做；删除账户级联删除其转账（确认弹窗含转账数提示）
   - 技术要点：独立 transfers 表（双 FK ON DELETE CASCADE + 三索引）；RecordTransferUseCase 仅注入 TransferRepository，不触发报告标脏；收支管理页转账 Tab 查看 / 删除
   - 口径：转账在账户间守恒不改变总额（Σ转出 = Σ转入）；NL 不支持转账

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

## 六、九项优化迭代（已随本次迭代交付）

### 29. 模式切换改名「手动记/自动记」
- 痛点：用户误将「数字键盘/自然语言」理解为纯模式切换，实为 UI 命名问题
- 方案：数字键盘模式 → 「手动记」、自然语言模式 → 「自动记」，TypeToggle 文案同步更新
- 技术要点：QuickAddViewModel 无逻辑变更，仅 UI 层文案调整
- 验收要点：切换模式文案即时更新，功能行为不变

### 30. 手动记可选备注
- 痛点：手动记账时无法输入备注信息，记录缺乏上下文
- 方案：手动记模式新增备注输入框（位于金额键盘下方），可选填写
- 技术要点：AddTransactionUseCase 新增 note 参数（默认 null），QuickAddSheet NumberInputSection 新增 OutlinedTextField 备注输入，落库时透传 note 字段
- 口径：备注为可选字段，空值落库为 null；自动记模式备注由 LLM 保障（§15 NL 备注兜底）
- 验收要点：手动记可输入备注并正确保存；不输入备注时落库 null；自动记模式不受影响

### 31. 记一笔保存后自动滚动到最新记录
- 痛点：保存后视口未同步，用户需手动滑动查看新记录
- 方案：HomeViewModel 新增 Saved 事件，HomeScreen 收集后自动滚动到列表顶部
- 技术要点：sealed interface HomeEvent + Channel(BUFFERED) 事件机制；LaunchedEffect 收集 listState.animateScrollToItem(0)；仅新增记账触发，编辑弹层不触发
- 验收要点：新增记账后自动滚动到最新记录；编辑保存不触发滚动；列表无记录时不崩溃

### 32. 首页入口收纳至侧栏（汉堡菜单）
- 痛点：首页入口过多，视觉分散
- 方案：引入 ModalNavigationDrawer + TopAppBar 汉堡菜单，收纳 5 个全屏入口：收支管理 / 管理标签 / API 管理 / 报告 / 管理账户
- 技术要点：rememberDrawerState + rememberCoroutineScope；DrawerContent 私有 Composable；NavigationDrawerItem 点击设置 FullScreen 枚举 + drawerState.close()；TopAppBar 汉堡图标
- 决策理由：抽屉式侧栏（vs 底部导航）更适合低频配置入口，首页保持简洁
- 验收要点：汉堡菜单可打开/关闭；5 个入口功能正常；FAB 与侧栏无遮挡冲突

### 33. 自动记输入框键盘遮挡适配
- 痛点：自动记输入框被软键盘遮挡，无法正常输入
- 方案：NlParseSection 输入框适配 WindowInsets，键盘弹出时自动上移
- 技术要点：Modifier.imePadding() + 焦点管理，仅自动记模式生效
- 验收要点：键盘弹出时输入框不被遮挡；手动记模式不受影响

### 34. 记一笔按钮右侧垂直居中
- 痛点：FAB 默认右下角位置遮挡最近记录金额展示
- 方案：FAB 从 Scaffold floatingActionButton 移至 Box 内容区，Alignment.CenterEnd + padding(end = 16.dp)
- 技术要点：Box 包裹内容区 + Modifier.align(Alignment.CenterEnd)；TopAppBar 增加后 FAB 随 Box 一起偏移，不重叠
- 决策理由：右侧垂直居中（vs 底部上移）避免遮挡最新记录金额
- 验收要点：FAB 位于右侧垂直居中；不遮挡列表内容；侧栏打开时不冲突

### 35. 报告页新增「本周」周报 + 分类开销饼状图
- 痛点：仅有月度/年度报告，缺乏短期消费概览
- 方案：ReportPeriodType 新增 WEEKLY 枚举，报告页新增「本周」选项 + Canvas 自绘饼状图（Top5 + 其他）
- 技术要点：周窗口 = 本周一 00:00 至下周一 00:00 半开区间；期键用 ISO weekBasedYear（跨年周正确性：2027-01-01→2026-W53、2029-12-31→2030-W01）；byKey 反推锚点 1 月 4 日；预生成上周周报置于月/年之后；标脏机制经 window_start_ms/window_end_ms 范围条件自动覆盖周报；reports 表 period_type 字符串存储零 schema 变更
- 饼状图：Canvas 自绘（不引第三方库）、固定色板 + 稳定哈希、Top5 +「其他」桶闭合 360°
- 验收要点：「本周」选项可切换；周报数据正确；饼状图显示 Top5 分类 + 其他；跨年周正确处理

### 36. 冷启动过渡画面
- 痛点：应用启动时白屏，体验割裂
- 方案：core-splashscreen 1.0.1 实现冷启动过渡画面，居中图标 + 背景与图标边缘一致
- 技术要点：Theme.Yunayu.Splash（#FED1D0 浅粉）；installSplashScreen 先于 super.onCreate；图标圆形遮罩风险留痕（冒烟确认点）
- 已知限制：圆形遮罩在部分设备可能裁剪图标边缘，需真机冒烟确认
- 验收要点：冷启动显示过渡画面；背景色与图标协调；无白屏闪烁

### 37. 标签管理页滑动流畅优化
- 痛点：标签管理页滑动卡顿，展开/收起时位置跳变
- 方案：LazyColumn 性能优化 + 滚动位置保持
- 技术要点：derivedStateOf 减少重组；Lambda 稳定性优化；rememberLazyListState 显式管理；toggle 前记录 firstVisibleItemIndex/offset + LaunchedEffect(expandedRootIds) scrollToItem 恢复；heightIn(max=420.dp) 稳定 ModalBottomSheet 尺寸
- 验收要点：滑动流畅无卡顿；展开/收起后滚动位置保持；动画保留

### 38. 评审修复留痕
- ISO 周年键：周报告期键改用 ISO weekBasedYear（DateTimeFormatter.ofPattern("YYYY-'W'ww")），修复跨年周（2027-01-01→2026-W53、2029-12-31→2030-W01）
- jan4 锚点：byKey 反推锚点从 1 月 1 日改为 1 月 4 日（ISO 周定义：1 月 4 日所在周为该年第一周）
- 饼图其他桶：total - top5Sum 确保闭合 360°，避免浮点误差导致缺口
- remember(tag)：TagTreeList 记忆展开集合，避免重组丢失状态
- ensureWeekly 顺序：周报预生成置于月/年之后，不阻塞既有补生成逻辑
- HomeMainContent 拆分：HomeScreen 主内容区抽取为私有 Composable，降圈复杂度
