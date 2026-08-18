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
- [x] 通用 `WebViewContentReader`：离屏 WebView 加载 URL（真实 UA + 执行 JS，自然跟随短链跳转），`onPageFinished` 后轮询取渲染后 DOM（`evaluateJavascript` 取 `outerHTML`），复用已测的 `HtmlContentExtractor`（内部按需走 `XhsNoteExtractor` 解析 `__INITIAL_STATE__` 或 Readability/Jsoup 兜底），未注入独立 `readability.js`；超时兜底走手动粘贴。已接入 `AppContainer`。
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
- [x] 导入页：接收 `ACTION_SEND`(text/plain) 分享链接 + 手动粘贴链接/正文入口。（`MainActivity` 声明 intent-filter + 解析 `EXTRA_TEXT`；`ShareIntentParser` 从分享文本中抽取链接（纯函数，JVM 单测）；抽不到链接则整段文本走手动粘贴正文路径；`HomeViewModel.onSharedTextReceived` 复用现有 `enqueueUrl`/`enqueueText`）
- [x] **分享即入队、立即可退出**：落库 `ImportedPost(status=PENDING)` 并提交 `WorkManager`。（`WorkManagerImportScheduler`/`PostStore.createPending`）
- [x] **WorkManager 后台任务**：`AnalyzePostWorker`（单 worker 内完成读取+分析，非文档最初设想的 `ReadWork→AnalyzeWork` 两阶段链，但功能等价）+ 网络约束 + 指数退避重试；进程/重启后可恢复。
- [x] **前台服务 + 进度通知**（`setForeground` + `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC`）；完成后本地通知直达题目列表。
- [x] 导入任务**状态机**（`ImportStatus`）与导入列表状态展示、`NEEDS_MANUAL_INPUT` 转手动粘贴。
- [ ] 入队前校验 DeepSeek Key，无 Key 引导去设置（目前仅校验"是否已配置简历"，未校验 Key；待办）。

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

**已完成 ✅**（首版）：
- `QuestionDeduplicator`（纯逻辑，JVM 单测）：`normalize`(NFKC 全半角统一+小写+去标点空白)、64 位 `simhash`(字符 bigram 加权)、`hamming`、`bucketKey`(按首个考点标签分桶)、`isDuplicate`(精确相等 或 同桶且汉明距离≤阈值3)。
- `QuestionEntity` 增列 `exactHash` / `simhash` / `bucketKey`（+ 索引），DB 版本 5→6。
- 落库去重接入 `PostStore.saveSuccess`：**增量**丢弃与本批已保留题、及同分桶/精确指纹已入库题重复的新题（`QuestionDao.fingerprintsInBuckets` + `existingExactHashes`，不扫全表），`questionCount` 按去重后计。
- 单测：`QuestionDeduplicatorTest`（规范化/simhash/汉明/分桶/阈值门控）、`PostStoreTest`（本批内去重、跨帖子去重）。

**后续可增强**：命中时"保留相关性更高者/记录来源计数"合并（当前为跳过入库）、无标签桶的 LSH 分带、端侧 embedding 语义去重。


---

## 待办 · 链接读取健壮性 + 图片面经 OCR（现网问题）

### 现象与结论（已确认）
- 现网日志：`NEEDS_MANUAL resolved=https://xhslink.cn/o/6Gz0nDGZxAE reason=短链未能展开为真实地址（网络异常或被平台风控）` —— **展开后 `resolved` 仍是短链**，说明 `OkHttpUrlResolver.resolve()` 在设备网络下未成功跟随重定向（`runCatching{}.getOrDefault(rawUrl)` 把异常/拦截吞掉后回退原短链）。
- 同一条链接在 `LiveLinkReadingTest`（JVM/测试机）能展开并读到正文：说明是**环境差异**（IP/风控/时效 `xsec_token`），非逻辑差异。
- 已做的小改进 ✅：`ContentReader` 按卡点输出可诊断原因（短链未展开 / 抓取失败 / 抓到但无正文），并加 `ContentReaderTest` 覆盖。

### 已完成 ✅ · WebView 离屏渲染读取（P2.4 落地，修根因）
- `WebViewContentReader : DynamicContentReader`：离屏 `WebView`（真实浏览器 UA + 执行 JS）加载展开后的页面，`onPageFinished` 后轮询取渲染后 DOM，复用已测的 `HtmlContentExtractor` 解析（内部按域名走 `XhsNoteExtractor`/`__INITIAL_STATE__` 或 Readability/Jsoup），超时兜底手动粘贴。
- 短链展开交由 WebView 自然跳转拿最终 URL，缓解"短链未展开"问题。
- 已接入 `AppContainer`（`dynamicReader = WebViewContentReader(context, htmlContentExtractor)`），生产环境不再是 `null`。
- ⚠️ 待验证：真机渲染表现（网络/风控环境差异）；`WebViewContentReader` 自身仅做手动/真机验证，未纳入 JVM 单测门槛（符合原计划）。

