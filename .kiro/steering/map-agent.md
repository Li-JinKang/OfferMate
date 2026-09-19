---
inclusion: auto
name: AI 流水线与工具轮
description: 当需求涉及 AI 分析流水线（抽题/相关性/作答）、新增或修改工具（Tool / function calling）、工具轮循环与流式输出、MCP 服务器接入、简历记忆的读写与分级加载、Prompt 与结构化输出解析、AI 日志排查时激活。
---

# 代码地图 · AI 流水线与工具轮

完整地图见 `docs/kb/map/agent-tools.md`，**动手前请先读它**。

#[[file:docs/kb/map/agent-tools.md]]

## 三条最容易踩的

1. **Prompt 里不注入简历**，模型靠工具按需取数（ADR-0003）。要给 AI 新增能力，默认做法是**加一个 `Tool`**：实现 + 注册进 `AppContainer.localTools` 两处即可，三个消费方自动生效。
2. `read_resume` / `ResumeReaderTool` **已删除**，但测试里仍用 `read_resume` 当虚构工具名——别据此以为它还存在。
3. 工具的失败一律**回填可读字符串给模型**（未知工具 / 参数缺失 / 超时），不要抛异常；id 类参数要做容错解析（见 `docs/kb/pitfalls/memory-detail-id-mismatch.md`）。
