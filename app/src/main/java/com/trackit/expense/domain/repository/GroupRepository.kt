package com.trackit.expense.domain.repository

import com.trackit.expense.domain.model.Balance
import com.trackit.expense.domain.model.Group
import kotlinx.coroutines.flow.Flow

interface GroupRepository {
    fun getGroups(): Flow<List<Group>>
    suspend fun getGroupById(groupId: String): Result<Group>
    suspend fun createGroup(name: String, emoji: String?): Result<Group>
    suspend fun addMember(groupId: String, memberId: String, memberName: String, memberPhone: String, memberUpiId: String?): Result<Unit>
    suspend fun getBalances(groupId: String): Result<List<Balance>>
    suspend fun recordSettlement(groupId: String, toUserId: String, amount: Double): Result<String>
    suspend fun confirmSettlement(settlementId: String): Result<Unit>
    suspend fun refreshGroups(): Result<Unit>
}
