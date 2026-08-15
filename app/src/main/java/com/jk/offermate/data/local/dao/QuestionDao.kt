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

    @Query("UPDATE question SET practiced = :practiced WHERE id = :id")
    suspend fun setPracticed(id: String, practiced: Boolean)

    @Query("SELECT COUNT(*) FROM question WHERE postId = :postId")
    suspend fun countByPost(postId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(questions: List<QuestionEntity>)

    @Query("DELETE FROM question WHERE postId = :postId")
    suspend fun deleteByPost(postId: String)
}
