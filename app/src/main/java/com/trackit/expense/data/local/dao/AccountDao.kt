package com.trackit.expense.data.local.dao

import androidx.room.*
import com.trackit.expense.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for handling account-related database operations.
 */
@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY created_at DESC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity)

    @Delete
    suspend fun deleteAccount(account: AccountEntity)

    @Query("UPDATE accounts SET is_default = 0")
    suspend fun clearDefaults()

    @Transaction
    suspend fun setAsDefault(id: String) {
        clearDefaults()
        updateDefault(id, true)
    }

    @Query("UPDATE accounts SET is_default = :isDefault WHERE id = :id")
    suspend fun updateDefault(id: String, isDefault: Boolean)
}
