package com.jk.offermate.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jk.offermate.data.local.entity.ImportedPostEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportedPostDao {

    @Query("SELECT * FROM imported_post ORDER BY pinned DESC, importedAt DESC")
    fun observeAll(): Flow<List<ImportedPostEntity>>

    @Query("UPDATE imported_post SET pinned = :pinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, updatedAt: Long)

    @Query("SELECT * FROM imported_post WHERE id = :id")
    fun observeById(id: String): Flow<ImportedPostEntity?>

    @Query("SELECT * FROM imported_post WHERE id = :id")
    suspend fun findById(id: String): ImportedPostEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(post: ImportedPostEntity)

    /** 状态流转。同时清掉上一次的失败原因，避免重试成功后旧原因残留。 */
    @Query("UPDATE imported_post SET status = :status, failureReason = NULL, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long)

    /** 落终态失败：状态 + 可读原因一起写。 */
    @Query("UPDATE imported_post SET status = :status, failureReason = :reason, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateFailure(id: String, status: String, reason: String?, updatedAt: Long)

    @Query("DELETE FROM imported_post WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM imported_post")
    suspend fun clear()
}
