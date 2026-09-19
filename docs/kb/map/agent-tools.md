---
trust: L1
anchors:
  - app/src/main/java/com/jk/offermate/agent/AiClient.kt
  - app/src/main/java/com/jk/offermate/agent/DeepSeekClient.kt#parseTurn
  - app/src/main/java/com/jk/offermate/agent/tool/ToolCalling.kt#LlmTurn
  - app/src/main/java/com/jk/offermate/agent/tool/Tool.kt#ToolRegistry
  - app/src/main/java/com/jk/offermate/agent/tool/ToolCallingAgent.kt#executeCalls
  - app/src/main/java/com/jk/offermate/agent/tool/MemoryTools.kt#memoryTools
  - app/src/main/java/com/jk/offermate/agent/tool/InlineToolCallParser.kt
  - app/src/main/java/com/jk/offermate/agent/pipeline/AnalysisPipeline.kt
  - app/src/main/java/com/jk/offermate/agent/mcp/McpToolRepository.kt
  - app/src/main/java/com/jk/offermate/data/memory/MemoryStore.kt#resolveDetailId
  - app/src/main/java/com/jk/offermate/di/AppContainer.kt#sharedToolRegistry
  - app/src/main/java/com/jk/offermate/agent/JsonSupport.kt
verified_commit: 63c65fb
verified_at: 2026-09-19
supersedes: null
---

# AI 流水线 / 工具轮 / MCP / 记忆工具

## 一句话

Prompt 里**不塞简历**——模型通过工具按需取数。想给 AI 新增能力，默认做法是**加一个 `Tool`**，只需改两处（实现 + `AppContainer.localTools`），三个消费方自动获得。

## 关键端口

| 抽象 | 文件 | 要点 |
|---|---|---|
| `AiClient` | `agent/AiClient.kt` | `suspend fun chat(messages): String`，简单补全 |
| `ToolCallingLlm` | `agent/tool/ToolCalling.kt` | `chat(messages, tools): LlmTurn` |
| `StreamingLlm` | 同上 | `chatStream(messages, tools, onDelta): LlmTurn`。约定：这轮若要调工具就**不回调任何文本增量** |
| `LlmTurn` | 同上 | `Final(text)` / `ToolInvocations(calls)` |
| `Tool` / `ToolRegistry` | `agent/tool/Tool.kt` | `ToolRegistry(provider)` **每次访问都重新求值 provider** |
| `ChatMessage` / `Role` | `agent/AiClient.kt` | `Role` 含 `TOOL`；消息可带 `toolCalls` / `toolCallId` |
| `DeepSeekClient` | `agent/DeepSeekClient.kt` | 唯一生产实现，同时实现上面三个端口 |

`ToolRegistry` 的 provider 惰性求值很关键：MCP 异步刷新完、记忆文件被改过，**下一轮工具轮自动看到新状态**，不需要重建注册表。

## 新增一个本地工具

1. 建 `agent/tool/XxxTool.kt` 实现 `Tool`：`spec = ToolSpec(name, description, parametersJson)` + `call(argumentsJson)`。
   - 惯例：数据来源用**函数注入**（如 `QuestionSearchTool(search = …)`），与存储解耦便于 JVM 单测。
   - 参数解析走 `JsonSupport.json`；缺参/空结果**返回可读中文字符串，不要抛异常**（异常会变成"工具执行失败"，模型拿不到有用信息）。
   - `description` 要写清"什么时候该调我"，模型只靠它决策。
2. 注册进 `AppContainer.localTools`。共享注册表是
   `ToolRegistry { localTools + memoryTools(memoryStore) + mcpToolRepository.current() }`，
   三个消费方自动生效：`RelevanceMatcher`、`AnswerGenerator`、`FollowUpService`。
3. **不需要**改 UiState 或加设置开关——本地工具没有开关，只有 MCP 服务器才有设置项。
4. 单测：本地工具加进 `agent/tool/LocalToolsTest.kt`（`runTest` + 直接 `tool.call(json)` 断言输出片段 + 一条 spec name 断言）；记忆类工具在 `MemoryToolsTest.kt`（临时目录 + 真实 `MemoryStore`）。

## 工具轮循环（`ToolCallingAgent`）

默认 `maxSteps = 15`、`toolTimeoutMs = 20_000`。

```
repeat(maxSteps):
  llm.chat(conversation, tools.specs())
    Final           → 返回文本
    ToolInvocations → executeCalls 回填后继续
超出步数 → 再发一次但 tools 传 emptyList()（逼收敛）
         → 仍要调工具则返回 STEP_LIMIT_FALLBACK 常量，绝不把裸标记透给用户
```

`executeCalls` 的三种"软失败"，全部**回填字符串而不抛异常**，让模型自己纠正：

| 情况 | 回填内容 |
|---|---|
| 工具不存在 | `未知工具：<name>`，循环继续 |
| `required` 参数缺失（`ToolArgumentValidator`） | 错误说明，**且不执行工具** |
| 超时 / 抛异常 | `工具执行超时：…` / `工具执行失败：…` |

`runStreaming` 首行就做降级：`llm as? StreamingLlm ?: return run(...)`。

### 流式的三个兜底组件

