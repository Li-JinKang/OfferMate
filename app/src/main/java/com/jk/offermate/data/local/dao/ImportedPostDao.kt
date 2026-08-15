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

    @Query("SELECT * FROM imported_post WHERE id = :id")
    fun observeById(id: String): Flow<ImportedPostEntity?>

    @Query("SELECT * FROM imported_post WHERE id = :id")
    suspend fun findById(id: String): ImportedPostEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(post: ImportedPostEntity)

    @Query("UPDATE imported_post SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long)

    @Query("DELETE FROM imported_post WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM imported_post")
    suspend fun clear()
}
