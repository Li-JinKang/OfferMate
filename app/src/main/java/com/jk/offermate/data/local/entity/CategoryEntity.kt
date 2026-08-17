package com.jk.offermate.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户自定义分类（考点）。允许存在没有题目的空分类，以便先建分类再加题。
 * AI 抽取产生的分类由题目标签隐式派生，不必落此表。
 */
@Entity(tableName = "category")
data class CategoryEntity(
    @PrimaryKey val name: String,
    val createdAt: Long
)
