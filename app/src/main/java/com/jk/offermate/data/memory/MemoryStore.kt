package com.jk.offermate.data.memory

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/**
 * 简历记忆的分层文件存储层。
 *
 * 目录布局（[rootDir] 通常为 `context.filesDir/memory`）：
 * ```
 * <root>/
 *   index.json                 所有记忆集的轻量索引 [{id,name,targetRole,summary}]
 *   global.md                  跨方向共享事实（年限/学历/语言）
 *   <profileId>/
 *     profile.md               L2 概览：技能清单 + 项目/经历 brief
 *     projects/<itemId>.md     L3 细节
 *     experiences/<itemId>.md  L3 细节
 * ```
 *
 * 设计要点：
 * - 仅依赖一个可注入的 [rootDir] 与 [io] 调度器，不依赖 Android `Context`，
 *   因此可用临时目录做纯 JVM 单测。
 * - 所有 id（profileId / itemId）经 [safeSegment] 校验，防止路径穿越。
 * - 访问模式为自上而下逐层下钻，与分级加载 tool（L1→L2→L3）一一对应。
 */
class MemoryStore(
    private val rootDir: File,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val indexFile: File get() = File(rootDir, INDEX_FILE)
    private val globalFile: File get() = File(rootDir, GLOBAL_FILE)

    // ---- 索引（L1） ----

    /** 读取全部记忆集索引；不存在时返回空列表。 */
    suspend fun listProfiles(): List<MemoryProfileEntry> = withContext(io) {
        readIndex()
    }

    /**
     * 新增或更新一个记忆集条目（按 id 覆盖），并确保其文件夹存在。
     * 保持 index 中原有顺序，新条目追加到末尾。
     */
    suspend fun upsertProfile(entry: MemoryProfileEntry): Unit = withContext(io) {
        requireSafe(entry.id)
        profileDir(entry.id).mkdirs()
        val current = readIndex()
        val replaced = current.any { it.id == entry.id }
        val updated = if (replaced) {
            current.map { if (it.id == entry.id) entry else it }
        } else {
            current + entry
        }
        writeIndex(updated)
    }

    /** 删除一个记忆集：移除其文件夹与索引条目。找不到时静默返回。 */
    suspend fun removeProfile(id: String): Unit = withContext(io) {
        requireSafe(id)
        profileDir(id).deleteRecursively()
        val remaining = readIndex().filterNot { it.id == id }
        writeIndex(remaining)
    }

    // ---- 概览（L2） ----

    /** 读取某记忆集的概览 profile.md；不存在返回 null。 */
    suspend fun readProfileOverview(profileId: String): String? = withContext(io) {
        requireSafe(profileId)
        File(profileDir(profileId), PROFILE_FILE).takeIf { it.isFile }?.readText()
    }

    /** 写入某记忆集的概览 profile.md（文件夹不存在时自动创建）。 */
    suspend fun writeProfileOverview(profileId: String, markdown: String): Unit = withContext(io) {
        requireSafe(profileId)
        profileDir(profileId).mkdirs()
        File(profileDir(profileId), PROFILE_FILE).writeText(markdown)
    }

    // ---- 细节（L3） ----

    /** 列出某记忆集某类别下的全部细节文件 id（不含扩展名）。 */
    suspend fun listDetails(profileId: String, kind: DetailKind): List<String> = withContext(io) {
        requireSafe(profileId)
        val dir = File(profileDir(profileId), kind.dirName)
        if (!dir.isDirectory) return@withContext emptyList()
        dir.listFiles { f -> f.isFile && f.name.endsWith(MD_EXT) }
            ?.map { it.name.removeSuffix(MD_EXT) }
            ?.sorted()
            ?: emptyList()
    }

    /** 读取某细节文件；不存在返回 null。 */
    suspend fun readDetail(profileId: String, kind: DetailKind, itemId: String): String? =
        withContext(io) {
            requireSafe(profileId)
            requireSafe(itemId)
            detailFile(profileId, kind, itemId).takeIf { it.isFile }?.readText()
        }

    /**
     * 把模型给出的 [wanted]（可能是真实 id、大小写不一致、经 slug 化的名称，甚至直接是中文标题）
     * 解析为实际存在的细节文件 id；无法匹配时返回 null。
     *
     * 这是为了容忍「概览里只暴露了显示名、模型拿不到精确 id」的历史数据：detail 工具据此仍能命中，
     * 而不是因为传入非法/不存在的 id 直接失败。匹配优先级：精确 id → slug 化 id → 忽略大小写 →
     * 与各细节正文首个标题行（`# 标题`）比对（相等/包含）。
     */
    suspend fun resolveDetailId(profileId: String, kind: DetailKind, wanted: String): String? =
        withContext(io) {
            requireSafe(profileId)
            val target = wanted.trim()
            if (target.isEmpty()) return@withContext null

            // 1) 精确 id
            if (MemoryIds.isValid(target) && detailFile(profileId, kind, target).isFile) {
                return@withContext target
            }
            // 2) slug 化后的 id（如 "VibePlayer" → "vibeplayer"）
            val slug = MemoryIds.sanitize(target, "")
            if (slug.isNotEmpty() && detailFile(profileId, kind, slug).isFile) {
                return@withContext slug
            }
            // 3) 与现有 id 忽略大小写比对；4) 与正文标题行比对
            val ids = detailDir(profileId, kind).listFiles { f -> f.isFile && f.name.endsWith(MD_EXT) }
                ?.map { it.name.removeSuffix(MD_EXT) }
                .orEmpty()
            ids.firstOrNull { it.equals(target, ignoreCase = true) }?.let { return@withContext it }
            ids.firstOrNull { id ->
                val title = detailFile(profileId, kind, id).takeIf { it.isFile }
                    ?.readText()
                    ?.lineSequence()
                    ?.firstOrNull { it.trimStart().startsWith("#") }
                    ?.trimStart('#', ' ', '\t')
                    ?.trim()
                    .orEmpty()
                title.isNotEmpty() &&
                    (title.equals(target, ignoreCase = true) ||
                        title.contains(target, ignoreCase = true) ||
                        target.contains(title, ignoreCase = true))
            }
        }

    /** 写入某细节文件（目录不存在时自动创建）。 */
    suspend fun writeDetail(
        profileId: String,
        kind: DetailKind,
        itemId: String,
        markdown: String
    ): Unit = withContext(io) {
        requireSafe(profileId)
        requireSafe(itemId)
        val file = detailFile(profileId, kind, itemId)
        file.parentFile?.mkdirs()
        file.writeText(markdown)
    }

    /** 删除某细节文件；文件不存在时静默返回 false。 */
    suspend fun deleteDetail(profileId: String, kind: DetailKind, itemId: String): Boolean =
        withContext(io) {
            requireSafe(profileId)
            requireSafe(itemId)
            detailFile(profileId, kind, itemId).delete()
        }

    // ---- 共享事实（global） ----

    /** 读取跨方向共享事实 global.md；不存在返回 null。 */
    suspend fun readGlobal(): String? = withContext(io) {
        globalFile.takeIf { it.isFile }?.readText()
    }

    /** 写入跨方向共享事实 global.md。 */
    suspend fun writeGlobal(markdown: String): Unit = withContext(io) {
        rootDir.mkdirs()
        globalFile.writeText(markdown)
    }

    // ---- 清空 ----

    /** 一键清空：删除整个 memory 目录。 */
    suspend fun clearAll(): Unit = withContext(io) {
        rootDir.deleteRecursively()
    }

    // ---- 内部 ----

    private fun profileDir(id: String) = File(rootDir, id)

    private fun detailDir(profileId: String, kind: DetailKind) =
        File(profileDir(profileId), kind.dirName)

    private fun detailFile(profileId: String, kind: DetailKind, itemId: String) =
        File(detailDir(profileId, kind), itemId + MD_EXT)

    /**
     * 读取 index.json 为条目列表。手动解析（与项目既有 [JsonSupport] 风格一致，
     * 不依赖 kotlinx-serialization 编译期插件）。文件缺失/损坏时返回空列表。
     */
    private fun readIndex(): List<MemoryProfileEntry> {
        val f = indexFile
        if (!f.isFile) return emptyList()
        return runCatching {
            json.parseToJsonElement(f.readText())
                .jsonObject["profiles"]?.jsonArray.orEmpty()
                .map { el ->
                    val o = el.jsonObject
                    MemoryProfileEntry(
                        id = o.str("id"),
                        name = o.str("name"),
                        targetRole = o.str("targetRole"),
                        summary = o.str("summary")
                    )
                }
                .filter { it.id.isNotEmpty() }
        }.getOrDefault(emptyList())
    }

    private fun writeIndex(entries: List<MemoryProfileEntry>) {
        rootDir.mkdirs()
        val root = buildJsonObject {
            put("profiles", buildJsonArray {
                entries.forEach { e ->
                    add(buildJsonObject {
                        put("id", e.id)
                        put("name", e.name)
                        put("targetRole", e.targetRole)
                        put("summary", e.summary)
                    })
                }
            })
        }
        indexFile.writeText(json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), root))
    }

    private fun kotlinx.serialization.json.JsonObject.str(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull ?: ""

    private fun requireSafe(segment: String) {
        require(MemoryIds.isValid(segment)) { "非法的记忆标识: '$segment'" }
    }

    private companion object {
        const val INDEX_FILE = "index.json"
        const val GLOBAL_FILE = "global.md"
        const val PROFILE_FILE = "profile.md"
        const val MD_EXT = ".md"
    }
}
