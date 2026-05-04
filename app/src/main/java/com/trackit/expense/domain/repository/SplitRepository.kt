package com.trackit.expense.domain.repository

import com.trackit.expense.domain.model.Split
import com.trackit.expense.domain.model.SplitParticipant
import kotlinx.coroutines.flow.Flow

interface SplitRepository {
    fun getSplits(groupId: String): Flow<List<Split>>
    suspend fun addSplit(groupId: String, description: String, totalAmount: Double, paidBy: String, participants: List<SplitParticipant>): Result<Split>
    suspend fun refreshSplits(groupId: String): Result<Unit>
}
