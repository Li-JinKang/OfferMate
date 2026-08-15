package com.jk.offermate.di

import android.content.Context
import androidx.room.Room
import com.jk.offermate.data.ai.AiClient
import com.jk.offermate.data.ai.AnalysisPipeline
import com.jk.offermate.data.ai.AnswerGenerator
import com.jk.offermate.data.ai.DeepSeekClient
import com.jk.offermate.data.ai.QuestionExtractor
import com.jk.offermate.data.ai.RelevanceMatcher
import com.jk.offermate.data.importer.ImportInteractor
import com.jk.offermate.data.local.OfferMateDatabase
import com.jk.offermate.data.local.PostStore
import com.jk.offermate.data.reader.ContentReader
import com.jk.offermate.data.reader.HtmlContentExtractor
import com.jk.offermate.data.reader.OkHttpHtmlFetcher
import com.jk.offermate.data.reader.OkHttpUrlResolver
import com.jk.offermate.data.repository.QuestionRepository
import com.jk.offermate.data.repository.RoomPostRepository
import com.jk.offermate.data.repository.RoomQuestionRepository
import com.jk.offermate.data.resume.DataStoreResumeRepository
import com.jk.offermate.data.resume.PdfBoxResumeTextExtractor
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
    val settingsRepository: SettingsRepository
    val resumeRepository: ResumeRepository
    val resumeTextExtractor: ResumeTextExtractor
    val aiClient: AiClient
    val analysisPipeline: AnalysisPipeline
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

    // BYOK：运行时从设置读取 Key 与模型名
    override val aiClient: AiClient by lazy {
        DeepSeekClient(
            apiKeyProvider = { settingsRepository.settings.first().deepSeekApiKey },
            modelProvider = { settingsRepository.settings.first().model },
            baseUrlProvider = {
                settingsRepository.settings.first().baseUrl.ifBlank { com.jk.offermate.data.settings.AiProvider.DEEPSEEK.baseUrl }
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

    private val contentReader: ContentReader by lazy {
        ContentReader(
            urlResolver = OkHttpUrlResolver(),
            htmlFetcher = OkHttpHtmlFetcher(),
            extractor = HtmlContentExtractor(),
            dynamicReader = null // 小红书 WebView 读取(P2.4)后续接入
        )
    }

    override val importInteractor: ImportInteractor by lazy {
        ImportInteractor(contentReader, analysisPipeline)
    }

    override val postStore: PostStore by lazy {
        PostStore(database.importedPostDao(), database.questionDao())
    }

    override val importScheduler: ImportScheduler by lazy {
        WorkManagerImportScheduler(context, postStore)
    }
}