### 待办 · 图片面经 OCR（图片转文字）
背景：牛客/小红书大量面经以**长图**发布，纯文本读取拿不到题目（现网案例：正文只有话题标签，题目全在图里）。

**调研结论（已用真实链接验证）✅**：
- 小红书笔记页 `window.__INITIAL_STATE__` 里，当前笔记的图片在**第一个** `imageList` 数组：
  ```
  "imageList":[ { "url":"http://sns-webpic-qc.xhscdn.com/.../notes_pre_post/<fileId>!h5_1080jpg",
                  "infoList":[{"imageScene":"H5_DTL","url":"...!h5_1080jpg"},   // 详情大图
                              {"imageScene":"H5_PRV","url":"...!style_..."}],   // 预览小图
                  "fileId":"notes_pre_post/<fileId>","height":..,"width":.. }, ... ]
  ```
  规则：详情大图以 `!h5_` 结尾、预览小图以 `!style_` 结尾；**取详情大图**，排除预览、评论头像（`sns-avatar-qc`）、静态资源，且只取第一个 imageList（避开"推荐笔记"）。字段顺序在不同响应下会变，不能依赖 `imageScene` 与 `url` 紧邻。
- 牛客：正文 HTML 的 `<img>`（Jsoup 绝对化 + 去重）。
- 图片 URL 为 `http` 明文 CDN，带时效路径段（如 `/202608162036/`），会过期 → 需即时下载。

**已完成 ✅**：
- `PostImageExtractor`（纯逻辑）：`extractXhsImageUrls`（首个 imageList、排除 `!style_`、解码 `\u002F`）、`extractHtmlImageUrls`（通用 `<img>`）。
- `PostImageExtractorTest`（离线夹具 `xhs_imagelist_sample.html`）：有序取详情大图、解码、排除预览/头像/推荐笔记。
- `ImageOcrProbeTest`（手动探针，真实抓取）：打印帖子全部图片 URL；提供 `-Docr.key`（多模态模型）时对每张图 OCR 并打印文字，供人工校验。实测该链接稳定取到 2 张详情大图。

**引擎选型（已定）✅：端侧 ML Kit Text Recognition v2**
- 离线、免费、无需 Key，契合"纯端侧"定位；不消耗 LLM token。
- **中英文数字混排要求**：计算机面经含大量英文专业术语（如 `Kotlin`、`Handler`、`ThreadLocal`）、数字、符号。选 **中文识别模型** `com.google.mlkit:text-recognition-chinese`——其模型在识别中文的同时也覆盖**拉丁字母与数字**，适合中英混排；上线前用探针/真机核对英文术语与代码样式（大小写、点号、括号）的识别质量。若个别纯英文长段落识别偏差明显，再评估对该图**叠加拉丁模型** `text-recognition`（`TextRecognition.getClient(TextRecognizerOptions.DEFAULT)`）做二次校正的必要性。
- 依赖：`com.google.mlkit:text-recognition-chinese`（APK +数 MB，模型可用 Play 按需下发以减小包体）。

**待办**：
- [ ] 抽象 `OcrTextRecognizer` 接口（输入图片字节/Bitmap → 输出文本 + 置信度/块）；Android/ML Kit 实现（真机或 androidTest 验证），测试用 fake，合并/排版逻辑 JVM 可测。
- [ ] 多图顺序拼接（按 imageList 顺序）、去重与空白/水印行清理；OCR 结果缓存进 `ImportedPost` 避免重复识别。
- [ ] 接入读取链路：WebView 取到 `__INITIAL_STATE__` → `PostImageExtractor` 提图 → 下载 → ML Kit OCR → 文本拼入正文 → 走"抽题→相关性→作答"。

**运行 OCR 探针校验**：
```
./gradlew :app:testDebugUnitTest --tests "com.jk.offermate.data.reader.ImageOcrProbeTest" --console=plain \
  -Docr.key=<视觉模型Key> -Docr.model=qwen-vl-max \
  -Docr.baseUrl=https://dashscope.aliyuncs.com/compatible-mode/v1/
```

---

## 已完成 ✅ · 题目分类改为 LLM 决定（不再写死关键词表）

