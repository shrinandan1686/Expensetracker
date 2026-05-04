package com.trackit.expense.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.trackit.expense.data.local.entity.SplitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SplitDao {

    @Query("SELECT * FROM splits WHERE group_id = :groupId ORDER BY created_at DESC")
    fun getSplitsByGroup(groupId: String): Flow<List<SplitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSplits(splits: List<SplitEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSplit(split: SplitEntity)

    @Query("DELETE FROM splits WHERE group_id = :groupId")
    suspend fun deleteSplitsByGroup(groupId: String)
}
