---
trust: L1
anchors:
  - app/src/main/java/com/jk/offermate/agent/pipeline/RelevanceMatcher.kt
  - app/src/main/java/com/jk/offermate/agent/pipeline/AnswerGenerator.kt
  - app/src/main/java/com/jk/offermate/di/AppContainer.kt#sharedToolRegistry
verified_commit: 63c65fb
verified_at: 2026-09-19
supersedes: null
---

# ADR-0003 Prompt 不注入简历，AI 按需调工具取数

## 背景

早期做法是把整份简历（或"最小画像 + `rawText` 截断 2000 字"）塞进每次相关性筛选与作答的 Prompt。问题：

- 每道题都重复发一遍简历，token 成本随题量线性增长；
- 上下文被大量无关信息污染，模型注意力被稀释；
- 多方向求职（Java 后端 / Android）时，塞哪一份都不对。

## 备选方案与否决理由

| 方案 | 否决理由 |
|---|---|
| 塞简历全文 | 成本高、污染上下文、多方向无解 |
| 塞"最小画像"（岗位 + 技能 + 项目名） | 画像字段在简历页改版后常为空（见 `pitfalls/resume-emptiness-check.md`）；且信息量不足时模型只能猜 |
| 预先为每道题算好相关性并存库 | 简历一改就要全量重算，成本与改动频率成正比，还让两个系统强耦合 |
| 端侧 embedding 检索简历片段 | 需要引入模型与向量存储，端侧包体与复杂度代价大；当前简历体量下，让模型自己按需读文件足够 |

## 决策

**Prompt 里不注入任何简历文本。** `RelevanceMatcher.buildMessages` / `AnswerGenerator.buildMessages` 只发题目列表，模型通过共享 `ToolRegistry` 里的记忆工具按需分级拉取（`list_memory_profiles` → `load_profile_overview` → `load_*_detail`）。

旧的 `read_resume` / `ResumeReaderTool` 已删除，统一走记忆工具。

**推论：新能力优先做成工具，而不是扩充 Prompt。** 这条已写进项目宪法红线。

## 影响

- 题量增长时简历只在模型认为需要时被读取，成本不再线性叠加。
- 多方向求职天然支持：模型自己挑相关那份。
- 改了简历，**下次调用即见新状态**，无需任何题目侧动作——"跟着变"是实时读取的结果。
- 代价一：多了工具轮的往返，单题延迟上升。这是刻意用延迟换准确性与成本。
- 代价二：**依赖 provider 支持 function calling**。不支持时（用户填的 baseUrl 指向的服务忽略 `tools` 字段）模型拿不到任何个人背景，只能按题目通用性给保守评分。这条降级要求写在 system prompt 里。
- 代价三：模型可能"偷懒不调工具"。对策是在 system 里用强指令要求先调用（见 `FollowUpService.systemContext`），并靠 `AgentLogger`（TAG `OfferMateAI`）观察实际调用情况。
- 代价四：工具描述成了关键资产 —— 模型只靠 `ToolSpec.description` 决定调不调、怎么传参。

## 后续若要推翻，需要满足什么条件

实测发现模型在充分提示下仍大比例不调记忆工具，导致相关性质量显著低于"直接注入画像"。届时的正确方向是**补一份极简画像作为兜底**（岗位 + 3~5 个技能），而不是回到塞全文。