- 动机：写死关键词表(handler→Android 等)不可扩展；应把**已有分类清单**给 LLM，由它决定归属并可**新建**分类。
- `CategoryClassifier`（`data/ai`，`AiClient`）：`buildMessages(questions, existingCategories)` + `parse`（容错，index→category）+ `classify`；提示词要求优先复用已有类目、必要时新建简洁粗类目、避免过细。
- `AnsweredQuestion.category` / `QuestionEntity.category`（DB 版本 7→8）；`ImportInteractor` 分析出题后调用分类器（可选注入），新分类写回 `CategoryRepository` 供下次复用。
- UI 分组：`CategoryCanonicalizer.displayCategory` 优先用 LLM 分类，**仅在为空（旧数据/离线/无 Key）时回退**本地启发式归并。
- 单测：`CategoryClassifierTest`（prompt/parse/classify/空输入不调用模型）。
- 注：分类为独立一次结构化调用（清晰、可测），后续如需省调用可并入相关性/作答步骤。

---

## 决策 · Agent/工具框架：自研 vs 成熟框架（评估结论）

- **分类不需要 tool/MCP**，普通结构化调用即可（已落地，见上）。tool/MCP 是更大的独立议题。
- 现状核对（2026-视角）：
  - LangChain4j：JDK17+/面向后端(Spring)，Android 支持存疑 → 排除。
  - MCP Kotlin SDK（官方，JetBrains 共维护，KMP，client+server）→ **仅当需接入外部第三方工具服务器时引入**；本地工具（读简历/列分类）用 function-calling 即可，无需 MCP。
  - Koog（JetBrains，1.0 稳定，KMP 明确支持 Android，内置 tools/memory/persistence/多 LLM 切换/MCP，另有 koog-edge 端侧推理）→ 覆盖我们规划的 agent/memory/tools。
- **结论/建议**：
  1. 本地工具的 function-calling 先用现有薄封装自研（小、可控、Android 友好）。
  2. 当 agent 逻辑/记忆变复杂时，做一次 **Koog 小样评估**（重点 Android APK 体积/方法数/稳定性 + BYOK OpenAI 兼容接入）；可接受则采用 Koog 承载 agent/memory/tools，**不要从零自研 MCP + agent 编排**。
  3. 三步分析流水线保持自研（简单可测，不上重框架）。
  4. 需要外部工具时再引官方 MCP Kotlin SDK（client）。

---

## 待办 · 工具调用（Function Calling / MCP / Skills）★重点

> 这一部分是架构级能力，**必须重视**，会影响 `AiClient`、分析流水线与记忆系统的设计。
> 落地路径按上节"决策"：本地工具先自研 function-calling，复杂化后评估 Koog，外部工具用官方 MCP Kotlin SDK。

### 动机
当前把整份简历/画像塞进每次 Prompt：成本高、上下文被污染、也不利于"方向切换/多简历"。改为 **Agentic 按需取数**：
- 帖子**首次**发给 LLM 时，只带**最小画像**（如核心技能 `Java`、`Android`、目标岗位），以及帖子正文/OCR 文本。
- 给 LLM 挂一组**工具**；当它判断信息不足（例如需要确认候选人是否做过音视频、是否熟悉某框架）时，**自行调用工具**拉取更多上下文，再继续作答。
- 典型工具：`read_resume(query|section)` —— 简历阅读器，按需返回相关简历片段（由 `ResumeRepository`/端侧检索支撑）；后续可扩展 `recall_memory(scope,query)`（接 P3.5 记忆）、`get_projects()` 等。

### 能力形态
- **Function Calling（本地工具）**：端侧注册的工具，LLM 通过 OpenAI 兼容的 `tools` / `tool_calls` 协议调用；由 App 本地执行并回填结果。DeepSeek / qwen / glm 均支持。
- **MCP（Model Context Protocol）**：以 MCP 客户端连接外部/可插拔工具服务器（Android 侧走 HTTP/SSE 传输），把 MCP 暴露的 tool 映射为可供 LLM 调用的工具。用户可配置 MCP server。
- **Skills**：把"提示词模板 + 所需工具集 + 参数"打包为可复用技能（如"简历深读技能""公司面经风格分析技能"），供流水线按场景挂载。

### 需要的改造
- **`AiClient` 升级**：现为 `suspend fun chat(messages): String`，需支持工具轮：
  - 传入 `tools` 定义；返回结构化结果（普通文本 **或** `tool_calls`）。
  - 新增 **Agent 循环**（`ToolCallingAgent`）：`发送(messages+tools) → 若有 tool_calls → 本地/MCP 执行 → 追加 tool 结果消息 → 再次发送`，直至产出最终答案或达到步数上限。
