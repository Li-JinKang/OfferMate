# ============================================================================
# OfferMate R8/ProGuard 规则
# ============================================================================

# ---- 枚举常量名必须保留 ----
# 项目大量以 enum 的 name/valueOf/entries 做 JSON 与数据库(Room/DataStore)序列化：
#   - Role.name.lowercase() 拼 DeepSeek API 的 role 字段
#   - Difficulty / Platform / ImportStatus / QuestionSource 存取用 name/valueOf
# R8 默认会重命名枚举常量，会导致这些序列化在运行期失配。这里统一保留。
-keepclassmembers enum * { *; }

# ---- kotlinx.serialization ----
# 本项目仅用 JsonElement / buildJsonObject 等运行期 API（无 @Serializable、无反射），
# 保守保留注解与内部类信息，避免优化误伤。
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**
-dontwarn kotlinx.serialization.**

# ---- PDFBox-Android（简历 PDF 解析）----
# 该库会加载字体/资源、内部有按类型分派逻辑，保守保留避免运行期缺类。
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**

# ---- 三方内容解析库：抑制与本项目无关的告警 ----
-dontwarn org.jsoup.**
-dontwarn net.dankito.readability4j.**
# slf4j 可选绑定（jsoup/readability4j 传递依赖，运行期无绑定实现，忽略即可）
-dontwarn org.slf4j.**

# ---- 协程：保留调试用的内部字段名（不影响体积，便于崩溃栈可读）----
-keepclassmembernames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-dontwarn kotlinx.coroutines.**
