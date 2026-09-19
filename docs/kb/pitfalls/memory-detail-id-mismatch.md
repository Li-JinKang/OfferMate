---
trust: L1
anchors:
  - app/src/main/java/com/jk/offermate/data/memory/MemoryStore.kt#resolveDetailId
  - app/src/main/java/com/jk/offermate/agent/tool/MemoryTools.kt#DetailTool
verified_commit: 63c65fb
verified_at: 2026-09-19
supersedes: null
---

# 模型按"显示名"调工具，导致细节加载失败

## 现象

AI 在追问/分析时调 `load_project_detail`，传的 `projectId` 是中文项目标题或公司全名（如"某音视频播放器"），工具找不到文件，回复里声称不了解用户的项目背景。

## 根因

工具暴露给模型的概览文本里，条目更显眼的是**显示名**，而实际文件名是 slug 化的 id（`vibeplayer.md`）。模型倾向于用它"看到的名字"当参数。这不是模型的错——**参数语义没有在接口层被兜住**。

同类问题在 MCP 侧也存在：对外工具名被命名空间化成 `mcp_<server>_<tool>`，与服务器上的原名不同。

## 规避规则

1. **工具的 id 类参数必须做容错解析，不要直接当文件名用。** `MemoryStore.resolveDetailId` 做了四级匹配：
   精确 id → slug 化后的 id → 忽略大小写 → 与正文首个标题行（`# 标题`）相等/互相包含。
2. **失败时返回"可用 id 列表"而不是报错。** `DetailTool.call` 找不到就回 `（未找到项目：X。可用 id：a、b、c。请使用 load_profile_overview 概览里各条目的 id= 值）`，模型能据此自己重试。这比抛异常有用得多——异常只会变成"工具执行失败"。
3. **在 `ToolSpec.description` 里写清参数取值来源**（"必须取自概览里各条目前缀的 `id=` 值，不要用中文标题或公司全名"）。描述是模型唯一的说明书。
4. 概览文本里**把 id 显式暴露出来**（`- id=vibeplayer｜项目名｜…`），让模型有正确的东西可抄。
5. 新增任何"按 id 下钻"的工具时照这套做：容错解析 + 列出可用值 + 描述写明来源。

## 证据

- commit `de07e9f`「修复简历细节工具因 id 与显示名不一致而调用失败」。
- `MemoryStore.resolveDetailId` 的方法注释直接记录了这个动机（"容忍概览里只暴露了显示名、模型拿不到精确 id"）。
- `MemoryTools.DetailTool.detailSpec()` 的 description 里那句"不要用中文标题或公司全名"。
- `McpToolRepository.sanitizeToolName` / `uniqueName` 是同类问题的另一处体现。

## 触发条件

新增任何有"标识符参数"的工具。只要模型看到的名字和程序用的键不是同一个东西，就会复现。