- **工具抽象**：`Tool { name, description, parametersSchema, suspend fun call(argsJson): String }` + `ToolRegistry`；`ResumeReaderTool` 首发。
- **MCP 客户端**：`McpClient`（连接、`listTools`、`callTool`），把 MCP tool 适配为 `Tool`。
- **接入流水线**：相关性筛选/作答步骤改为"最小画像 + 工具可用"，让模型按需 `read_resume`；与 P3.5 `recall` 协同（工具从记忆/简历取数）。
- **可插拔 Provider**：仅对**支持 function calling** 的 provider 启用工具轮；不支持时回退为"直接携带精简画像"的旧路径。

### 测试（测试先行，尽量 JVM 可测）
- [ ] `ToolCallingAgent` 循环：用 `FakeAiClient` 先返回一个 `tool_call(read_resume)`、再返回最终答案 → 断言工具被调用、结果被回填、最终答案正确、步数上限生效。
- [ ] `ResumeReaderTool`：给定简历文本 + query → 返回相关片段（纯逻辑可测）。
- [ ] Prompt/协议：`tools` 定义与 `tool_calls` 解析（含畸形/多次调用/无调用直接作答）。
- [ ] MCP 适配：用 fake MCP server（内存实现）验证 `listTools`/`callTool` → `Tool` 映射。

### 已完成 ✅（自研薄框架核心）
- 端口与循环：`ToolCallingLlm`（工具轮端口）+ `LlmTurn`(Final/ToolInvocations) + `Tool`/`ToolRegistry` + `ToolCallingAgent`（发送→执行工具→回填→再发送，带 maxSteps 兜底）。
- 消息模型扩展：`ChatMessage` 支持 `toolCalls` 与 `Role.TOOL`（工具结果）；`ToolCall`/`ToolSpec`。
- Provider：`DeepSeekClient` 实现 `ToolCallingLlm`（构造 OpenAI `tools`、解析 `tool_calls`/最终文本），与原 `chat(messages)` 简单补全共存。
- 首个工具：`ResumeReaderTool`（按需读简历，可 query 过滤；`resumeTextProvider` 注入解耦）。
- 单测：`ToolCallingAgentTest`（工具轮/未知工具/步数上限）、`ResumeReaderToolTest`、`DeepSeekToolCallingTest`（tools 请求 + tool_calls/final 解析 + tool 结果序列化）。

### 已完成 ✅（首个消费方接入：追问）
- `FollowUpService` 接入工具轮：注入 `ToolCallingLlm?` + `ToolRegistry`，有工具时用 `ToolCallingAgent` 驱动，system 提示可用 `read_resume`，模型按需拉取简历；无 function-calling 的 provider 回退 `aiClient.chat` 旧路径。
- `AppContainer` 装配：`toolCallingLlm = aiClient as? ToolCallingLlm`，`ToolRegistry(ResumeReaderTool{ resumeRepository.profile.rawText })`。
- 顺带修复：简历改版后 `targetRole/skills` 常为空，追问改由 `read_resume` 取简历，画像不再缺失。
- 单测：`FollowUpServiceTest` 新增"启用工具轮时 reply 调用 read_resume 并回填结果"。

### 已完成 ✅（相关性/作答接入 read_resume）
- `RelevanceMatcher` / `AnswerGenerator` 注入 `ToolCallingLlm?` + `ToolRegistry`：启用工具轮时**首屏只带最小画像**（岗位/技能/项目），不再塞简历全文，模型按需 `read_resume`；无 function-calling 的 provider 回退旧路径（仍带 rawText）。
- `AppContainer` 抽出共享 `resumeToolRegistry` + `toolCallingLlm`，供追问/相关性/作答复用。
- 单测：`RelevanceMatcherTest` / `AnswerGeneratorTest` 各新增"启用工具轮时调用 read_resume 并回填"。

### 待办清单
- [ ] 更多工具（`recall_memory` 接 P3.5 记忆、`list_categories` 等）与 Skills 打包。
- [ ] 工具轮日志（TAG=OfferMate）便于真机观察模型是否调用工具、query 与结果。
- [ ] `McpClient` + 配置项（用户可增删 MCP server）+ tool 映射。
- [ ] Skills 打包机制（模板 + 工具集）与在流水线中的挂载。
- [ ] `DeepSeekClient` 等实现工具轮的真实请求/解析（P5，真机/真实 Key 验证）。

---

## 待办 · 后续功能（用户规划）

