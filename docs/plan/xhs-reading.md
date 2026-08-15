# 小红书链接读取方案（参考 MediaCrawler）

> 参考开源项目 [MediaCrawler](https://github.com/NanmiCoder/MediaCrawler)（内容经改写，仅采纳其技术思路）。
> ⚠️ 该项目声明**仅供学习、禁止商用**；本方案的合规边界见文末。

## 1. 从 MediaCrawler 借鉴的核心思路

- 用**真实浏览器**（它用 Playwright）登录并**保存登录态**（Cookie / 浏览器上下文）。
- **不逆向 JS 加密**，而是在保留登录态的浏览器上下文里，用页面**自身的 JS** 生成请求所需的**签名参数**。
- 带着登录态 Cookie + 签名去请求平台数据。

关键启示：**签名和渲染都交给页面自己的 JS 在真实浏览器环境里完成，我们只负责"驱动浏览器 + 取结果"。**

## 2. 映射到 Android（端侧、无后端）

Android 的 **WebView** 就是等价的"带登录态的真实浏览器上下文"。两条落地路线：

### 路线 A：WebView DOM/状态提取（MVP 首选，简单）
1. 用户在应用内的 WebView 里**登录小红书**；登录态由 `CookieManager` 持久化在本机。
2. 展开短链（`UrlResolver`）得到帖子真实 URL，在（可离屏的）WebView 中加载。
3. 等待 JS 渲染完成（`onPageFinished` + 轮询/超时）。
4. 注入 JS 提取正文：优先读 `window.__INITIAL_STATE__`（笔记标题/正文的 JSON），退化为 `document` 正文文本。
5. 产出 `PostContent(method = WEBVIEW)`。
- 优点：不碰签名算法，跟着页面走，实现门槛低。
- 缺点：依赖页面结构，平台改版可能失效 → 保留"手动粘贴"兜底。

### 路线 B：WebView 取签名 + OkHttp 调接口（增强，较稳但维护重）
1. 在带登录态的 WebView 上下文里 `evaluateJavascript` 调用页面签名函数，生成请求笔记详情接口所需的签名参数。
2. 用 OkHttp 带 Cookie + 签名请求接口，拿结构化 JSON。
- 优点：结构化、稳定。
- 缺点：签名函数名/入参会随版本变化，维护成本高。

**结论**：MVP 走**路线 A**；若 A 稳定性不足再上路线 B。

## 3. 与现有代码的衔接

已在 P2 定义好抽象，天然支持接入：

```kotlin
interface DynamicContentReader { suspend fun read(url: String): PostContent? }
```

- 新增 `XhsWebViewReader : DynamicContentReader`（Android 组件：离屏 WebView + CookieManager 登录态 + JS 注入提取）。
- `ContentReader` 的降级链已经是：静态抓取 → `DynamicContentReader` → 手动粘贴。接入后**上层逻辑无需改动**。
- 登录界面：一个可见的 WebView 登录页（首次或登录态失效时触发），成功后离屏复用登录态读取。

## 4. 测试策略（延续"测试先行"原则）

- **JVM 单测（已完成）**：`ContentReaderTest` 已用假的 `DynamicContentReader` 覆盖"静态失败 → WebView 成功/失败 → 手动兜底"的降级决策，无需真机。
- **可单测的纯逻辑再抽一层**：把"从 `__INITIAL_STATE__` JSON 解析出标题/正文"抽成纯函数 `XhsStateParser`，用保存的 JSON 夹具做 JVM 单测（不依赖 WebView）。→ 新增任务。
- **端侧验证**：`XhsWebViewReader` 的真机登录 + 加载 + JS 注入，用 `androidTest` 或手动验证，不纳入 JVM 单测门槛。
- 若需要小红书 `__INITIAL_STATE__` 的确切字段结构，再请你 fork MediaCrawler，参考其小红书模块的字段命名来写 `XhsStateParser` 与注入脚本。

## 5. 合规边界（重要）

- **仅个人学习**：只读取**用户主动分享的单篇链接**，用户用**自己的账号**登录，登录态仅存本机。
- **不做规模化爬取**：不实现关键词搜索、批量抓取、创作者主页遍历等（这正是 MediaCrawler 与本应用的定位差异）。
- **注明来源、不二次分发**，尊重平台协议与作者版权。
- **商用风险提示**：MediaCrawler 明确禁止商用；若 OfferMate 计划商用，直接采用其签名/爬取手法存在协议与法律风险，需先做法律评估，并优先走"用户登录态 + 单篇读取"的最小化路径。

## 6. 纳入路线图（补充到 P2.4）

- [ ] `XhsStateParser`（纯函数）：解析 `__INITIAL_STATE__` JSON → 标题/正文 + JVM 单测（JSON 夹具）。
- [ ] `XhsWebViewReader : DynamicContentReader`：离屏 WebView + CookieManager 登录态 + JS 注入（路线 A）。
- [ ] WebView 登录页与登录态持久化/失效处理。
- [ ] androidTest / 手动验证：真机登录 + 真实链接读取。
- [ ] （可选，增强）路线 B：WebView 取签名 + OkHttp 调接口。
