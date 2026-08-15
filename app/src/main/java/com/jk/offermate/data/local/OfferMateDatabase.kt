package com.jk.offermate.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jk.offermate.data.local.dao.ImportedPostDao
import com.jk.offermate.data.local.entity.ImportedPostEntity

/**
 * 应用本地数据库（P3 最小版；后续补全题目/记忆/会话等实体）。
 */
@Database(
    entities = [ImportedPostEntity::class],
    version = 1,
    exportSchema = false
)
abstract class OfferMateDatabase : RoomDatabase() {
    abstract fun importedPostDao(): ImportedPostDao
}
