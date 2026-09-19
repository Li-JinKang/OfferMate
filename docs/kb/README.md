# OfferMate 项目知识库

给 **AI agent 与新同学**用的工作知识库：接到需求时先读这里，避免每次从零探索仓库；开发过程中踩的坑沉淀在这里，避免重犯。

搭建规划与设计取舍见 [`../plan/knowledge-base.md`](../plan/knowledge-base.md)。

## 核心原则

1. **单一真源是代码**。知识库只写"代码里读不出来的东西"——意图、约束、坑、决策。不复述函数签名、不抄实现细节，那些交给 agent 现场读。
2. **不写进度**。"某功能是否已实现"必须查代码。`docs/plan/` 是意图文档，会滞后。
3. **无人引用的卡是负资产**。臃肿的常驻上下文会降低任务成功率、推高成本。库越大越要敢删。

## 目录

| 目录 | 内容 | 组织维度 |
|---|---|---|
| `map/` | 代码地图：一类需求要动哪些文件、有什么约束 | **按需求类型**，不按模块 |
| `decisions/` | ADR：为什么这么做、否决了什么 | 一决策一卡，不可变，只 supersede |
| `pitfalls/` | 坑位卡：现象 → 根因 → 规避规则 → 证据 | 一坑一卡 |
| `inbox/` | agent 自动沉淀的候选，**未经人工审阅** | 临时区 |

## 信任分层

| 等级 | 含义 | 可否作为实现依据 |
|---|---|---|
| `L0` | 机器可验证（锚点存在 + 校验脚本通过） | ✅ |
| `L1` | 人工确认过的决策/经验 | ✅ |
| `L2` | agent 提出的候选（只在 `inbox/`） | ❌ **不得作为依据**，需人工审阅后升级 |

## front-matter 规范

每张卡必须以此开头：

```yaml
---
trust: L1
anchors:
  - app/src/main/java/com/jk/offermate/ui/components/PuzzleGrid.kt
  - app/src/main/java/com/jk/offermate/ui/quiz/QuizScreen.kt#categoryColor
verified_commit: 63c65fb
verified_at: 2026-09-18
supersedes: null
---
```

| 字段 | 说明 |
|---|---|
| `trust` | `L0` / `L1` / `L2` |
| `anchors` | 本卡描述的代码。`路径` 或 `路径#符号名`。校验脚本据此判失效，**必须真实存在** |
| `verified_commit` | 最后一次人工核对时的 HEAD 短 sha |
| `verified_at` | 同上的日期 |
| `supersedes` | 取代了哪张卡（文件名，无则 `null`） |

## 失效判定（`tools/kb-verify.sh`）

| 状态 | 判据 | 处理 |
|---|---|---|
| `STALE` | 锚点文件或符号已不存在 | **硬错误**，脚本退出码非 0。必须修卡或删卡 |
| `SUSPECT` | `verified_commit..HEAD` 之间有 commit 改过锚点文件 | 软告警。**读卡前必须先核对代码** |
| `OK` | 锚点齐全且自核对以来未变动 | 可直接信任 |

不按时间过期：纯时间衰减会误伤长期稳定的知识，又漏掉高频变动的热点。失效的真正原因是**被描述的代码变了**，就直接测这件事。

```bash
tools/kb-verify.sh            # 全量校验
tools/kb-verify.sh --quiet    # 只输出问题
```

## 写入规则

| 谁 | 能写什么 |
|---|---|
| 人 | 任何目录 |
| agent | 只能写 `inbox/`；主库卡片仅允许在**核对无误后**更新 `verified_commit` / `verified_at` |

补充约束：

- **坑位卡必须带证据**（commit sha / 测试名 / 日志片段）。没有可追溯证据的"经验"不收录——这是防污染的第一道闸。
- **冲突不覆盖**：新知识与旧卡矛盾时，新建卡并写 `supersedes: <旧卡文件名>`，旧卡顶部标注 `> ⚠️ 已被 xxx.md 取代`，保留审计链。
- **篇幅上限**：单卡 ≤150 行；`.kiro/steering/00-project.md`（常驻注入）≤120 行。超了就拆或删。
- **`inbox/` 不积压**：每 5~10 个需求审一次，每条只有三种结局——合并进主库 / 升级为 L1 独立卡 / 丢弃。

## 卡片模板

### map/

```markdown
# <需求类型>

## 一句话
这类需求的改动通常落在哪一层。

## 涉及文件
| 文件 | 职责 |

## 数据流
（从数据源到 UI，或从入口到落库）

## 改动清单：<某类改动>
1. 必须改 X
2. 必须同步 Y（否则 Z）

## 约束与易错点
- …

## 相关单测
- …
```

### decisions/

```markdown
# ADR-NNNN <决策标题>

## 背景
## 备选方案与否决理由
## 决策
## 影响
## 后续若要推翻，需要满足什么条件
```

### pitfalls/

```markdown
# <坑的一句话描述>

## 现象
## 根因
## 规避规则
（可直接照做的规则，不是"注意一下"）

## 证据
- commit / 测试名 / 日志

## 触发条件
什么场景下会再次遇到。
```

## 索引

地图（`map/`）
- [`map/quiz-puzzle.md`](./map/quiz-puzzle.md) — 题库页 / 拼图 / 分类 / 刷题
- [`map/import-pipeline.md`](./map/import-pipeline.md) — 链接导入 → 读取 → OCR → 分析 → 落库
- [`map/agent-tools.md`](./map/agent-tools.md) — AI 流水线 / 工具轮 / MCP / 记忆工具

决策（`decisions/`）
- [`decisions/0001-manual-di-over-hilt.md`](./decisions/0001-manual-di-over-hilt.md) — 手动 DI 取代 Hilt
- [`decisions/0002-memory-file-store.md`](./decisions/0002-memory-file-store.md) — 记忆层用分层文件而非 Room
- [`decisions/0003-relevance-not-precomputed.md`](./decisions/0003-relevance-not-precomputed.md) — 相关性不预存、不连锁重算

坑位（`pitfalls/`）
- [`pitfalls/http2-shared-connection.md`](./pitfalls/http2-shared-connection.md) — 并发任务共享 HTTP/2 连接连坐
- [`pitfalls/coroutine-runcatching-cancellation.md`](./pitfalls/coroutine-runcatching-cancellation.md) — `runCatching` 吞掉协程取消
- [`pitfalls/room-column-migration.md`](./pitfalls/room-column-migration.md) — 加列不升版本 / 升版本清库
- [`pitfalls/compose-zoom-nested-scroll.md`](./pitfalls/compose-zoom-nested-scroll.md) — 缩放图片吞掉外层滚动
- [`pitfalls/resume-emptiness-check.md`](./pitfalls/resume-emptiness-check.md) — 简历"已配置"判据是 `rawText`
- [`pitfalls/memory-detail-id-mismatch.md`](./pitfalls/memory-detail-id-mismatch.md) — 模型按显示名调工具导致失败
