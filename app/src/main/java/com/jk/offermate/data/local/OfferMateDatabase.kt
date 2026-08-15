package com.jk.offermate.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jk.offermate.data.local.dao.ImportedPostDao
import com.jk.offermate.data.local.dao.QuestionDao
import com.jk.offermate.data.local.entity.ImportedPostEntity
import com.jk.offermate.data.local.entity.QuestionEntity

/**
 * 应用本地数据库。
 */
@Database(
    entities = [ImportedPostEntity::class, QuestionEntity::class],
    version = 2,
    exportSchema = false
)
abstract class OfferMateDatabase : RoomDatabase() {
    abstract fun importedPostDao(): ImportedPostDao
    abstract fun questionDao(): QuestionDao
}
