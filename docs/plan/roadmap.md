# 分步实现路线图（Roadmap）

标记规范：`- [ ]` 未完成，`- [x]` 已完成。每阶段末尾有**验收标准**，全部满足才进入下一阶段。

---

## P0 · 工程脚手架与工具链

目标：把当前 Java/View 空工程改造为 Kotlin + Compose 可编译工程，并跑通一个空单测。

- [ ] 在 `gradle/libs.versions.toml` 增加版本与依赖别名：Kotlin、Compose BOM、Hilt、Coroutines、Retrofit/OkHttp、kotlinx-serialization（或 Moshi）、Room、Navigation-Compose、DataStore、security-crypto、Coil、Jsoup、Readability4J（JVM 正文提取）、junit4、kotlinx-coroutines-test、mockk（或 mockito-kotlin）、Robolectric（可选）。
- [ ] 修改 `app/build.gradle`：应用 Kotlin/Compose/Hilt/kapt(或 ksp)/kotlinx-serialization 插件；开启 `buildFeatures.compose`；`compileOptions`/`kotlinOptions` 设为 Java 17；保留 `minSdk 26`。
- [ ] 将 `MainActivity` 迁移为 Kotlin + `ComponentActivity` + Compose `setContent`（占位 UI 即可）。
- [ ] 配置包结构：`ui/ data/{reader,ai,resume,local,repository} di/ share/`（见项目规划 6.3）。
- [ ] 配置 Hilt：`@HiltAndroidApp` Application、基础 `@Module`。
- [ ] 在 `app/src/test` 写一个 `SanityTest`（断言 `true`）并通过 `./gradlew testDebugUnitTest`。

**验收标准**
- `./gradlew assembleDebug` 编译通过。
- `./gradlew testDebugUnitTest` 通过（含 SanityTest）。

---

## P1 · 领域模型 + AI 分析流水线（★测试先行，核心能力）

目标：**不联网**实现并测试"抽题 → 相关性筛选 → 作答"三步逻辑。真实 DeepSeek 调用放到 P5。

### P1.1 领域模型
- [x] 定义模型：`ResumeProfile`、`ExtractedQuestion`、`RelevanceResult`、`AnsweredQuestion`（`RawPost` 以 P2 的 `PostContent` 代替）。
- [x] JSON 解析：用 `kotlinx-serialization-json` 运行时 `JsonElement` 动态解析（无需 @Serializable/编译器插件），共享工具 `JsonSupport`。

### P1.2 AiClient 抽象（可 mock）
- [x] 定义接口 `AiClient { suspend fun chat(messages): String }`（返回原始文本）。
- [x] 提供 `FakeAiClient`（测试用，按输入返回预置夹具响应，并记录消息）。
- [x] 定义 `AiException`（解析/调用异常）。

### P1.3 三步能力（每步 = 构造 Prompt + 解析响应）
- [x] `QuestionExtractor`：`buildMessages(postText)` + `parse(raw) -> List<ExtractedQuestion>`。
- [x] `RelevanceMatcher`：`buildMessages(questions, profile)` + `parse(raw, questions) -> List<RelevanceResult>`（0-100 分+理由+命中技能），按阈值过滤/排序。
- [x] `AnswerGenerator`：`buildMessages(relevant, profile)` + `parse(raw, relevant) -> List<AnsweredQuestion>`（答案/难度/要点，带回相关性）。
- [x] `AnalysisPipeline`：编排三步（抽题→筛选→作答）。长文本分块合并留待后续。

