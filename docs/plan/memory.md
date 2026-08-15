# 端侧记忆管理子系统

> 目标：不仅记住会话，更要**管理长期、可演进的用户记忆**。典型场景：用户先投 Java 后端，后改投 Android 开发；未来支持多套简历。方向切换后，"什么题目算相关"、AI 如何作答都要随之改变，同时保留历史可追溯。全部在端侧（Room），可测（测试先行）。

关联：底层框架决策见 [`ai-framework.md`](./ai-framework.md)；本文件聚焦"记忆管理"。

## 1. 记忆三层模型

| 层 | 载体 | 作用 | 时效 |
|----|------|------|------|
| 工作记忆 | `ChatMessage` 窗口 | 当前多轮对话上下文 | 短期 |
| 情景记忆 | `MemorySummary` + `MemoryEvent` | 会话摘要、关键事件（"何时说了什么"） | 中期 |
| 语义记忆 | `MemoryFact` | 关于用户的持久结构化知识 | 长期，核心 |

## 2. 核心概念

### 2.1 职业档案 CareerProfile（应对方向切换/多简历）
- 一个 profile 代表一个求职方向（如"Java 后端""Android 开发"），可关联一份 `Resume`。
- 有且仅有一个**激活档案**（`isActive`）。切换激活档案会重定义"相关题目"的判定口径与作答上下文。
- profile 可 `ARCHIVED`（归档旧方向而不删除）。
- 多简历：`Resume` 可有多份；MVP 先支持单份，模型已为多份预留（profile → resumeId）。

### 2.2 事实 MemoryFact（语义记忆单元）
关键字段：
```text
id, scope(GLOBAL|PROFILE), profileId?, type, key, value,
confidence(0-1), salience(重要度), source(RESUME|CONVERSATION|MANUAL|INFERRED),
status(ACTIVE|SUPERSEDED|EXPIRED), supersedesId?,
createdAt, updatedAt, lastUsedAt, expiresAt?
```
- **scope**：`GLOBAL`（跨方向共享，如年限、学历、语言）；`PROFILE`（方向专属，如 target_role、该方向技能与项目侧重）。
- **type 示例**：`target_role`、`skill`、`project`、`preference`、`weakness`、`mastered_topic`、`company_interest`、`experience_years`。
- **status + supersedesId**：事实演进用"取代"而非删除，保留历史链，可追溯"过去想做 Java 后端"。

### 2.3 情景事件 MemoryEvent
```text
id, profileId?, type, content, relatedFactId?, createdAt
```
记录如"用户表示改投 Android""用户反馈某题不会"等，用于回溯与后续巩固。

### 2.4 会话与摘要（见 ai-framework.md）
`Conversation` / `ChatMessage` / `MemorySummary`。

## 3. 记忆管理操作（MemoryManager，均为可测纯逻辑）

- **remember(candidate)**：upsert + 冲突处理。
  - 若存在同 `(scope, profileId, type, key)` 的 `ACTIVE` 事实且 value 不同 → 旧事实置 `SUPERSEDED` 并写 `supersedesId` 链，插入新 `ACTIVE`。
  - value 相同则提升 confidence/salience、更新 `updatedAt`。
- **recall(context) → List<MemoryFact>**：按当前激活 profile 检索 = 该 profile 的 `ACTIVE` 事实 + `GLOBAL` `ACTIVE` 事实；按 salience/recency/confidence 排序，按预算截断。MVP 用 type/key + 关键词/标签匹配（向量检索留作未来增强）。
- **forget/decay**：周期性衰减 salience、过期 `expiresAt`、每类事实数量封顶做修剪。
- **consolidate**：合并重复、把情景记忆提炼为语义事实。
- **switchProfile(profileId)**：切换激活档案；此后 recall/分析/作答都以新方向为准。

### 处理你举的例子（Java 后端 → Android）
- 推荐用**新建 profile "Android 开发"并切换激活**（两个方向并行、互不污染；GLOBAL 事实如年限自动共享）。
- 若在同一 profile 内改口，则 `target_role` 事实被 `SUPERSEDED`，旧值留档。
- 分析相关性时用**激活 profile 的 target_role + 关联简历 + recall 的事实**，所以切到 Android 后，"相关题目"立即以 Android 口径重算。

