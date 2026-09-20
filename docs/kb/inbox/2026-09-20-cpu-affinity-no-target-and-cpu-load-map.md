---
trust: L2
anchors:
  - app/src/main/java/com/jk/offermate/platform/CpuTopology.kt
  - app/src/main/java/com/jk/offermate/di/AppContainer.kt
  - app/src/main/java/com/jk/offermate/data/resume/PdfBoxResumeTextExtractor.kt
  - app/src/main/java/com/jk/offermate/data/resume/PdfPageRenderer.kt
  - app/src/main/java/com/jk/offermate/data/reader/ContentReader.kt
  - app/src/main/java/com/jk/offermate/data/ocr/MlKitTextRecognizer.kt
verified_commit: eb8f31e
verified_at: 2026-09-20
supersedes: null
---

# 绑核（CPU 亲和度）在本项目没有落点；附 CPU 负载分布

**否决性结论 + 调研结果。** 记下来是为了避免「绑核优化」被反复提出时每次都重做这轮调研
（读参考实现 + 查 Android 平台约束 + 全仓摸 CPU 负载分布）。

参考实现：`/Users/lijinkang/code/android/gitlab/wink/.../utils/cpu/StephenStrange.kt`
（美图 wink，JNI 调 `sched_setaffinity`，按 `cpuinfo_max_freq` 分大/中/小核）。

## 一、Android 上绑核的三条硬约束