| 组件 | 职责 |
|---|---|
| `ToolCallAccumulator` | SSE 把一次工具调用拆成多个 delta，按 `index` 归并 |
| `InlineToolCallParser` | 模型把工具调用当**文本**吐进 content（`<invoke …>` 伪标记）时解析出来；`strip` 从展示文本剔除 |
| `StreamingTextBuffer` | 文本里一出现 `invoke`/`tool_calls` 就进静默模式，避免伪标记逐字闪现 |

`DeepSeekClient` 的判定顺序：结构化 `tool_calls` → 内联伪标记 → 才当最终文本。

## 分析流水线

`AnalysisPipeline(extractor, matcher, answerer, relevanceThreshold = 60)`，三步短路：抽题空 → 返回空；相关性过滤后空 → 返回空；否则作答。

**工具轮可用 vs 不可用的差异只在取数，不在 prompt 分支**：
`buildMessages` 只发题目列表，**不注入任何简历文本**（`read_resume` 已删除）。门控是
`toolsEnabled = toolCallingLlm != null && !toolRegistry.isEmpty()`；不可用时退回 `aiClient.chat`，此时模型拿不到任何个人背景，只能按题目通用性给保守评分。

解析容错：先 `JsonSupport.extractJsonBlock` 整体解析，失败才 `salvageObjects` 抢救已闭合对象，抢救也空才抛 `AiException`。注意"解析成功但数组为空"视为模型明确结果，不算失败。

## 记忆工具族（L1→L2→L3）

| 工具 | 层级 | 读什么 |
|---|---|---|
| `list_memory_profiles` | L1 | `index.json` 全部方向记忆 |
| `load_profile_overview(profileId, query?)` | L2 | `<profileId>/profile.md` + `global.md` |
| `load_project_detail(profileId, projectId)` | L3 | `<profileId>/projects/<id>.md` |
| `load_experience_detail(profileId, experienceId)` | L3 | `<profileId>/experiences/<id>.md` |

存储在 `filesDir/memory/`，全部 id 经 `MemoryIds.requireSafe` 防路径穿越。L3 走 `MemoryStore.resolveDetailId` 做容错解析（见 `pitfalls/memory-detail-id-mismatch.md`）。

## MCP

`McpClient`（端口）/ `HttpMcpClient`（JSON-RPC over Streamable HTTP，`Mutex` 保护一次性 initialize + 记 `Mcp-Session-Id`）/ `McpTool`（适配成本地 `Tool`）/ `McpToolRepository`（多台发现，**单台失败只跳过该台**）。

- 对外工具名被命名空间化为 `mcp_<server>_<tool>`（`sanitizeToolName` 满足 `^[a-zA-Z0-9_-]{1,64}$`，重名追 `_1`），调用时由 `McpTool` 转回原名。
- 配置存 DataStore key `mcp_servers`，UI 在设置页「MCP 工具服务器」。
- 刷新点：`OfferMateApplication` 启动时 + 设置页手动刷新。

## 约束与易错点

1. **`toolCallingLlm` 实际永不为 null**（生产 `aiClient` 固定是 `DeepSeekClient`，实现了全部端口）。真正的失效场景是"用户填的 baseUrl 指向的服务不支持 `tools` 字段"——此时不会走 `toolsEnabled=false` 分支，而是服务端报错或模型吐内联伪标记（靠 `InlineToolCallParser` 兜底）。另一个真实回退条件是 `toolRegistry.isEmpty()`。
2. **`read_resume` / `ResumeReaderTool` 已删除**。但 `DeepSeekToolCallingTest` 与 `ToolCallingAgentTest` 仍用 `read_resume` 作虚构工具名——**不要据此以为它还存在**。
3. **`AnswerGenerator` 没注入 logger**（默认 `NoopAgentLogger`），所以作答步骤的工具轮是静默的。排查"模型有没有调工具"时别在这里找日志。日志 TAG 是 `OfferMateAI`（`adb logcat -s OfferMateAI`）。
4. **`maxSteps` 防啰嗦，`toolTimeoutMs` 防卡住**，两者不能互相替代。
5. **`ToolArgumentValidator` 只校验 `required` 存在且非空**，不做全量 schema 校验。工具内部仍要自己校验取值范围。
6. **流式请求用的 `streamClient` 摘掉了 `RetryInterceptor`**，且 `allowReplay = false`——增量已透出再重试会产生重复文本，其瞬时失败交由任务层整轮重试。
7. **`MAX_OUTPUT_TOKENS = 8192` 是显式拉满的**，因为默认 4096 在多题长答案时会截断导致 JSON 不完整。别随手调小。
8. `JsonSupport.extractJsonBlock` **只认显式 ```json 围栏**（避免把答案里的 ```java 代码块误当 JSON）。

## 相关单测

`ToolCallingAgentTest`（工具轮 / 未知工具 / 步数上限）、`FakeToolCallingLlm`、`LocalToolsTest`、`MemoryToolsTest`、`DeepSeekClientTest`、`DeepSeekToolCallingTest`、`JsonSupportTest`、`AnalysisPipelineTest`、`RelevanceMatcherTest`、`AnswerGeneratorTest`、`QuestionExtractorTest`、`CategoryClassifierTest`、`FollowUpServiceTest`、`HttpMcpClientTest`、`McpParsingTest`、`McpToolRepositoryTest`、`MemoryStoreTest`、`ResumeIngestorTest`。

⚠️ `InlineToolCallParser` / `ToolArgumentValidator` / `ToolCallAccumulator` **没有专项单测**，改这三个兜底组件要格外小心。
