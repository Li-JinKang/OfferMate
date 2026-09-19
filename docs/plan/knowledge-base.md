# 项目知识库搭建规划

> 目标：让 agent 接到一个需求时，**不必重新探索仓库**就掌握该改哪里、有什么约束、前人踩过什么坑；并让每次需求过程中的教训**可沉淀、可校验、不腐化、不被污染**。
>
> 标记规范同 [`roadmap.md`](./roadmap.md)：`- [ ]` 未完成，`- [x]` 已完成，`- [~]` 部分完成。每阶段末尾有**验收标准**。

## 0. 问题与设计取舍

现状（2026-09-18 核对）：仓库无 `.kiro/`、无 `AGENTS.md`，agent 每次需求都从零探索；需求过程中的教训只留在 commit message 里，不可检索，因此同类错误会重犯。

三条已锁定的设计决策：

| 决策 | 内容 | 理由 |
|---|---|---|
| **组织维度** | 按**需求类型**组织「代码地图」，而非按模块写文档 | 一个改动通常横跨 UI/ViewModel/仓库/持久化多层，按模块写会把它拆散在数张卡里；按需求组织则一张卡说完 |
| **加载方式** | 常驻注入只留项目宪法 + 路由；地图/坑位卡按需求语义**按需激活** | 与本项目记忆系统 L1→L2→L3 分级加载同构，已验证有效；臃肿指令文件会反噬（见 §5） |
| **单一真源** | 知识库只写"代码里读不出来的东西"（意图、约束、坑、决策）；**不复述代码能自己说的话** | 复述即腐化面；能从代码读出的信息交给 agent 现场读 |

**硬性红线（本次文档校准直接换来的教训）**：进度类内容（"哪些功能实现了"）腐化最快，**不进知识库**。判断某能力是否存在必须查代码，`docs/plan/` 一律视为**意图文档**而非现状描述。

## 1. 目标结构

```
.kiro/
  steering/
    00-project.md              # always，≤120 行：技术栈/构建命令/包结构/红线/知识库使用规则
    map-quiz.md                # inclusion: auto + description → 需求提到题库/拼图时激活
    map-import.md              # 导入/读取/OCR 链路
    map-agent.md               # AI 流水线/工具轮/记忆
  hooks/
    knowledge-capture.json     # Stop / PostTaskExec → 提示把教训沉淀进 inbox
    knowledge-guard.json       # PreToolUse 写主库 → ask（防静默污染）
    anchor-check.json          # PostFileSave on docs/kb/**.md → 跑 kb-verify
docs/
  kb/
    README.md                  # 信任分层 + 卡片模板 + front-matter 规范 + 写入规则
    map/*.md                   # 代码地图（按需求类型）
    decisions/*.md             # ADR：为什么这么做，不可变，只 supersede
    pitfalls/*.md              # 坑位卡，必须带证据
    inbox/*.md                 # agent 自动沉淀的候选，未经审阅
  plan/                        # 现有规划（意图文档，边界见 §0）
tools/
  kb-verify.sh                 # 锚点校验 + 失效检测
```

`.kiro/steering/map-*.md` 是**薄壳**：只有 `description`（供语义激活）+ 一行指向 `docs/kb/map/*.md` 的引用。真内容在 `docs/kb/` 下，保证人也能正常阅读、review、diff。

## 2. 卡片规范（三类，职责不重叠）

每张卡统一 front-matter：

```yaml
---
trust: L1                      # L0 机器可验证 / L1 人工确认 / L2 未审候选
anchors:                       # 锚点：这张卡描述的代码
  - app/src/main/java/com/jk/offermate/ui/components/PuzzleGrid.kt
  - app/src/main/java/com/jk/offermate/data/repository/CategoryOrderStore.kt
verified_commit: 641c650       # 最后一次人工核对时的 HEAD
verified_at: 2026-09-18
supersedes: null               # 冲突时指向被取代的卡，不覆盖
---
```

