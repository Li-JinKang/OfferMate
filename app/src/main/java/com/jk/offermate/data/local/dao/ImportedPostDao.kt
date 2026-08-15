package com.jk.offermate.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jk.offermate.data.local.entity.ImportedPostEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportedPostDao {

    @Query("SELECT * FROM imported_post ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<ImportedPostEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(post: ImportedPostEntity)
}