## 4. 记忆抽取（LLM 辅助，mock 可测）

- `ResumeMemoryExtractor`：从简历解析出 GLOBAL/PROFILE 事实（技能、年限、项目、目标岗位）。
- `ConversationMemoryExtractor`：从对话识别记忆变更意图（如"我现在想投安卓"）→ 产出 `MemoryFact` 候选，交 `MemoryManager.remember` 处理。
- 二者经 `AiClient`，返回**结构化候选**；单测用 `FakeAiClient` + 夹具响应验证抽取与冲突处理。
- **用户可控**：变更默认应用但保留历史；提供"记忆管理"界面查看/编辑/删除/手动增记，避免 AI 记错。

## 5. 记忆进入上下文（更新 ContextAssembler）

发送给模型的 `messages`：
```
[system: 角色与任务约束]
[system: 激活档案上下文 = target_role + recall 的关键 ACTIVE 事实]
[system: 长期会话摘要 (若有)]
[…会话窗口历史…]
[user: 当前输入]
```
无状态分析流水线（抽题→相关性→作答）也从 `recall` 注入激活档案的事实，使相关性判定与作答贴合当前方向。

## 6. 隐私

全部本地存储；支持按 profile 查看/编辑/删除记忆、归档/删除方向、（未来）切换简历；一键清空。

## 7. 数据模型汇总（追加到项目规划第 8 节 / P3 Room）
```text
Resume(id, title, rawText, parsedProfileJson, createdAt)                 // 可多份
CareerProfile(id, name, targetRole, resumeId?, isActive, status, createdAt, updatedAt)
MemoryFact(id, scope, profileId?, type, key, value, confidence, salience,
           source, status, supersedesId?, createdAt, updatedAt, lastUsedAt, expiresAt?)
MemoryEvent(id, profileId?, type, content, relatedFactId?, createdAt)
Conversation(id, title, profileId?, questionId?, createdAt, updatedAt)
ChatMessage(id, conversationId, role, content, tokenCountApprox, createdAt)
MemorySummary(conversationId, summary, coveredUpToMessageId, updatedAt)
```

## 8. 纳入路线图（替换/扩展 P3.5，测试先行）

- [ ] Room 实体与 DAO：`CareerProfile`、`MemoryFact`、`MemoryEvent`、`Conversation`、`ChatMessage`、`MemorySummary`；`Resume` 支持多份。
- [ ] `MemoryManager`：`remember`（含 supersede 冲突处理）、`recall`（scope 过滤+排序+预算）、`decay/prune`、`switchProfile` + 单测。
- [ ] `ResumeMemoryExtractor` / `ConversationMemoryExtractor`（`AiClient`）+ 单测（`FakeAiClient` 夹具）。
- [ ] `ChatMemory` 三策略 + `TokenEstimator` + 单测（见 ai-framework.md）。
- [ ] `ContextAssembler`：注入激活档案事实 + 摘要 + 窗口 + 当前输入 + 单测。
- [ ] `ConversationRepository` 会话 CRUD + 单测（in-memory Room）。

### 验收标准（测试先行，JVM 单测全绿、不联网、无真实 Key）
- **取代逻辑**：写入同 key 新值 → 旧事实变 `SUPERSEDED`、新值 `ACTIVE`、`supersedesId` 链正确、历史可查。
- **方向切换**：建/切 profile 后，`recall` 只返回该 profile + GLOBAL 的 ACTIVE 事实；旧方向事实不泄漏到新方向。
- **相关性随记忆变化**：给定同一批题目，激活 Java 后端 vs Android 档案时，注入的档案上下文不同（断言 `recall`/`ContextAssembler` 输出差异）。
- **抽取+冲突**：`ConversationMemoryExtractor` 从"改投安卓"文本产出候选 → `remember` 正确取代 target_role。
- **衰减/修剪**：超额/过期事实被正确降权或清理。
- 会话窗口/摘要/上下文顺序正确。
