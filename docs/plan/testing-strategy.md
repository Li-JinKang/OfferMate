# 测试策略与夹具规范

核心原则：**核心能力先在 `app/src/test`（JVM 单元测试）下通过，不依赖网络、真机、真实 Key。**

## 测试分层

| 层级 | 位置 | 是否纳入"测试先行"门槛 | 说明 |
|------|------|------------------------|------|
| JVM 单元测试 | `app/src/test` | ✅ 是（门槛） | 纯逻辑：Prompt 构造、LLM 响应解析、HTML 正文提取、相关性筛选、编排、DAO(in-memory) |
| Instrumented 测试 | `app/src/androidTest` | ❌ 否 | 需真机/模拟器：WebView 动态取文、真实 Room、UI |
| 集成测试（真实网络/Key） | `app/src/test` 或独立 | ❌ 否（默认 `@Ignore`） | 真实 DeepSeek 调用，手动开启用于质量核对 |

> "测试先行门槛"仅指 **JVM 单元测试**。P1、P2 的门槛单测未全绿前，不实现对应的真实网络/设备代码。

## 关键：如何在不联网的情况下测试"AI 核心能力"

AI 调用被抽象为 `AiClient` 接口。单测使用 `FakeAiClient`：
- 输入：`AnalysisPipeline` 传入的 messages/prompt。
- 输出：从 `fixtures/llm/*.json` 读取的**预录制响应**（模拟 DeepSeek 返回）。
- 断言点：
  1. Prompt 是否包含必要约束（要求 JSON、注入简历上下文、注入帖子正文）。
  2. 响应解析是否得到正确的领域对象（题目、分数、答案）。
  3. 编排是否正确串联三步、正确按阈值过滤。
  4. 畸形响应是否被健壮处理（抛 `AiException`，不崩溃）。

这样"抽题→相关性→作答"的全部业务逻辑都可被确定性验证，与模型是否真的联网无关。

## 夹具目录规范

```
app/src/test/resources/fixtures/
├── html/
│   ├── nowcoder_53245626677297352.html   # 牛客基准页
│   └── xhs_6Gz0nDGZxAE.html              # 小红书基准页（若可捕获）
├── llm/
│   ├── extract_response.json             # 抽题步骤的录制响应
│   ├── relevance_response.json           # 相关性步骤的录制响应
│   └── answer_response.json              # 作答步骤的录制响应
└── resume/
    └── sample_resume.txt                 # 示例简历文本
```

## 基准链接（本项目的验收样例）

- 牛客：`https://www.nowcoder.com/share/jump/53245626677297352`
- 小红书：`https://xhslink.cn/o/6Gz0nDGZxAE`

捕获方式（P2.1）：优先在真机/浏览器打开另存 HTML，或用命令行抓取；保存到 `fixtures/html/`。
- 牛客：预期静态可读，直接作为 `HtmlContentExtractorTest` 输入。
- 小红书：先解析短链重定向；若正文由 JS 渲染导致静态抓取为空/被拦，则在 `roadmap.md` P2.1 记录 `XHS: BLOCKED`，并向用户索取参考项目后确定读取实现，再补对应测试。

## 运行命令

```bash
./gradlew testDebugUnitTest        # JVM 单元测试（测试先行门槛）
./gradlew connectedDebugAndroidTest # instrumented 测试（需设备）
./gradlew assembleDebug            # 编译
```

## 每阶段"测试全绿"清单映射

- P1 门槛：`QuestionExtractorTest`、`RelevanceMatcherTest`、`AnswerGeneratorTest`、`AnalysisPipelineTest`、Prompt 快照、解析异常用例。
- P2 门槛：`HtmlContentExtractorTest`、`UrlResolverTest`、`ContentReaderTest`。
- P3 门槛：DAO 读写测试、`SecureKeyStore` 存取测试。
- P4 门槛：ViewModel `UiState` 流转测试。
