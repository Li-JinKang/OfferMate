# 端侧简历记忆子系统

> 目标：把用户的简历/背景做成**可长期共存、可结构化、可按需加载**的记忆，供 AI 在作答时逐层调用。典型场景：用户先投 Java 后端、后又投 Android，两份记忆并存互不覆盖；遇到某道题时，AI 自己挑相关的那份记忆、按需下钻到具体项目。全部在端侧（Room），可测（测试先行）。

关联：底层框架决策见 [`ai-framework.md`](./ai-framework.md)；本文件聚焦“简历记忆”。

## 0. 三个核心

1. **多简历/记忆共存**：每个求职方向（Java 后端、Android……）是一份独立记忆，**永久共存、互不覆盖**。方向变化不删旧记忆、不切换、不弹窗确认——只是再新增一份。没有“激活档案”这一概念。
2. **简历结构化**：用 AI 把简历原文解析成结构化数据（岗位、技能、项目、年限、教育……），作为记忆内容的真源。
3. **记忆能力暴露为 tool，按需 + 分级加载**：AI 不预先获得全部记忆，而是通过一族 tool 逐层拉取——先看有哪些记忆集、挑相关的，再取概览，必要时再下钻到具体项目。需要多少取多少。

## 1. 首要架构原则：双系统解耦，AI 编排

**简历/记忆系统** 与 **题目系统** 彼此独立、互不感知，无直接调用、无连锁触发、无为对方维护的派生数据。唯一连接点是 **AI**：各系统把“封装了自身能力的 tool”注册进共享工具轮（`ToolRegistry`），由 AI 按需调用编排。

```
              ┌───────────────────────────────┐
              │            AI (agent)          │
              │   按需编排，决定调用哪些 tool     │
              └───────┬───────────────┬────────┘
                      │ 调用 tool      │ 调用 tool
          ┌───────────▼──────┐  ┌─────▼───────────────┐
          │  简历/记忆系统      │  │      题目系统         │
          │  分级记忆 tool 族   │  │  search_questions   │
          │                   │  │  list_categories    │
          └───────────────────┘  └─────────────────────┘
            两系统之间无直接连线，互不感知
```

硬性约束：

1. **简历/记忆更新只改自己系统内部的状态**，绝不触碰题目、绝不触发相关性重算。记忆系统甚至不需要知道题库存在。
2. **相关性不是预存字段，而是 AI 用到时现场判断的**：AI 处理某题若需用户背景，就自己调记忆 tool 拉取**当前状态**并当场判断。改了简历，下次 AI 调用即见新状态——“跟着变”是实时读取的结果，而非批量重算。
3. **两系统对称内聚**：各自“管理好自己的数据 + 暴露 tool”，谁都不为对方维护派生数据。
4. **AI 是唯一编排者**：不写死跨系统流程。本地简历/题目工具与外部 MCP 工具平等汇入同一工具轮。

## 2. 存储方式：分层文件（非关系表）

记忆内容按访问模式（自上而下逐层下钻，几乎无横切查询）采用**文档型分层文件**存储，与分级加载 tool 1:1 对应；而非拆进关系表。存放于 App 私有目录 `filesDir/memory/`，每个记忆集一个文件夹：

```
memory/
  index.json                     # 轻量索引：所有记忆集 [{id, name, targetRole, summary}]
  global.md                      # 跨方向共享事实：年限、学历、语言
  java-backend/
    profile.md                   # L2 概览：targetRole + 技能清单 + 项目/经历 brief 列表
    resume.txt                   # 简历原文留档（可选）
    projects/
      order-system.md            # L3 细节：某项目 detail 全文
      im-gateway.md
    experiences/
      company-a.md               # L3 细节：某段经历 detail 全文
  android/
    profile.md
    projects/
      ...
```

存储职责划分（按数据性质，不一刀切）：

