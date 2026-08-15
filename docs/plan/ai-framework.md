# AI 框架决策：端侧会话与记忆

> 前提：不部署本地模型（推理走远端 DeepSeek，OpenAI 兼容接口，BYOK）；但**会话与记忆在端侧管理**。

## 决策结论

- **LLM 客户端**：`AiClient` 抽象 + `DeepSeekClient`（Retrofit + OpenAI 兼容 `chat/completions`）。
- **会话/记忆编排**：**自研轻量层**，持久化到 Room，不引入重型 Agent 框架。
- **记忆模型**：借鉴 LangChain4j `ChatMemory` 概念（消息窗口 / Token 窗口 / 摘要记忆），自研实现以保证可测与轻量。
- **备选（暂不采用）**：Koog（Kotlin/KMP，Android 友好，较新）、LangChain4j（成熟但偏服务端、Android 有兼容摩擦）。若未来需要复杂多工具 Agent/RAG，再评估。

> 说明：候选框架的"最新版本 Android 兼容性"未经联网核实，本决策基于架构适配性、依赖体积与"测试先行"的可测性要求。

## 端侧会话与记忆设计

### 数据模型（Room，追加到项目规划第 8 节）
```text
Conversation(id, title, resumeId?, questionId?, createdAt, updatedAt)
ChatMessage(id, conversationId, role, content, tokenCountApprox, createdAt)
   role ∈ { system, user, assistant }
MemorySummary(conversationId, summary, coveredUpToMessageId, updatedAt)  // 长期/摘要记忆
```
- 一个 `Conversation` = 一次会话（可关联某道题或某份简历，用于追问，如"这道题追问怎么答""换个角度再答"）。
- `MemorySummary` 保存被裁剪掉的旧对话的滚动摘要，作为长期记忆。

### 记忆策略（纯逻辑，单元可测）
定义接口 `ChatMemory`：给定历史消息 + 当前输入，产出要发送给模型的 `messages`。

- `MessageWindowMemory(maxMessages)`：保留最近 N 条。
- `TokenWindowMemory(maxTokens, tokenEstimator)`：按 Token 预算保留最近若干条。
- `SummarizingMemory(budget, aiClient)`：超预算时，把最旧的若干条用 LLM 压缩成滚动摘要写入 `MemorySummary`，发送时用"摘要 + 最近窗口"。

`TokenEstimator`：端侧无 DeepSeek 分词器，用近似估算（英文按 ~4 char/token，CJK 按 ~1.5 char/token 的启发式），仅用于预算控制。抽象成接口便于替换与测试。

### 上下文组装（ContextAssembler）
发送给模型的 `messages` 顺序：
```
[system: 角色与任务约束]
[system: 长期记忆摘要 (若有)]
[…窗口内的历史 user/assistant 消息…]
[user: 当前输入]
```

### 与分析流水线的关系
- **无状态分析**（P1 的抽题→相关性→作答）：一次性任务，不需要会话记忆。
- **有状态会话**：用户对某道题的追问、澄清、再作答，走会话+记忆层，携带该题与简历画像作为上下文。
- 两者共用同一个 `AiClient`。

### 会话管理（ConversationRepository）
- 创建/列出/删除会话；追加消息；加载会话上下文。
- 全部本地持久化；支持导出/一键删除（隐私）。

## 纳入路线图的任务（插入到 roadmap 的 P3 之后、P4 之前，标记为 P3.5）

- [ ] Room：`Conversation` / `ChatMessage` / `MemorySummary` 实体与 DAO。
- [ ] `TokenEstimator`（启发式）+ 单测。
- [ ] `ChatMemory` 三种策略实现 + 单测（用 `FakeAiClient` 测 `SummarizingMemory`）。
- [ ] `ContextAssembler` 组装顺序 + 单测（断言 system/摘要/窗口/当前输入的拼装正确）。
- [ ] `ConversationRepository` 会话 CRUD + 追加消息 + 加载上下文 + 单测（in-memory Room）。

### 验收标准（测试先行）
- 记忆裁剪：给定超长历史，`MessageWindowMemory`/`TokenWindowMemory` 输出条数/Token 在预算内且保留最新。
- 摘要记忆：超预算时触发摘要（用 `FakeAiClient` 返回固定摘要），后续发送含摘要且旧消息被裁剪。
- 上下文顺序：`ContextAssembler` 输出顺序与角色正确。
- 会话 CRUD 与消息持久化正确。
- 全程不联网、无真实 Key。
