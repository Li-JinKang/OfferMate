# OfferMate

<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" width="96" alt="OfferMate logo" />
</p>

<p align="center">
  一款<strong>纯端侧</strong> Android 应用：分享一条牛客 / 小红书面经链接，AI 帮你抽取面试题、匹配简历相关度、生成参考答案，并通过"先答后看"的刷题机制帮你复习。
</p>

<p align="center">
  <img src="https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/minSdk-26-blue" />
  <img src="https://img.shields.io/badge/language-Kotlin-7F52FF?logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4" />
</p>

<!-- TODO: 在这里放一张 App 首页/整体效果截图 -->
<p align="center">
  <img src="docs/screenshots/home.png" width="280" alt="首页截图占位" />
  <img src="docs/screenshots/quiz.png" width="280" alt="刷题模式截图占位" />
  <img src="docs/screenshots/chat.png" width="280" alt="AI 追问截图占位" />
</p>

## 目录

- [这是什么](#这是什么)
- [核心能力](#核心能力)
- [工作流程](#工作流程)
- [功能预览](#功能预览)
- [技术架构](#技术架构)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [关于 AI Key（BYOK）](#关于-ai-keybyok)
- [隐私说明](#隐私说明)
- [路线图](#路线图)

## 这是什么

求职期间刷"面经"（面试经验帖）是件苦活：帖子里的问题夹杂在大段文字/截图中，哪些跟自己的岗位和技术栈相关、该怎么答，都得自己一条条筛。

**OfferMate 不是一个刷题库，也不会凭空"出题"**。它做的是更聚焦的事情：

1. **读取**：从你分享/粘贴的面经链接中提取帖子正文内容。
2. **抽取**：用 AI 从正文里识别出一道道离散的面试题。
3. **匹配**：结合你上传的简历，判断每道题与你的岗位/技术栈/项目背景的相关性，筛掉不相关的题目。
4. **作答**：为相关题目生成参考答案（可结合简历内容个性化）。
5. **复习**：进入刷题模式，题目与答案分离，自己先答再看参考答案，自评掌握程度。

整个应用**完全跑在设备本地，没有自建后端**。你在"设置"里填入自己的大模型 API Key（目前默认适配 DeepSeek，兼容 OpenAI 协议的服务商也可接入），App 直接从手机端调用模型接口，Key 加密存储在本地，不经过任何中间服务器。

更完整的产品思路见 [`docs/项目规划.md`](docs/项目规划.md)。

## 核心能力

- 📥 **一键导入**：从牛客 / 小红书 App 分享链接到 OfferMate，或直接粘贴链接。
- 🧠 **AI 分析流水线**：题目抽取 → 简历相关性筛选 → 参考答案生成，三阶段解耦、可独立测试。
- 📄 **简历理解**：支持 PDF / 纯文本简历导入，AI 会将简历结构化为技能、项目、经历等字段，用于相关性打分和个性化作答。
- 🖼️ **图文混排识别**：帖子中的截图使用 ML Kit 中文文字识别（OCR）提取文字，避免漏题。
<!-- TODO: 若有分类/标签截图，可放在此处 -->
- 🗂️ **题目分类管理**：按类别整理已抽取的题目，方便按主题复习。
- 📝 **刷题自测**：答案默认隐藏，自己作答后再展示参考答案，自评掌握度。
- 💬 **追问 / AI 对话**：针对某道题或某份简历发起进一步提问，AI 具备记忆与工具调用能力（可结合 MCP 外部工具扩展）。
- 🔒 **BYOK，无后端**：API Key 使用 Android Keystore 加密存储在本机，所有网络请求由设备直连模型服务商。

## 工作流程

```
分享/粘贴链接
      │
      ▼
 内容读取（HTTP 抓取 / WebView 渲染 / OCR 识别图片）
      │
      ▼
 AI 题目抽取 ──▶ 结合简历做相关性匹配 ──▶ 生成参考答案
      │
      ▼
 保存到本地题库（按分类整理）
      │
      ▼
 刷题模式：先答 → 自评 → 看参考答案
      │
      ▼
（可选）针对某题发起 AI 追问 / 对话
```

## 功能预览

<!-- TODO: 按下面的表格逐个替换为实际截图 -->

| 首页 / 导入 | 题目列表 | 刷题模式 | AI 追问 |
|---|---|---|---|
| ![首页占位](docs/screenshots/placeholder.png) | ![题目列表占位](docs/screenshots/placeholder.png) | ![刷题模式占位](docs/screenshots/placeholder.png) | ![AI追问占位](docs/screenshots/placeholder.png) |

## 技术架构

- **UI 层**：Jetpack Compose + Navigation Compose，按功能拆分为 `home` / `questions` / `quiz` / `followup` / `aichat` / `profile` 等页面模块。
- **依赖注入**：手写 DI（`di/AppContainer.kt`），未引入 Hilt/Dagger，组合根统一装配各 Repository、AI 客户端与后台任务。
- **数据层**：
  - Room 持久化帖子 / 题目 / 分类 / 对话记录。
  - DataStore + `EncryptedSharedPreferences` 存储设置与密钥。
  - Jsoup / WebView / Readability 提取网页正文，ML Kit 完成图片 OCR。
  - PdfBox-Android 解析 PDF 简历。
- **AI 层（`agent/`）**：
  - `AiClient` 抽象出统一的对话接口，`DeepSeekClient` 是当前的 OpenAI 兼容实现，支持普通对话、工具调用（Function Calling）与流式输出（SSE）。
  - `AnalysisPipeline` 串联 `QuestionExtractor → RelevanceMatcher → AnswerGenerator` 三个阶段，完成"抽题 → 筛选 → 作答"。
  - `ResumeStructurer` 把简历原文结构化为 JSON（技能 / 项目 / 经历等），供相关性匹配和记忆系统使用。
  - `ToolCallingAgent` 是通用的 ReAct 风格工具调用循环，驱动追问/对话场景，支持本地工具（记忆检索、分类查询等）与 MCP 外部工具接入。
- **后台任务**：WorkManager（`AnalyzePostWorker`）在后台执行分析流水线，配合前台服务通知分析进度。

## 技术栈

| 分类 | 选型 |
|---|---|
| 语言 | Kotlin |
| UI | Jetpack Compose, Material 3, Navigation Compose |
| 异步 | Kotlin Coroutines |
| 网络 | OkHttp, Retrofit + kotlinx.serialization |
| 本地存储 | Room, DataStore Preferences, AndroidX Security Crypto |
| 后台任务 | WorkManager |
| 网页内容提取 | Jsoup, readability4j, WebView |
| OCR | ML Kit 中文文字识别 |
| 简历解析 | PdfBox-Android |
| 图片加载 | Coil |
| Markdown 渲染 | multiplatform-markdown-renderer (m3) |
| 构建 | AGP 8.7, Gradle Version Catalog, KSP |

- `compileSdk` / `targetSdk`：35
- `minSdk`：26
- JVM Target：17

## 项目结构

```
app/src/main/java/com/jk/offermate/
├── agent/          # AI 核心：LLM 客户端、分析流水线、工具调用 Agent、简历结构化
│   └── chat/       # 追问对话的记忆与上下文管理
├── data/
│   ├── reader/     # 链接内容抓取与正文提取（HTML/WebView/小红书专用解析）
│   ├── ocr/        # ML Kit 图片文字识别
│   ├── resume/     # 简历文件解析与存储
│   ├── local/      # Room 数据库
│   ├── repository/ # Room 仓库实现
│   ├── memory/     # 面向 AI 工具的分层记忆存储
│   ├── importer/   # 导入链接的编排逻辑
│   ├── settings/   # 设置与密钥加密存储
│   └── dedup/      # 帖子/题目去重
├── domain/         # 领域模型与仓库接口
├── di/             # 手写依赖注入组合根
├── share/          # 分享 Intent 解析
├── ui/             # Compose 页面（首页/题目/刷题/追问/个人资料等）
└── work/           # WorkManager 后台分析任务与通知
```

## 快速开始

### 环境要求

- Android Studio（建议使用支持 AGP 8.7 / Kotlin 2.0 的最新稳定版）
- JDK 17
- 一台 Android 8.0（API 26）及以上的设备或模拟器

### 构建与运行

```bash
git clone <本仓库地址>
cd OfferMate
./gradlew assembleDebug
```

构建过程**不需要**任何密钥或额外配置文件，直接可编译运行。

首次启动后，进入「设置」页面填入你自己的模型 API Key（详见下一节），即可开始使用。

## 关于 AI Key（BYOK）

OfferMate 采用 **BYOK（Bring Your Own Key）** 模式：

- 应用本身不内置、不代理任何大模型密钥，也没有服务端。
- 你需要自行申请一个兼容 OpenAI 接口协议的模型服务 API Key（默认适配 [DeepSeek](https://platform.deepseek.com/)），在设置页填入。
- Key 通过 Android Keystore 加密后存储在本机（`EncryptedSharedPreferences`），所有对模型的请求均由你的设备直接发出。
- 模型调用产生的费用由你自己承担，与开发者无关。

## 隐私说明

- 所有帖子内容、简历数据、题目与答案均存储在本机数据库，不会上传到任何第三方服务器（模型 API 调用除外，调用对象是你自己配置的模型服务商）。
- 简历/密钥等敏感数据在本机以加密方式保存。

---

<!-- TODO: 如需要，可在此补充 License 说明 -->
