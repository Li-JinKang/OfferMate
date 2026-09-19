---
inclusion: always
---

# OfferMate · 项目宪法

Android 端侧面经学习 App：导入面经链接 → AI 抽题 → 按简历筛相关性 → 生成答案 → 刷题。
**完全端侧，无自建后端**；AI 走 BYOK（用户自填 Key，加密存本机）。

## 构建与测试

```bash
./gradlew assembleDebug                 # 编译
./gradlew :app:testDebugUnitTest        # JVM 单测（唯一的"测试先行"门槛）
./gradlew connectedDebugAndroidTest     # instrumented（需设备，不纳入门槛）
```

只有 `app/src/test` 的 JVM 单测算门槛。联网型探针测试（`LiveLinkReadingTest`、`ImageOcrProbeTest`）默认跳过，需显式传 `-D` 参数开启。

## 架构关键事实（代码里不容易一眼看出的）

- **手动 DI**：组合根 `di/AppContainer.kt`（`DefaultAppContainer`），由 `OfferMateApplication` 持有。**没有 Hilt**，新依赖在这里 `by lazy` 装配。
- **包结构**：`agent/{pipeline,tool,chat,resume,mcp}` = AI 相关；`data/{reader,importer,ocr,dedup,local,memory,net,repository,settings}`；`ui/`；`work/` = WorkManager；`domain/model`。
- **存储分工**：题库/帖子/分类/会话 → Room；设置偏好 → DataStore；API Key → `EncryptedSharedPreferences`；**简历记忆 → `filesDir/memory/` 分层 Markdown 文件**（不是 Room）；分类显示顺序 → DataStore。
- **AI 取数靠工具轮**：Prompt 里**不注入简历全文**。模型通过 `sharedToolRegistry` 里的工具按需取数（记忆四件套 + 题库/分类 + MCP）。旧的 `read_resume` 已删除。
- **kotlinx-serialization 只用运行时 `JsonElement` API**，没上编译器插件，所以不要写 `@Serializable`。LLM 输出解析统一走 `agent/JsonSupport.kt`。

## 红线

1. **改 Room 实体（加/改列）必须同步升 `OfferMateDatabase.version` 并更新其上方的版本注释。** 当前 `AppContainer` 用 `fallbackToDestructiveMigration()`，升版本 = 清空用户数据；要保数据必须自己加 `addMigrations(...)`。
2. **协程里不要用 `runCatching` 兜底 IO/AI 调用**。它会把 `CancellationException` 也吞掉，导致任务取消后继续跑并在已取消的 scope 上写库。项目惯例是手写 `try/catch` 并重抛 `CancellationException`（见 `ImportInteractor.tolerate`、`AnalyzePostWorker.safely`）。
3. **新增 DAO 方法要同步补测试里的 fake 实现**，否则单测编译失败（`CategoryRepositoryTest.FakeCategoryDao`、`PostStoreTest` 里都有手写 fake）。
4. **不引入重型 Agent 框架**（LangChain4j / Koog 等）。工具轮是自研薄封装，保持可 JVM 单测。
5. **新能力优先做成工具（`Tool`）让模型按需调用**，而不是把更多内容塞进 Prompt。

## 判断"某功能是否已实现"

**查代码，不查文档。** `docs/plan/` 下的所有文件是**意图文档**（规划、设计、决策），其进度勾选会滞后于实现，不得作为"某能力是否存在"的依据。
（实例：`roadmap.md` 曾长期写着 DB 版本 9、MCP 未实现，而代码里版本已是 11、MCP 早已落地。）

## 知识库（docs/kb/）使用规则

需求开始前先看 `docs/kb/map/` 下对应的代码地图卡，避免重复探索仓库。三类卡：`map/`（代码地图，按需求类型）、`decisions/`（ADR）、`pitfalls/`（坑位卡）。规范见 `docs/kb/README.md`。

**读卡时的硬性要求**：

1. 卡片 front-matter 的 `verified_commit` 之后若锚点文件有过改动（用 `tools/kb-verify.sh` 可查出 `SUSPECT`），**必须先核对代码再使用卡片内容**。
2. 发现卡片与代码不符，**当场修正卡片或标注存疑，不得沉默使用**。确认无误可更新 `verified_commit` / `verified_at`。
3. **agent 自己总结的新知识只能写进 `docs/kb/inbox/`**（`trust: L2`），不得直接写入 `map/` `decisions/` `pitfalls/`。`inbox/` 内容未经人工审阅，不得作为实现依据。
4. 坑位卡必须带可追溯证据（commit / 测试名 / 日志），没有证据的经验不收录。

## 语言

代码注释、文档、提交信息、对话一律用中文。注释写"为什么"，不复述"做了什么"。
