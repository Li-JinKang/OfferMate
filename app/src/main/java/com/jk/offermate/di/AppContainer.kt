package com.jk.offermate.di

import android.content.Context
import androidx.room.Room
import com.jk.offermate.agent.AiClient
import com.jk.offermate.agent.AndroidAgentLogger
import com.jk.offermate.agent.pipeline.AnalysisPipeline
import com.jk.offermate.agent.pipeline.AnswerGenerator
import com.jk.offermate.agent.pipeline.CategoryClassifier
import com.jk.offermate.agent.tool.CategoryListTool
import com.jk.offermate.agent.tool.QuestionSearchTool
import com.jk.offermate.agent.tool.Tool
import com.jk.offermate.agent.tool.ToolCallingLlm
import com.jk.offermate.agent.tool.ToolRegistry
import com.jk.offermate.agent.DeepSeekClient
import com.jk.offermate.agent.pipeline.QuestionExtractor
import com.jk.offermate.agent.pipeline.RelevanceMatcher
import com.jk.offermate.agent.resume.ProfileMatcher
import com.jk.offermate.agent.resume.ResumeStructurer
import com.jk.offermate.agent.tool.memoryTools
import com.jk.offermate.agent.mcp.McpToolRepository
import com.jk.offermate.agent.chat.ContextAssembler
import com.jk.offermate.data.memory.MemoryStore
import com.jk.offermate.data.memory.ResumeIngestor
import com.jk.offermate.agent.chat.FollowUpService
import com.jk.offermate.agent.chat.HeuristicTokenEstimator
import com.jk.offermate.agent.chat.TokenWindowMemory
import com.jk.offermate.data.importer.ImportInteractor
import com.jk.offermate.data.ocr.MlKitTextRecognizer
import com.jk.offermate.data.reader.OkHttpImageFetcher
import com.jk.offermate.data.local.OfferMateDatabase
import com.jk.offermate.data.local.PostStore
import com.jk.offermate.data.reader.ContentReader
import com.jk.offermate.data.reader.HtmlContentExtractor
import com.jk.offermate.data.reader.OkHttpHtmlFetcher
import com.jk.offermate.data.reader.OkHttpUrlResolver
import com.jk.offermate.data.reader.WebViewContentReader
import com.jk.offermate.data.repository.AnswerUpdateStore
import com.jk.offermate.data.repository.CategoryOrderStore
import com.jk.offermate.data.repository.CategoryRepository
import com.jk.offermate.data.repository.ConversationRepository
import com.jk.offermate.data.repository.QuestionRepository
import com.jk.offermate.data.repository.RoomCategoryRepository
import com.jk.offermate.data.repository.RoomConversationRepository
import com.jk.offermate.data.repository.RoomPostRepository
import com.jk.offermate.data.repository.RoomQuestionRepository
import com.jk.offermate.data.resume.DataStoreResumeRepository
import com.jk.offermate.data.resume.PdfBoxResumeTextExtractor
import com.jk.offermate.data.resume.ResumeFileStore
import com.jk.offermate.data.resume.ResumeRepository
import com.jk.offermate.data.resume.ResumeTextExtractor
import com.jk.offermate.data.settings.DataStorePreferencesStore
import com.jk.offermate.data.settings.DefaultSettingsRepository
import com.jk.offermate.data.settings.EncryptedPrefsKeyStore
import com.jk.offermate.data.settings.SettingsRepository
import com.jk.offermate.domain.repository.PostRepository
import com.jk.offermate.work.ImportScheduler
import com.jk.offermate.work.WorkManagerImportScheduler
import kotlinx.coroutines.flow.first

/**
 * 应用级依赖容器（手动依赖注入组合根）。集中构造并持有依赖，向上暴露接口。
 */
interface AppContainer {
    val postRepository: PostRepository
    val questionRepository: QuestionRepository
    val categoryRepository: CategoryRepository
    val conversationRepository: ConversationRepository
    val settingsRepository: SettingsRepository
    val resumeRepository: ResumeRepository
    val resumeTextExtractor: ResumeTextExtractor
    val resumeFileStore: ResumeFileStore
    val aiClient: AiClient
    val analysisPipeline: AnalysisPipeline
    val followUpService: FollowUpService
    val importInteractor: ImportInteractor
    val postStore: PostStore
    val importScheduler: ImportScheduler

    /** 外部 MCP 服务器工具发现/刷新（其工具会并入共享工具轮）。 */
    val mcpToolRepository: McpToolRepository

    /** 简历记忆分层文件存储（供记忆工具与 UI 读写）。 */
    val memoryStore: MemoryStore