| 数据 | 存储 | 理由 |
|---|---|---|
| 简历记忆内容（概览/项目/经历/global） | **分层文件** `filesDir/memory/` | 文档型、层级下钻、AI 直接读写、人可编辑/导出 |
| 记忆集索引 | `index.json` | 轻量清单，供 L1 与 ProfileMatcher |
| 会话/消息历史、会话摘要 | **Room（沿用）** | 追加型日志、事务、按会话分页查询 |
| 题库、分类、设置 | **Room（沿用）** | 现状不变 |

> 一句话：记忆内容是文档 → 文件；对话日志和题库是记录 → 数据库。

### 2.1 记忆集 = 一个文件夹（= 一个求职方向）
- 每个方向一个子目录（`java-backend/`、`android/`）。多份**永久共存**，无 isActive、无归档、无切换。新增方向 = 新建文件夹 + 往 `index.json` 追一条。
- `index.json` 每项：`{ id, name, targetRole, summary }`。`summary` 由 AI 生成，供 L1 让 AI 快速判断相关性。

### 2.2 概览文件 profile.md（L2）
AI 结构化简历后产出：targetRole + 技能清单 + 各项目/经历的 **brief**（一句话摘要 + 对应 detail 文件名/id）。加载它即得该方向全貌，但不含细节。

### 2.3 细节文件 projects/*.md、experiences/*.md（L3）
每个项目/经历一个文件，含 **detail** 全文（背景、职责、技术栈、难点、亮点）。仅在信息不足或题目要求“结合项目谈…”时按需读取。

### 2.4 共享事实 global.md
跨方向共享的事实（年限、学历、语言）。任何记忆集加载时可一并带出，避免各方向重复维护。

### 2.5 简历原文
`resume.txt` 留档，非加载主路径；结构化后的 `profile.md` + 细节文件才是 AI 常规读取对象。

### 2.6 会话与摘要（见 ai-framework.md，留在 Room）
`Conversation` / `ChatMessage` / `MemorySummary`（跨会话长期记忆，独立于简历记忆）。

## 3. 记忆能力暴露：分级按需加载 tool 族

AI 像剥洋葱一样逐层拉取，全部注册进共享 `ToolRegistry`，与题目工具平等：

```
L1  list_memory_profiles()                          → 读 index.json
      → [{id, name:"Java后端", targetRole, summary}, {id, name:"Android", ...}]
      AI 按与题目的相关性挑选（如"分布式"→ 优先 Java 后端）

L2  load_profile_overview(profileId, query?)         → 读 <profileId>/profile.md + global.md
      → 该记忆集的技能清单 + 项目/经历的 brief 列表 + GLOBAL 事实
      query 命中则只回相关条目

L3  load_project_detail(profileId, projectId)        → 读 <profileId>/projects/<id>.md
    load_experience_detail(profileId, experienceId)  → 读 <profileId>/experiences/<id>.md
      → 某个项目/经历的 detail 全文
      仅当信息不足、或题目要求"结合项目谈…"时才调用
```

分级示例（对应“分布式”场景）：
1. AI 遇到分布式题目，需要用户背景 → 调 `list_memory_profiles` → 见 [Java后端, Android]。
2. 判断分布式与 Java 后端更相关 → 调 `load_profile_overview("java-backend", query="分布式")` → 看到技能、项目 brief。
3. 信息仍不足 / 题目要求结合项目 → 调 `load_project_detail` 取某项目 detail。

要点：**加载渐进、由 AI 按需驱动、先粗后细**；哪份记忆集、哪个项目更贴题，由 AI 在加载时判断，非预存字段。

## 4. 简历更新流程（只在记忆系统内，无提示、无切换）

```
简历更新/新增
  │
  ├─1. ResumeStructurer：AI 把简历原文结构化 → profile.md + projects/*.md + experiences/*.md
  │
  ├─2. 按 targetRole 语义找同方向记忆集（ProfileMatcher，读 index.json，复用 CategoryClassifier 套路）
  │
  └─3. 命中同方向 → 覆盖/新增该记忆集文件夹内的文件（旧文件按需保留）
       未命中       → 新建一个记忆集文件夹 + 往 index.json 追一条（不删旧的、不切换、不弹窗）
```

