package com.jk.offermate.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jk.offermate.data.local.entity.QuestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {

    @Query("SELECT * FROM question WHERE postId = :postId ORDER BY orderIndex")
    fun observeByPost(postId: String): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM question ORDER BY relevanceScore DESC, orderIndex")
    fun observeAll(): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM question WHERE id = :id")
    fun observeById(id: String): Flow<QuestionEntity?>

    @Query("UPDATE question SET practiced = :practiced WHERE id = :id")
    suspend fun setPracticed(id: String, practiced: Boolean)

    @Query("UPDATE question SET answer = :answer WHERE id = :id")
    suspend fun updateAnswer(id: String, answer: String)

    @Query("SELECT COUNT(*) FROM question WHERE postId = :postId")
    suspend fun countByPost(postId: String): Int

    /** 去重用：取指定分桶内已有题目的指纹（增量比对，不扫全表）。 */
    @Query("SELECT exactHash, simhash, bucketKey FROM question WHERE bucketKey IN (:buckets)")
    suspend fun fingerprintsInBuckets(buckets: List<String>): List<FingerprintRow>

    /** 去重用：命中的精确指纹（跨分桶的精确重复兜底）。 */
    @Query("SELECT exactHash FROM question WHERE exactHash IN (:hashes)")
    suspend fun existingExactHashes(hashes: List<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(questions: List<QuestionEntity>)

    @Query("DELETE FROM question WHERE postId = :postId")
    suspend fun deleteByPost(postId: String)
}