    /** 简历落地：结构化 + 方向匹配 + 写入记忆文件。 */
    val resumeIngestor: ResumeIngestor
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    private val database: OfferMateDatabase by lazy {
        Room.databaseBuilder(context, OfferMateDatabase::class.java, "offermate.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    override val postRepository: PostRepository by lazy {
        RoomPostRepository(database.importedPostDao(), database.questionDao())
    }

    override val questionRepository: QuestionRepository by lazy {
        RoomQuestionRepository(database.questionDao())
    }

    override val categoryRepository: CategoryRepository by lazy {
        RoomCategoryRepository(database.categoryDao(), CategoryOrderStore(context))
    }

    override val conversationRepository: ConversationRepository by lazy {
        RoomConversationRepository(database.conversationDao(), AnswerUpdateStore(context))
    }

    override val settingsRepository: SettingsRepository by lazy {
        DefaultSettingsRepository(
            secureKeyStore = EncryptedPrefsKeyStore(context),
            preferencesStore = DataStorePreferencesStore(context)
        )
    }

    override val resumeRepository: ResumeRepository by lazy {
        DataStoreResumeRepository(context)
    }

    override val resumeTextExtractor: ResumeTextExtractor by lazy {
        PdfBoxResumeTextExtractor(context)
    }

    override val resumeFileStore: ResumeFileStore by lazy {
        ResumeFileStore(context)
    }

    // BYOK：运行时从设置读取 Key 与模型名
    override val aiClient: AiClient by lazy {
        DeepSeekClient(
            apiKeyProvider = { settingsRepository.activeConfig.first().apiKey },
            modelProvider = { settingsRepository.activeConfig.first().model },
            baseUrlProvider = {
                settingsRepository.activeConfig.first().baseUrl.ifBlank { com.jk.offermate.data.settings.AiProvider.DEEPSEEK.baseUrl }
            },
            logger = AndroidAgentLogger
        )
    }

    // provider 支持 function-calling 时可用工具轮
    private val toolCallingLlm: ToolCallingLlm? by lazy { aiClient as? ToolCallingLlm }

    // MCP：由设置里的服务器配置发现外部工具（异步刷新，best-effort）
    override val mcpToolRepository: McpToolRepository by lazy {
        McpToolRepository(serversProvider = { settingsRepository.mcpServers.first() })
    }

    // 简历记忆：分层文件存储于 filesDir/memory
    override val memoryStore: MemoryStore by lazy {
        MemoryStore(java.io.File(context.filesDir, "memory"))
    }

    // 简历落地：结构化 + 方向匹配 → 写入记忆文件（只操作记忆系统）
    override val resumeIngestor: ResumeIngestor by lazy {
        ResumeIngestor(
            structurer = ResumeStructurer(aiClient),
            matcher = ProfileMatcher(aiClient),
            store = memoryStore
        )
    }

    // 本地工具：让模型自主取数——查题库、列分类（简历背景走分级记忆工具，按需拉取）
    private val localTools: List<Tool> by lazy {
        listOf(
            QuestionSearchTool(search = { q, limit -> questionRepository.search(q, limit).first() }),
            CategoryListTool(categoriesProvider = {
                categoryRepository.observeCategories().first() +
                    questionRepository.observeAll().first().map { it.category }
            })
        )
    }

    // 共享工具注册表：本地工具 + 分级记忆工具 + 已发现的 MCP 工具
    // （provider 按需求值：MCP 刷新、记忆文件更新后自动生效）
    private val sharedToolRegistry: ToolRegistry by lazy {
        ToolRegistry { localTools + memoryTools(memoryStore) + mcpToolRepository.current() }
    }

    override val analysisPipeline: AnalysisPipeline by lazy {
        AnalysisPipeline(
            extractor = QuestionExtractor(aiClient),
            matcher = RelevanceMatcher(aiClient, toolCallingLlm, sharedToolRegistry, logger = AndroidAgentLogger),
            answerer = AnswerGenerator(aiClient, toolCallingLlm, sharedToolRegistry)
        )
    }

    override val followUpService: FollowUpService by lazy {
        FollowUpService(
            aiClient = aiClient,
            assembler = ContextAssembler(
                // 追问历史按 token 预算裁剪，保留最近的多轮讨论
                TokenWindowMemory(maxTokens = 3000, estimator = HeuristicTokenEstimator())
            ),
            toolCallingLlm = toolCallingLlm,
            toolRegistry = sharedToolRegistry,
            logger = AndroidAgentLogger
        )
    }

    private val htmlContentExtractor: HtmlContentExtractor by lazy { HtmlContentExtractor() }

    private val contentReader: ContentReader by lazy {
        ContentReader(
            urlResolver = OkHttpUrlResolver(),
            htmlFetcher = OkHttpHtmlFetcher(),
            extractor = htmlContentExtractor,
            // WebView 离屏渲染兜底：真实浏览器环境跟随短链跳转 + 执行 JS，解决小红书静态抓取失败
            dynamicReader = WebViewContentReader(context, htmlContentExtractor)
        )
    }

    override val importInteractor: ImportInteractor by lazy {
        ImportInteractor(
            contentReader = contentReader,
            analyzer = analysisPipeline,
            ocrRecognizer = MlKitTextRecognizer(),
            imageFetcher = OkHttpImageFetcher(),
            categorizer = CategoryClassifier(aiClient),
            categoryRepository = categoryRepository
        )
    }

    override val postStore: PostStore by lazy {
        PostStore(database.importedPostDao(), database.questionDao())
    }

    override val importScheduler: ImportScheduler by lazy {
        WorkManagerImportScheduler(context, postStore)
    }
}
