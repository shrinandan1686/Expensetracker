package com.trackit.expense.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.trackit.expense.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [CategoryEntity].
 *
 * Provides reactive [Flow]-based queries and suspend write operations
 * for managing expense categories.
 */
@Dao
interface CategoryDao {

    // ──────────────────────────── INSERT / UPDATE / DELETE ────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id AND is_default = 0")
    suspend fun deleteNonDefaultCategoryById(id: Long)

    // ──────────────────────────── SELECT ────────────────────────────

    /** Observe all categories ordered by name */
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    /** Get a single category by ID */
    @Query("SELECT * FROM categories WHERE id = :id")
    fun getCategoryById(id: Long): Flow<CategoryEntity?>

    /** Get only system default categories */
    @Query("SELECT * FROM categories WHERE is_default = 1")
    fun getDefaultCategories(): Flow<List<CategoryEntity>>

    /** Check if any categories exist (used for first-run seeding) */
    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCategoryCount(): Int
}