- 全程只操作记忆系统，**不触碰题目、不重算相关性**。
- 结果永远是“记忆集只增不减地共存”。用户投了新方向就多一份，AI 之后自己按需挑选。

## 5. 旧记忆处理

- 多份记忆**永久共存**，不因新方向而删除或归档（多个文件夹并存）。
- 同一方向内简历改版：覆盖该文件夹内的文件；如需追溯历史版本，可保留旧文件副本（MVP 可先只存最新）。
- 不做“激活/失活”，因此不存在“旧记忆失效”问题——AI 每次按题目相关性重新挑选。

## 6. 隐私

全部本地存储（App 私有目录）；文件为人可读格式，支持查看/编辑/删除任一记忆集文件夹与其文件；一键清空 = 删 `memory/` 目录。

## 7. 落地路线（测试先行，JVM 单测全绿、不联网、无真实 Key）

### Step 1 — 文件存储层
- [ ] `MemoryStore`：封装 `filesDir/memory/` 的读写——`index.json` 增删查、记忆集文件夹创建、`profile.md`/`projects/*.md`/`experiences/*.md`/`global.md` 读写。路径与 IO 抽象为可注入接口，用临时目录做 JVM 单测。
- [ ] 测试：索引读写、记忆集文件夹创建、文件读写、多记忆集并存互不干扰、一键清空。

### Step 2 — 简历结构化（经 AiClient，FakeAiClient 可测）
- [ ] `ResumeStructurer`：简历原文 → `profile.md`（含 brief 列表）+ `projects/*.md` + `experiences/*.md` + 更新 `global.md`。
- [ ] `ProfileMatcher`：读 `index.json`，按 targetRole 语义判定命中已有记忆集 / 新建，输出 `{matchedProfileId?, inferredTargetRole, summary}`。
- [ ] 测试：结构化解析、同方向命中覆盖更新、新方向新建文件夹并追加 index（旧记忆集不受影响）。

### Step 3 — 分级记忆 tool 族
- [ ] `list_memory_profiles`（读 index） / `load_profile_overview(query?)`（读 profile.md+global.md） / `load_project_detail` / `load_experience_detail`（读对应细节文件），注册进共享 `ToolRegistry`。
- [ ] 测试：L1 返回全部记忆集摘要；L2 按 query 过滤概览；L3 返回指定文件 detail；跨记忆集加载不串号；缺失 id 优雅报错。

### Step 4 — 接入 AI 编排
- [ ] 分析/追问流水线中，AI 按需调用记忆 tool 分级加载；`ContextAssembler` 只注入角色约束 + 已加载记忆片段。
- [ ] 「我的」页：记忆集列表、文件查看/编辑/删除、简历导入触发结构化。
- [ ] 测试：给定分布式题目，断言 AI 走 L1→L2(→L3) 的调用序列（用 fake agent/tool spy）；改简历后无任何题目侧写操作。

### 验收标准
- **解耦**：简历/记忆的任何更新路径中，对题目表零写操作、无相关性批量重算（spy/fake 断言题目系统零调用）。
- **共存**：新增方向后旧记忆集文件夹完整保留，`index.json` 与 `list_memory_profiles` 同时返回多份，无 isActive/归档字段。
- **结构化**：`ResumeStructurer` 能把原文拆成 profile.md（brief）+ 独立细节文件，brief 与 detail 分离于不同文件。
- **分级加载**：tool 族支持 L1(index)→L2(profile.md)→L3(细节文件) 渐进拉取，L2 支持 query 过滤，L3 按 id 精确下钻。
- **实时读取**：改简历后无需任何题目侧动作，AI 下次调用记忆 tool 即见新状态。