1. ~~**题目追问页**：提供页面对某道题向 AI 发起提问，根据 AI 回答**更新该题答案**（依赖会话层，见 memory.md 的追问会话）。~~ ✅ 已完成
   - 会话层（测试先行）：`data/ai/chat` 下 `TokenEstimator`/`HeuristicTokenEstimator`、`ChatMemory`(`MessageWindowMemory`/`TokenWindowMemory`)、`ContextAssembler`、`FollowUpService`(追问/`reviseAnswer` 重写答案)。
   - 持久化：Room `Conversation`/`ChatMessage` 实体 + `ConversationDao` + `ConversationRepository`（每题一会话），DB 版本 4→5。
   - `QuestionRepository` 扩展 `observeById`/`updateAnswer`。
   - UI：`FollowUpScreen`/`FollowUpViewModel`（聊天气泡 + 输入 + “用本轮讨论更新答案”），题目卡片“追问”入口，导航 `followup/{questionId}`。
   - 单测：TokenEstimator/ChatMemory/ContextAssembler/FollowUpService/ConversationRepository 全绿。
2. **答案分点 + Markdown**：`AnswerGenerator` 产出**分点**答案；题目卡片答案用 **Markdown 渲染**（候选：compose-richtext / Markwon / 自研轻量渲染）。
3. **需调研后落地**（统一调研，关联 memory.md）：
   - AI 记忆机制（三层记忆、语义事实、方向切换）——仍未开始，见 P3.5.1。
   - 题目相似**去重**（SimHash/LSH 分桶，增量不扫全表）。
   - **简历更新 → 相关度连锁重算**：简历/职业档案变更后，对已有题目重新评估相关性并更新，避免全量重跑的高成本。
   - ~~**会话管理**（多会话持久化）~~ ✅ 已完成（基础版）：原"一题一会话"（`questionId` 唯一索引）改为一题可开**多轮独立会话**；`ConversationRepository` 新增 `createNewForQuestion`/`observeConversationsForQuestion`；`FollowUpScreen` 加会话切换条（"第 N 轮" + "新开一轮"），默认续上最近更新的会话。DB 版本 8→9。上下文窗口裁剪/摘要记忆仍是现状（`TokenWindowMemory`），未做跨会话摘要。
4. **题库拼图增强**：
   - 更丰富的配色（扩充调色板 / 按分类稳定取色 / 渐变）。
   - 支持**拖拽排序**（自定义顺序持久化到本地）。
6. ~~**简历预览区缩放/拖拽越界**~~ ✅ 已完成：`ZoomableImage` 缩放范围维持 1x~5x；新增按缩放倍数计算的可拖拽边界（不能无限拖走内容），拖到边界后剩余手势通过 `NestedScrollDispatcher` 转发给外层 `verticalScroll`，未放大时单指拖动整段转发给外层，解决"拖到底部无法带动外层滚动"的问题。
5. ~~**用户自定义分类与题目**~~ ✅ 已完成
   - 分类：`CategoryEntity`/`CategoryDao`/`RoomCategoryRepository`，支持手动新增/删除分类（可先建空分类）；题库总览 `QuizViewModel` 合并"标签派生分类 + 用户分类"，空分类显示 0/0。
   - 题目：`QuestionRepository.addManualQuestion`（id=`manual_UUID`、`source=MANUAL`、relevanceScore=100 置顶），与 AI 题共存；分类页对手动题提供"删除"（`QuestionEntity.source` + `AnsweredQuestion.source` 区分 AI/MANUAL）。
   - UI：题库页"+ 分类""+ 题目"对话框（分类可选已有 chip、难度 chip）。DB 版本 6→7。
   - 单测：`CategoryRepositoryTest`、`QuestionRepositoryTest`。
   - 待增强：分类重命名、手动题编辑（当前支持增/删）。
6. ~~**简历页改版（预览 + 折叠，去结构化框）**~~ ✅ 已完成
   - 去掉"目标岗位""技能"输入框。
   - 上传 PDF 后：`ResumeFileStore` 复制到内部存储，`PdfPageRenderer`（系统 `PdfRenderer`）渲染为位图，`ZoomableImage`（双指缩放/平移）预览。
   - 识别文本以**可折叠区块**呈现，可编辑并保存（`RecognizedTextSection`）。
   - 依赖影响已处理：相关性/作答改为把 `ResumeProfile.rawText`（截断 2000 字）注入 Prompt；`AnalyzePostWorker` 与 `HomeViewModel` 的"已配置简历"判断由 `targetRole` 改为 `rawText` 非空，避免导入误拦。
   - ⚠️ 需真机验证：PDF 渲染、缩放手势、预览 UI（本机仅编译 + 单测通过，未跑设备）。
   - 待增强：从简历文本 AI 抽取岗位/技能画像（接 P3.5），进一步提升相关性精度。
