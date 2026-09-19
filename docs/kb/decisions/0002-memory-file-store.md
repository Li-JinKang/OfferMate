---
trust: L1
anchors:
  - app/src/main/java/com/jk/offermate/data/memory/MemoryStore.kt
  - app/src/main/java/com/jk/offermate/data/memory/MemoryModels.kt
  - app/src/main/java/com/jk/offermate/data/memory/ResumeIngestor.kt
  - app/src/main/java/com/jk/offermate/agent/tool/MemoryTools.kt#memoryTools
verified_commit: 63c65fb
verified_at: 2026-09-19
supersedes: null
---

# ADR-0002 简历记忆用分层文件，不用 Room；多方向记忆集永久共存

## 背景

初稿设计是"三层记忆（工作/情景/语义）+ Room 三表（`CareerProfile` / `MemoryFact` / `MemoryEvent`）+ `MemoryManager`（`remember` / `recall` / `supersede` / `decay` / `prune` / `switchProfile`）+ 激活档案切换"。

典型场景：用户先投 Java 后端，后又投 Android，两份背景要并存；遇到某道题时要能挑出相关那份。

## 备选方案与否决理由

| 方案 | 否决理由 |
|---|---|
| Room 三表 + 语义事实 | 记忆的访问模式是**自上而下逐层下钻，几乎没有横切查询**（"列出所有 target_role 为 X 的事实"这种需求不存在）。关系表的查询能力用不上，却要付出 schema 迁移的代价——而本项目升 DB 版本会清库（见 `pitfalls/room-column-migration.md`）。 |
| 激活档案 + `switchProfile` | 需要用户手动切换或弹窗确认，是**把系统的不确定性转嫁给用户**。而"哪份记忆与这道题相关"本来就是模型最擅长判断的事。 |
| 事实级 `supersede` 链 + `decay/prune` | 为"记忆事实"建立生命周期管理，复杂度很高；而用户真正需要的只是"当前简历是什么"。历史版本追溯不是需求。 |
| 预存相关性（简历变更后批量重算题目相关性） | 成本与简历改动频率成正比，且会让简历系统与题目系统强耦合。 |

## 决策

**分层 Markdown 文件 + 多记忆集永久共存 + 能力暴露为分级 tool，由 AI 编排。**

```
filesDir/memory/
  index.json                     所有记忆集索引 [{id,name,targetRole,summary}]
  global.md                      跨方向共享事实（年限/学历/语言）
  <profileId>/profile.md         L2 概览：技能 + 项目/经历 brief
  <profileId>/projects/<id>.md   L3 细节
  <profileId>/experiences/<id>.md
```

配套取消的能力（**这些不是欠项，是设计上不需要**）：

| 原设想 | 现状 |
|---|---|
| `switchProfile` | 取消。无"激活档案"概念；写入时由 `ProfileMatcher` 判方向，读取时由 AI 挑 |
| `recall` | 取消。相关性不预存，AI 现场调 tool 拉当前状态判断 |
| `supersede` 冲突链 | 取消。同方向简历改版直接覆盖文件 |
| `decay` / `prune` | 取消。由用户在设置页手动删除 |
| 简历变更 → 相关性连锁重算 | 取消。"跟着变"是实时读取的结果，不是批量重算 |

**首要原则：简历/记忆系统与题目系统彼此不感知**，唯一连接点是 AI —— 双方各自把能力注册成 tool 进共享 `ToolRegistry`，由模型按需编排。

## 影响

- 记忆内容是**人可读、可直接编辑**的 Markdown，设置页的记忆管理 UI 就是文件编辑器，导出/删除都是文件操作。
- 加记忆字段**不需要 DB 迁移**，不会清库。
- 记忆更新路径对题目表**零写操作**，不触发任何重算。
- 代价一：所有 id 必须做路径穿越防护（`MemoryIds.requireSafe`）。
- 代价二：模型传错 id 的问题需要在工具层兜（见 `pitfalls/memory-detail-id-mismatch.md`）。
- 代价三：**没有历史版本追溯**。同方向简历改版会覆盖旧文件。
- 会话/消息历史仍在 Room —— 那是追加型日志，需要事务与分页，性质不同。判断依据：**内容是文档就用文件，是记录就用数据库。**

## 后续若要推翻，需要满足什么条件

出现真实的横切查询需求（例如"跨所有方向统计技能出现频次"并且要高频调用），或需要记忆事实的版本审计。仅仅"想要结构化"不是理由。

详细设计见 [`../../plan/memory.md`](../../plan/memory.md)。
