package com.jk.offermate.di

import android.content.Context
import androidx.room.Room
import com.jk.offermate.agent.AiClient
import com.jk.offermate.agent.AnalysisPipeline
import com.jk.offermate.agent.AnswerGenerator
import com.jk.offermate.agent.CategoryClassifier
import com.jk.offermate.agent.DeepSeekClient
import com.jk.offermate.agent.QuestionExtractor
import com.jk.offermate.agent.RelevanceMatcher
import com.jk.offermate.agent.chat.ContextAssembler
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
        RoomCategoryRepository(database.categoryDao())
    }

    override val conversationRepository: ConversationRepository by lazy {
        RoomConversationRepository(database.conversationDao())
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
            }
        )
    }

    override val analysisPipeline: AnalysisPipeline by lazy {
        AnalysisPipeline(
            extractor = QuestionExtractor(aiClient),
            matcher = RelevanceMatcher(aiClient),
            answerer = AnswerGenerator(aiClient)
        )
    }

    override val followUpService: FollowUpService by lazy {
        FollowUpService(
            aiClient = aiClient,
            assembler = ContextAssembler(
                // 追问历史按 token 预算裁剪，保留最近的多轮讨论
                TokenWindowMemory(maxTokens = 3000, estimator = HeuristicTokenEstimator())
            )
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
            analysisPipeline = analysisPipeline,
            ocrRecognizer = MlKitTextRecognizer(),
            imageFetcher = OkHttpImageFetcher(),
            categoryClassifier = CategoryClassifier(aiClient),
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