### P1.4 解析健壮性
- [x] JSON 解析容错：处理 LLM 输出被 ```` ```json ```` 包裹、尾随文本、字段缺失等情况（用 `kotlinx-serialization-json` 运行时 API，无需编译器插件）。
- [x] 解析失败时抛出可读的 `AiException` 而非崩溃。

### P1.5 单元测试（必须全绿）
- [x] `QuestionExtractorTest`：夹具响应/```json```包裹/裸数组/空题过滤/空结果/畸形抛异常/Prompt 注入/端到端 extract。
- [x] `RelevanceMatcherTest`：评分解析、阈值过滤、排序、index 映射与越界忽略。
- [x] `AnswerGeneratorTest`：答案/难度/要点解析、相关性带回、空输入短路。
- [x] `AnalysisPipelineTest`：`FakeAiClient` 串起三步；覆盖"无题目""无相关题目""正常"三种场景。
- [x] Prompt 快照测试：断言要求 JSON、简历上下文与题目序号注入。
- [x] 解析异常用例：畸形 JSON → 抛 `AiException`。

**验收标准**
- 上述所有单测在 `./gradlew testDebugUnitTest` 下全绿。
- 全程无真实网络请求、无真实 API Key。

---

## P2 · 内容读取（★测试先行）

目标：先用 **JVM 可测的静态解析**（Jsoup + Readability4J）从 HTML 提取正文并测试；WebView 动态渲染作为降级适配器，最后实现。

### P2.1 夹具捕获
- [ ] 捕获牛客链接页面，保存 `app/src/test/resources/fixtures/nowcoder_53245626677297352.html`。
- [ ] 解析小红书短链 `xhslink.cn/o/6Gz0nDGZxAE` 的重定向真实 URL；尝试捕获正文 HTML 保存为 `xhs_6Gz0nDGZxAE.html`。
- [x] 小红书静态抓取拿不到正文（JS 渲染 + 反爬）→ 结论：`XHS: 静态不可用，采用 WebView 登录态方案`。读取方案见 [`xhs-reading.md`](./xhs-reading.md)（参考 MediaCrawler 思路）。

### P2.2 静态解析层（JVM 可测）
- [x] `HtmlContentExtractor`：输入 HTML 字符串 → 输出 `PostContent(title, text)`（Readability4J + Jsoup 兜底）。
- [x] `UrlResolver` / `OkHttpUrlResolver`：跟随重定向展开短链。
- [x] `HtmlFetcher` / `OkHttpHtmlFetcher`：静态抓取（接口注入，便于测试）。
- [x] `ContentReader` 门面：`展开短链 → 静态抓取 → 降级 WebView → 手动粘贴` 决策逻辑（WebView 以接口 `DynamicContentReader` 注入）。

### P2.3 单元测试（必须全绿）
> 已实现，待在联网环境运行 `./gradlew :app:testDebugUnitTest` 验证（本机无法联网构建）。
- [x] `HtmlContentExtractorTest`：代表夹具 → 断言提取正文含面试题关键片段、去除导航/页脚、JS 空壳判不可用。
- [x] `UrlResolverTest`：MockWebServer 模拟 301/302/多段重定向 → 断言短链正确展开、失败回退原 URL。
- [x] `ContentReaderTest`：静态成功 / 短链展开后抓取 / WebView 兜底 / 手动粘贴 的降级决策正确。

### P2.4 WebView 动态读取适配器（设备侧，后置）
- [ ] 通用 `WebViewContentReader`：离屏 WebView 加载 URL，`onPageFinished` 后注入 `readability.js`（assets）提取正文；超时兜底 `document.body.innerText`。
- [x] 小红书正文提取（静态方案，见 [`xhs-reading.md`](./xhs-reading.md) 第 7 节）：
  - [x] `XhsNoteExtractor`（纯函数）从静态 HTML 的 `__INITIAL_STATE__` 提取笔记 title/desc、去评论/推荐噪声 + JVM 单测；已用真实链接验证。
  - [x] 接入 `HtmlContentExtractor`（小红书域名优先，失败回退 Readability/手动粘贴）。
  - [ ] （后备）`XhsWebViewReader : DynamicContentReader` + 登录态：仅当遇到需登录/静态拿不到的笔记再实现。
- [ ] instrumented 测试或手动验证（不纳入 JVM 单测门槛）。

**验收标准**
- P2.3 所有 JVM 单测全绿。
- 牛客夹具能稳定提取正文；小红书结论已明确记录（采用 WebView 登录态方案，见 xhs-reading.md）。

---

## P3 · 本地存储 + 设置（BYOK）

目标：把简历、帖子、题目、答案、进度、设置（含加密 Key）持久化。

- [ ] Room 实体与 DAO：`Resume`、`ImportedPost`、`Question`、`StudyRecord`（见项目规划第 8 节）。
- [ ] `DataStore` 保存偏好；`EncryptedSharedPreferences` 保存 DeepSeek API Key、provider、模型名、相关性阈值。
- [ ] `Repository` 聚合 reader + ai + resume + local。
- [ ] 单测：DAO 读写（Room in-memory / Robolectric）；`SecureKeyStore` 存取（可抽象接口 + fake 测逻辑）。

**验收标准**
- DAO 与设置存取单测通过。
- 能以编程方式加密保存并读回 API Key（不明文落盘）。

---

## P3.5 · 端侧记忆管理 + 会话（★测试先行）

目标：端侧三层记忆 + 多职业档案 + 事实取代机制，支持"方向切换/多简历"，并持久化多轮会话。详见 [`memory.md`](./memory.md) 与 [`ai-framework.md`](./ai-framework.md)。真实 DeepSeek 调用仍在 P5。

### P3.5.1 记忆存储与管理
- [ ] Room：`CareerProfile`、`MemoryFact`、`MemoryEvent` 实体与 DAO；`Resume` 支持多份。
- [ ] `MemoryManager`：`remember`（含 supersede 冲突处理）、`recall`（scope 过滤+排序+预算）、`decay/prune`、`switchProfile`。
- [ ] `ResumeMemoryExtractor` / `ConversationMemoryExtractor`（经 `AiClient`）产出事实候选。

### P3.5.2 会话与对话记忆
- [ ] Room：`Conversation` / `ChatMessage` / `MemorySummary` 实体与 DAO。
- [ ] `TokenEstimator`（CJK/英文启发式）。
- [ ] `ChatMemory` 三策略：`MessageWindowMemory` / `TokenWindowMemory` / `SummarizingMemory`。
- [ ] `ContextAssembler`：system + 激活档案事实(recall) + 长期摘要 + 窗口历史 + 当前输入。
- [ ] `ConversationRepository`：会话 CRUD、追加消息、加载上下文。

### P3.5.3 单元测试（必须全绿）
- [ ] 取代逻辑：同 key 新值 → 旧 `SUPERSEDED`、新 `ACTIVE`、`supersedesId` 链、历史可查。
- [ ] 方向切换：切/建 profile 后 `recall` 仅返回该 profile + GLOBAL 的 ACTIVE 事实，旧方向不泄漏。
- [ ] 相关性随记忆变化：Java 后端 vs Android 激活档案下，`ContextAssembler`/`recall` 输出差异。
- [ ] 抽取+冲突：从"改投安卓"文本抽取 → `remember` 正确取代 `target_role`（`FakeAiClient` 夹具）。
- [ ] 衰减/修剪：超额/过期事实被降权或清理。
- [ ] 会话窗口/摘要/上下文顺序正确；会话 CRUD 持久化正确（in-memory Room）。

**验收标准**
- 上述 JVM 单测全绿；全程不联网、无真实 Key。

---

## P4 · UI 与后台运行（Compose）

目标：打通 导入 → 简历 → 题目 → 刷题 的可用界面，并支持"分享后台异步处理"。导航与后台设计详见 [`ui-and-runtime.md`](./ui-and-runtime.md)。
> 刷题的具体交互机制（揭示方式、自评、复习队列）待后续单独探讨，此处先占位。

### P4.1 导航与页面骨架
- [x] 底部 **3 Tab**：导入（首页）/ 题库&刷题 / 我的；**设置并入"我的"**。
- [x] 各 Tab 的 Compose 骨架 + Navigation（Scaffold + NavigationBar + NavHost）。
- [x] 架构骨架：`AppContainer`(手动DI) + `PostRepository` 接口 + `FakePostRepository` + MVVM(`HomeViewModel`/`HomeUiState`/单向数据流) + 首页 UI（导入卡片/来源筛选/帖子卡片，贴合设计稿）。

### P4.2 导入与后台运行（重点）
- [ ] 导入页：接收 `ACTION_SEND`(text/plain) 分享链接 + 手动粘贴链接/正文入口。
- [ ] **分享即入队、立即可退出**：落库 `ImportedPost(status=PENDING)` 并提交 `WorkManager`。
- [ ] **WorkManager 链式任务** `ReadWork → AnalyzeWork`：网络约束 + 指数退避重试；进程/重启后可恢复。
- [ ] **前台服务 + 进度通知**（expedited + setForeground）；完成后本地通知直达题目列表。
- [ ] 导入任务**状态机**与导入列表状态展示、失败重试 / 转手动粘贴。
- [ ] 入队前校验 DeepSeek Key，无 Key 引导去设置。

### P4.3 简历与结果
- [ ] 简历页：SAF 选文件导入 PDF/纯文本，端侧解析为 `ResumeProfile`（PdfBox-Android；DOCX/OCR 二期）。
- [ ] 题目列表页：按相关性/考点/难度展示，标注来源链接与相关性理由。
- [ ] 刷题页（占位，机制后续探讨）：答案默认隐藏。

### P4.4 我的 & 设置
- [ ] 我的页：简历、职业档案切换、（后续）记忆管理。
- [ ] 设置（并入我的）：填写/校验 DeepSeek Key、相关性阈值、隐私/一键删除、离线开关（占位）。
- [ ] ViewModel 单测：UiState 流转（用 fake repository）。

**验收标准**
- 分享一个链接后**可立即退出 App**，后台完成读取+分析，完成有通知，回来能在题目列表看到结果。
- 从"粘贴一段面经正文 + 一份简历"到"生成相关题目"全流程可用。

---

## P5 · 集成、真实链接与打磨

目标：接入真实网络与真实 DeepSeek，端到端跑通两个基准链接。

- [ ] 实现 `DeepSeekClient`（Retrofit，OpenAI 兼容 `chat/completions`），用 BYOK 的 Key 直连；实现重试/限流/超时。
- [ ] 集成测试（需真实 Key，标注 `@Ignore` 默认跳过，可手动开启）：对夹具正文跑真实分析并人工核对质量。
- [ ] 端到端：分享牛客链接 → WebView/静态读取正文 → 分析 → 刷题，真机跑通。
- [ ] 小红书：按 P2.1 结论落地（若需参考项目，接入其读取方式）。
- [ ] 异常兜底：读取失败→手动粘贴；Key 无效→引导设置；无相关题目→友好提示。
- [ ] 免责声明、来源标注、隐私说明、一键删除数据。
- [ ] 性能与体验打磨；可选 P6：端侧本地模型离线开关。

**验收标准**
- 用真实 DeepSeek Key，对牛客链接端到端产出相关题目与答案并可刷题。
- 所有失败路径都有明确兜底，不崩溃。

---

## 依赖关系与顺序

```
P0 ─▶ P1(测试先行) ─▶ P2(测试先行) ─▶ P3 ─▶ P3.5(测试先行) ─▶ P4 ─▶ P5
              └── P1、P2、P3.5 未"测试全绿"，不进入其真实网络/设备实现 ──┘
```


---

## 待办 · 题目相似去重（需求，后续实现）

问题：随导入增多，题库会出现语义重复的题目（同一问法的不同表述）。需要去重，且**不能每次扫全表**。

设计方向（增量、可扩展）：
- 入库前对新题做**规范化指纹**（去标点/空白、全半角统一、小写）→ 计算 hash（精确重复用哈希直接命中，O(1)）。
- 近似重复：为每题生成 **SimHash / MinHash** 指纹，存 `QuestionEntity.fingerprint`；新题只与**候选桶**（按 LSH 分桶或按首个考点标签分区）内的题比对，避免全表扫描。
- 可选：接入端侧 embedding（后续）做语义相似，向量入库 + ANN 近邻检索。
- 增量：仅在"分析落库"时对本批新题去重（对已有同分区做有限比对），不做全量重扫。
- 阈值命中则合并（保留相关性更高者/记录来源计数），不重复入库。

落地时新增：`QuestionEntity.fingerprint`、去重服务 `QuestionDeduplicator`（纯逻辑，可 JVM 单测：规范化、SimHash、汉明距离、分桶）。