| 类型 | 写什么 | 不写什么 |
|---|---|---|
| **map/**（代码地图） | 该类需求的入口文件与调用链、数据流向、必须同步改的地方（如加列要升 DB 版本）、已知约束、明确"不要做什么" | 函数实现细节、能从代码直接读出的签名 |
| **decisions/**（ADR） | 背景、被否决的选项及原因、决策、影响范围 | 实现步骤、进度 |
| **pitfalls/**（坑位卡） | 现象 → 根因 → 规避规则 → **证据**（commit / 测试名 / 日志片段） | 无证据的猜测、一次性环境问题 |

坑位卡的 `证据` 字段是**强制**的，这是防污染的第一道闸：没有可追溯证据的教训不收录。

## 3. 保鲜机制：用因果信号，不用日历信号

`tools/kb-verify.sh` 判两种失效：

- **STALE（硬错误）**：`anchors` 里的文件或符号已不存在 → 卡片在说不存在的代码，必须修或删。
- **SUSPECT（软告警）**：`git log verified_commit..HEAD -- <anchors>` 非空，即"被描述的代码在最后一次核对后改过" → 读这张卡前必须先核对代码。

> 为什么不按时间过期：纯时间衰减会误伤长期稳定的知识（`AppContainer` 手动 DI 半年没变），又漏掉高频变动的热点（`QuizScreen` 一周改三次）。失效的真正原因是**被描述的代码变了**，就应该直接测这件事。业界的 stale reference 问题是实测存在的——[一项对 356 个仓库的检查](https://arxiv.org/html/2606.09090v1)发现 23% 的仓库存在过期的代码元素引用（内容经转述以符合许可要求）。

配套两条行为规则（写进 `00-project.md`，对 agent 生效）：
1. 读到 `SUSPECT` 的卡，**先核对代码再使用**；发现不符，当场更新卡片或降级标记，**不得沉默使用**。
2. 任何卡片被使用并确认无误后，可更新 `verified_commit`/`verified_at`（这是 L0/L1 卡唯一允许 agent 自主修改的字段）。

## 4. 防污染机制

| 风险 | 措施 |
|---|---|
| agent 写入错误知识 | 只能落 `inbox/`（`trust: L2`），并在 `docs/kb/README.md` 明文声明 **L2 不得作为实现依据** |
| 静默改主库 | `knowledge-guard` hook：`PreToolUse` 匹配对 `docs/kb/{map,decisions,pitfalls}` 的写操作 → `permissionDecision: ask` |
| 无根据的"经验" | 坑位卡强制 `证据` 字段；review 时无证据直接退回 |
| 新旧知识矛盾 | **supersede 不覆盖**：新卡写 `supersedes`，旧卡标记 superseded 保留审计链 |
| 碎片膨胀 | 单卡 ≤150 行；`always` 注入 ≤120 行；定期合并同题卡 |

> 注意与业务侧的取舍差异：简历记忆系统**主动取消**了 supersede 链（见 [`memory.md`](./memory.md) 第 5 节），因为那里用户只要当前状态；知识库反过来**需要**审计链，因为一条错误知识会带偏后续所有决策，必须可追溯可回滚。失败代价不同，结论就该不同。

## 5. 反模式（明确不做）

- **不做向量库 / RAG**：单仓知识量级下，plain markdown + 语义激活足够，引入检索层只增加不可解释的召回失败。
- **不把 `docs/plan/` 当知识库**：进度会腐化，见 §0 红线。
- **不追求覆盖率**：据 [philschmid 的分析](https://www.philschmid.de/writing-good-agents)引用的研究，臃肿的指令文件平均降低任务成功率约 3%、推高推理成本 20% 以上（内容经转述以符合许可要求）。库越大越要敢删——**长期无人引用的卡是负资产**。
- **不写"如何写 Kotlin"这类通用知识**：模型已有，占预算且无增量。

---

## KB0 · 骨架与规范 ✅

- [x] 建目录：`docs/kb/{map,decisions,pitfalls,inbox}`、`tools/`。
- [x] `docs/kb/README.md`：信任分层（L0/L1/L2）、三类卡模板、front-matter 字段定义、写入规则（谁能改什么）、supersede 流程、索引。另建 `docs/kb/inbox/README.md` 明确 L2 不得作为实现依据 + 审阅三选一。
- [x] `.kiro/steering/00-project.md`（`always`，**实际 54 行**，上限 120）：
  - 技术栈与构建命令（`./gradlew assembleDebug` / `:app:testDebugUnitTest` / `connectedDebugAndroidTest`）。
  - 包结构与关键约定：手动 DI 组合根 `di/AppContainer`、`agent/{pipeline,tool,chat,resume,mcp}` 分层、Resume 用 DataStore 而非 Room、记忆用 `filesDir/memory` 分层文件。
  - 红线：`QuestionEntity` 等加列必须升 DB 版本并写迁移；新 AI 能力优先走工具轮而非塞 Prompt；不引入重型 Agent 框架。
  - **进度查代码不查文档**（§0 红线）。
  - 知识库使用与写入规则（§3 两条行为规则 + §4 的 L2 限制）。

**验收标准**
- [x] 新会话中 agent 未读任何代码即能正确回答：构建/测试命令、DI 方式、记忆存储形态、加列要不要升 DB 版本。（已验证 `00-project.md` 被注入生效）
- [x] `00-project.md` 行数 ≤120 → 实际 54。

---

## KB1 · 种子内容（只做有真实需求驱动的）

> 内容已全部写完（12 张主库卡 + 3 张 steering 薄壳），**但验收尚未完成**——读卡 vs 裸探索的对比需要等下一个真实需求。在那之前不扩充新卡。

### 代码地图（3 张起步，不求全）
- [x] `map/quiz-puzzle.md`：题库拼图与分类。锚点 `ui/components/PuzzleGrid.kt`、`ui/quiz/QuizScreen.kt`(`CategoryPalette`)、`ui/quiz/QuizViewModel.kt`、`data/repository/CategoryOrderStore.kt`、`CategoryRepository.observeOrder/saveOrder`。含：拖拽排序如何持久化、分类来源是"标签派生 + 用户分类"合并、配色按 hashCode 稳定取色。
- [x] `map/import-pipeline.md`：分享→入队→读取→OCR→分析→落库。锚点 `ShareIntentParser`、`HomeViewModel.canAnalyze`、`WorkManagerImportScheduler`、`AnalyzePostWorker`、`ContentReader`、`WebViewContentReader`、`XhsNoteExtractor`、`PostImageExtractor`、`ImportInteractor`、`PostStore`。含：降级链顺序、`ImportStatus` 状态机、去重在哪一步。
- [x] `map/agent-tools.md`：AI 流水线与工具轮。锚点 `AnalysisPipeline`、`RelevanceMatcher`、`AnswerGenerator`、`ToolCallingAgent`、`ToolRegistry`、`AppContainer.sharedToolRegistry`、`agent/tool/MemoryTools.kt`、`agent/mcp/`。含：新增工具要改哪几处、工具轮与非工具轮的回退路径、`read_resume` 已废弃改走记忆工具。

### 坑位卡（计划 5 张，实际 6 张，全部已付过学费，证据来自 commit 与现有代码）
- [x] `pitfalls/http2-shared-connection.md`：多个分析任务并发共享 HTTP/2 连接 → 一条被风控/超时会连坐拖垮其余。规避：`work/AnalyzeGate` 用 `Semaphore(1)` 串行化。证据：`641c650`、`data/net/` 全套。
- [x] `pitfalls/compose-zoom-nested-scroll.md`：可缩放图片在 `verticalScroll` 内，拖到边界后手势被吞、外层滚不动。规避：按缩放倍数算可拖边界 + 剩余手势经 `NestedScrollDispatcher` 转发外层；未放大时单指拖动整段转发。证据：`ZoomableImage` 相关修复提交。
- [x] `pitfalls/resume-emptiness-check.md`：简历改版去掉结构化字段后 `targetRole`/`skills` 常为空，用它们判"已配置简历"会误拦导入。规避：判 `ResumeProfile.rawText` 非空。证据：`AnalyzePostWorker`、`HomeViewModel`。
- [x] `pitfalls/memory-detail-id-mismatch.md`：概览只暴露显示名，模型按显示名调 detail 工具会失败。规避：`MemoryStore.resolveDetailId` 多级容错（精确 id → slug → 忽略大小写 → 正文标题比对）。证据：`de07e9f`。
- [x] `pitfalls/room-column-migration.md`：给 `QuestionEntity` 加列（`exactHash`/`simhash`/`bucketKey`/`category`/`source`）必须同步升 DB 版本，否则崩库；而升了版本因 `fallbackToDestructiveMigration()` 会**清空用户数据**。证据：`OfferMateDatabase.version` 当前为 **11**（写卡时核对；本规划初稿误写为 9，又一次印证"版本号只能查代码"）。
- [x] `pitfalls/coroutine-runcatching-cancellation.md`（**计划外补充**）：`runCatching` 吞掉 `CancellationException`，任务取消后继续跑并在已取消 scope 上写库。规避：手写 `try/catch` 重抛取消（`ImportInteractor.tolerate` / `AnalyzePostWorker.safely`）。补这张的原因是它已经是项目宪法红线，却没有对应的可查卡片。

### ADR（3 张，只记有争议的换轨与否决）
- [x] `decisions/0001-manual-di-over-hilt.md`：否决 Hilt，用 `AppContainer` 手动 DI。
- [x] `decisions/0002-memory-file-store.md`：记忆层从 Room 三表 + `MemoryManager` 换轨为分层文件 + 多记忆集共存 + AI 编排；`switchProfile`/`recall`/`supersede`/`decay` 取消的理由。
- [x] `decisions/0003-relevance-not-precomputed.md`：相关性不预存、不做简历变更后的连锁重算，改为 AI 现场调记忆工具判断。

### steering 薄壳（按需求语义激活地图卡）
- [x] `.kiro/steering/{map-quiz,map-import,map-agent}.md`：`inclusion: auto` + `description`，正文仅"指向 `docs/kb/map/*.md` 的引用 + 三条最容易踩的"，各 17 行。真内容留在 `docs/kb/` 以便人阅读与 review。

**验收标准**
- [ ] 取**下一个落在三张地图覆盖范围内的真实需求**，走一次端到端对比：**读卡路径** vs **裸探索路径**，记录各自的文件读取次数与澄清轮次。读卡路径应显著更少，且不漏该需求的任何关键改动点。 ← **待下一个真实需求，整套规划的成败判据**
- [ ] 反向抽查：对每张地图卡，随机挑一处"必须同步改的地方"核对代码，确认描述准确。
- [x] 每张卡都有完整 front-matter（`kb-verify.sh` 已校验 12 张全绿）；坑位卡都有可追溯证据。

---

## KB2 · 保鲜机制 ✅

- [x] `tools/kb-verify.sh`：解析 front-matter；锚点文件/符号不存在 → `STALE`（退出码 1）；`git log <verified_commit>..HEAD -- <anchors>` 非空 → `SUSPECT`（仅告警，并列出相关 commit）；`verified_commit` 在仓库中不存在 → `SUSPECT` 并提示重新核对。支持 `--quiet`（仅输出问题，供 hook / CI 用）。
- [x] `.kiro/steering/00-project.md` 写入两条读卡行为规则（`SUSPECT` 先核对、不符则当场修正或标注）。
- [ ] 可选：接入 pre-commit 或 CI，`STALE` 视为失败。（脚本已具备退出码语义，未接）

**验收标准**
- [x] 故意重命名被锚定的符号 `PuzzleGrid` → 报 `STALE` 并定位到 `map/quiz-puzzle.md`，退出码 1。
- [x] 删除锚点文件 `WaveFillBlob.kt` → 报 `STALE`。
- [x] 把 `verified_commit` 改成旧 commit → 报 `SUSPECT`（非 `STALE`），并列出改动过锚点的 commit，退出码 0。
- [x] 未改动锚点的卡不产生任何告警：基线 12 张全绿，`--quiet` 零输出。

> **过程记录（重要）**：首版脚本能跑且报"全绿"，但两条判定其实都失效，是靠上面的反向用例才发现的——
> ① 符号检查对整个文件做子串匹配，KDoc 注释里残留的旧名字掩盖了"函数已改名"；
> ② `set -o pipefail` + `grep -q` 导致上游 `sed` 收到 SIGPIPE，把"符号存在"误判成 `STALE`。
> 教训：**校验工具必须有"能否发现问题"的反向测试**，只跑正向用例会让这类失效完全隐形。已作为候选记入 `docs/kb/inbox/2026-09-19-bash-verify-script-traps.md`。

---

## KB3 · 写入闭环（hooks）✅

- [x] `kb-capture`（`Stop`，`agent` 动作）：`.kiro/hooks/kb-capture.json`。提示复盘，并明确列出"值得沉淀 / 不要沉淀"各三条（不要沉淀：一次性环境问题、无证据猜测、能从代码读出的信息、进度），限定只能写 `inbox/`，没有就明说"无需沉淀"不硬凑。
- [x] `kb-guard`（`PreToolUse`，matcher `fs_write|str_replace|fs_append`，`command` 动作）：`.kiro/hooks/kb-guard.json` → `tools/kb-guard.sh`。命中 `docs/kb/{map,decisions,pitfalls}` 返回 `permissionDecision: ask`；`docs/kb/inbox/` 与普通代码文件静默放行。
- [x] `kb-anchor-check`（`PostFileSave`，matcher `docs/kb/.*\.md$`）：跑 `tools/kb-verify.sh --quiet`。

**验收标准**
- [x] `inbox/` 出现结构合规的候选卡：`2026-09-19-bash-verify-script-traps.md`（建库过程本身产出的教训）。
- [x] 写主库触发确认：`kb-guard.sh` 四场景验证 —— 写 `pitfalls/` → `ask`、写 `map/` → `ask`、写 `inbox/` → 放行、写 `.kt` → 放行。
- [x] front-matter 有误立刻报错：`kb-verify.sh` 对缺 `trust` / `verified_commit` / `anchors` 均判 `STALE`，经 `PostFileSave` hook 在保存时触发。
- [ ] hook 实际生效需**下次会话启动**后确认（创建时即已提示）。

---

## KB4 · 运营与收敛（持续）

- [ ] 需求开始前：读命中的地图卡；`SUSPECT` 先核对代码。
- [ ] 需求结束后：教训入 `inbox/`；顺手修正读卡时发现的偏差。
- [ ] 周期性（建议每 5~10 个需求或每月）审 `inbox/`：**合并 / 升级为 L1 / 丢弃**三选一，不积压。
- [ ] 预算与删除：`always` 注入 ≤120 行；单卡 ≤150 行；**连续多个需求未被引用的卡降级或删除**。

**度量指标**（用来判断这套东西是否真的省了成本，不是为了好看）
- 单次需求的探索类工具调用次数（读文件/搜索）。
- 需求前的澄清轮次。
- 同类错误重犯次数（目标：坑位卡覆盖过的坑不再重犯）。
- `kb-verify` 的 `STALE` 存量（目标长期为 0）。

---

## 顺序与依赖

```
KB0(骨架+宪法) ─▶ KB1(种子：3 地图 + 5 坑位 + 3 ADR) ─▶ KB2(校验脚本) ─▶ KB3(hooks 闭环) ─▶ KB4(运营)
                        └── KB1 验收（真实需求读卡 vs 裸探索对比）未通过前，不扩充更多卡片 ──┘
```

**关键节制**：KB1 的验收是整套规划的成败判据。先用 3 张地图卡验证"读卡确实比裸探索省"，再谈扩充；否则容易一次铺开几十张卡，最后大部分无人引用，反而拖累上下文预算（§5）。
