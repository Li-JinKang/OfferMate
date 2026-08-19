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

    /**
     * 题库搜索：题干/答案/标签/考点/分类任一子串命中；题干命中优先，其次相关度。
     * [kw] 需由上层包裹为 `%关键词%` 并对 `% _ \` 做转义（配合 ESCAPE '\'）。
     */
    @Query(
        "SELECT * FROM question " +
            "WHERE question LIKE :kw ESCAPE '\\' OR answer LIKE :kw ESCAPE '\\' " +
            "OR tagsCsv LIKE :kw ESCAPE '\\' OR keyPointsCsv LIKE :kw ESCAPE '\\' " +
            "OR category LIKE :kw ESCAPE '\\' " +
            "ORDER BY (CASE WHEN question LIKE :kw ESCAPE '\\' THEN 0 ELSE 1 END), relevanceScore DESC, orderIndex " +
            "LIMIT :limit"
    )
    fun search(kw: String, limit: Int): Flow<List<QuestionEntity>>

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(question: QuestionEntity)

    @Query("DELETE FROM question WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM question WHERE postId = :postId")
    suspend fun deleteByPost(postId: String)
}
