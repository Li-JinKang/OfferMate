package com.jk.offermate.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jk.offermate.data.local.dao.CategoryDao
import com.jk.offermate.data.local.dao.ConversationDao
import com.jk.offermate.data.local.dao.ImportedPostDao
import com.jk.offermate.data.local.dao.QuestionDao
import com.jk.offermate.data.local.entity.CategoryEntity
import com.jk.offermate.data.local.entity.ChatMessageEntity
import com.jk.offermate.data.local.entity.ConversationEntity
import com.jk.offermate.data.local.entity.ImportedPostEntity
import com.jk.offermate.data.local.entity.QuestionEntity

/**
 * 应用本地数据库。
 */
@Database(
    entities = [
        ImportedPostEntity::class,
        QuestionEntity::class,
        ConversationEntity::class,
        ChatMessageEntity::class,
        CategoryEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class OfferMateDatabase : RoomDatabase() {
    abstract fun importedPostDao(): ImportedPostDao
    abstract fun questionDao(): QuestionDao
    abstract fun conversationDao(): ConversationDao
    abstract fun categoryDao(): CategoryDao
}