1. **affinity 只能在 cgroup cpuset 允许的范围内取子集。** 进程被放进哪个 cpuset 由进程状态
   决定；后台进程被关进小核 cpuset 时，把 mask 设成全核**既不生效也不报错**。
   （[SO 54127097](https://stackoverflow.com/questions/54127097/android-background-process-affinity-cant-be-modified-through-native-code)：
   6 核设备上 mask 写 `111111` 毫无变化。）
2. **会偶发 `EINVAL`。** 骁龙 888 上指定大核 id 时 `sched_setaffinity` 间歇返回 -1 / errno 22
   （[SO 76322956](https://stackoverflow.com/questions/76322956/ocassional-invalid-argument-with-sched-setaffinity-on-the-android-device)），
   核心被 hotplug 下线是常见原因。
3. **它剥夺调度器的迁移能力。** 绑定后线程被抢占只能回原核，不能迁到空闲核
   （[SO 61218410](https://stackoverflow.com/questions/61218410/is-cpu-affinity-enforced-across-system-calls)）。
   绑在繁忙大核上排队可能比让 EAS 自己挑更慢。

（以上内容经改写以符合许可限制。）

参考实现外面套了三层开关（本地 SP + 服务端 `stephenStrangeEnable` + so 加载成功）且默认关闭，
本身就说明这是**需要 A/B 验证的优化，不是稳赢项**。

## 二、为什么本项目不该做（三条项目内证据）

1. **首页/启动路径没有 CPU 负载可绑。** `DefaultAppContainer` 的成员**全是 `by lazy`，
   构造函数体是空的**，所以 `OfferMateApplication.onCreate` 里那句 `DefaultAppContainer(context)`
   是零成本。整条启动链条是「登记依赖 + 各自线程池上的首次 IO 读」：Room 懒打开、
   DataStore 取委托句柄、`EncryptedPrefsKeyStore` 内部还套一层 `by lazy`。
   主线程本身已在 top-app cpuset，绑核对它没有增量。
2. **项目零 NDK/JNI**：无 `CMakeLists.txt`、无 `externalNativeBuild`/`ndkVersion`/`abiFilters`、
   无 `.c/.cpp`、无 `System.loadLibrary`。要拿 `sched_setaffinity` 就得把这套全建起来，
   而 JNI 部分**无法 JVM 单测**（项目宪法「保持可 JVM 单测」会破口）。
3. **「后台任务被关在小核」这个主要动机不成立**：两个 Worker 都调了 `setForeground` +
   `FOREGROUND_SERVICE_TYPE_DATA_SYNC`（`work/AnalyzePostWorker.kt:56`、
   `work/AnalyzeResumeWorker.kt:39`），带前台服务的进程不会被塞进 background cpuset。

**规避规则：再提绑核前，先用 Perfetto 确认目标线程实际落在哪个 cpuset。**
前提不成立时，引入 NDK 只是付了全部成本换零收益。

## 三、CPU 负载分布（⚠️ 量级为按代码规模的估算，未实测）

真正 CPU-bound 且够长的只有三处，**全在导入/简历路径，不在首页**：

| 工作 | 量级（估） | 现在跑在哪 |
|---|---|---|
| 长图解码 + ML Kit 中文 OCR | 数百 ms ~ 数秒/张，一帖多张**串行** | 解码在 Worker 的 `Dispatchers.Default`；推理在 ML Kit 内部线程 |
| HTML 正文提取（Jsoup ×2 + Readability4J，同一份 HTML 最多解析三遍） | 50~300ms/页 | Worker 的 `Dispatchers.Default`（`ContentReader` 里 `extractor.extract` **未切线程**）|
| PDF 文本抽取 / 全页渲染成 1080 宽 ARGB_8888 | 几百 ms ~ 数秒 | **`Dispatchers.IO`** |

**明确不值得**：JSON 解析（几 ms~几十 ms，且紧跟在几十秒网络调用之后）、SimHash 去重（<5ms）、
`InlineMarkdown.annotate`（单块十几~几十 μs）。

线程现状：项目**没有任何自定义线程池 / Executor / ThreadFactory**，也**没有任何
`Process.setThreadPriority` 调用**，线程优先级全是默认值。唯一可注入线程控制的官方点是
`TextRecognition.getClient(options)` 的 Executor（当前没配）与 Room 的 `setQueryExecutor`
（但 Room 侧是 IO 等待型，收益近零）。

## 四、一个真实的语义误用：CPU 密集任务放在 `Dispatchers.IO`

`PdfBoxResumeTextExtractor.extractText` 与 `PdfPageRenderer.render` 都是纯 CPU 活
（`PDFTextStripper` 解析、Skia 渲染），却跑在 `Dispatchers.IO` 上。

`Dispatchers.IO` 的语义是「阻塞等待型」，**默认 64 线程**——线程数是按 IO 等待设计的。
把 CPU 密集任务放上去，并发度会远超核心数，几个 PDF 渲染并发就能占满所有核、
抢走主线程 CPU。

**规则：CPU-bound 用 `Dispatchers.Default` 或专用池，不要用 `Dispatchers.IO`。**
反过来，`ContentReader.read` 里给 `urlResolver.resolve` / `htmlFetcher.fetch` 显式切 IO
是对的（那两个是同步阻塞网络调用），它的注释也解释了原因——**同一个文件里两种用法并存，
改动时别照着错的那处抄。**

## 五、待验证（不要当成事实使用）

1. **`Process.setThreadPriority(THREAD_PRIORITY_BACKGROUND)` 可能把线程关进小核。**
   记忆中 AOSP 在 `pri >= THREAD_PRIORITY_BACKGROUND` 时会 `set_sched_policy(SP_BACKGROUND)`，
   即不只设 nice、还切 cgroup，**效果与「上大核」相反**。
   [一条 SO 回答](https://stackoverflow.com/revisions/14214185/2)提到用 priority 9 而非 10
   可以「避开后台任务的人为限制」，间接印证 10 这个阈值确实触发了额外约束，
   但**没找到权威来源，需实测**。
   影响：做专用 CPU 池时优先级只能取 `THREAD_PRIORITY_DEFAULT` 附近，
   **别想当然用 `BACKGROUND`**。
2. 两个 Worker 运行时的实际 cpuset 归属（第二节第 3 条的依据）未用 Perfetto 确认。
3. `PDFBoxResourceLoader.init(applicationContext)` 在主线程，其真实代价未实测
   （按 API 语义它只登记 context，字体/cmap 到首次 `PDDocument.load` 才读 assets）。

## 六、顺带确立的一条：核心分档只用来定并行度

`platform/CpuTopology.kt` 按 sysfs 的 `cpuinfo_max_freq` 聚类出大/中/小核，
但它的用途是**给 CPU 密集任务的线程池定大小**（`performanceCoreCount` = 大核 + 中核，
刻意不含小核），**不是绑核**。

理由：`availableProcessors()` 在 8 核机上返回 8，而其中 4 个小核单核性能可能只有大核三分之一；
按 8 开池会把任务拆到小核上拖慢整体，还把大核让给别的线程。

分档规则相比参考实现放宽了三处，改动时别改回去：
- 档位 ≥3 时**中间所有档位都并进中核**（只取次高档会让 1+2+2+4 这类四档 SoC 漏掉一整簇）；
- **两档机型（4 大 + 4 小的中低端机）照常工作**（参考实现要求恰好三档，否则整个功能不启用）；
- 按**频率**而非核心号区间分档，离线核心造成的编号空洞不会错分。

三条都由 `CpuTopologyTest` 覆盖（8 条）。

## 证据

- 参考实现：wink 的 `StephenStrange.kt`（`queryLevelInfoList` 的分档逻辑、`canWork()` 的三层开关、
  `bindThreadToCoreList` 的 external 声明）。
- 平台约束：第一节三条 SO 链接。
- 项目内事实：第二、三、四节的文件行号，均在 `eb8f31e` 上核对。
  CPU 负载分布来自一次全仓调研（启动链条、`Dispatchers` 全量 grep、`work/` 下两个 Worker、
  NDK/JNI 全量 grep）。
- `CpuTopologyTest`（8 条）：四档不丢核、两档可用、同构、核心号不连续、读不到时兜底。
