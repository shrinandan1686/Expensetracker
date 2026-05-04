package com.trackit.expense.domain.usecase

import com.trackit.expense.domain.repository.GroupRepository
import javax.inject.Inject

class SettleDebtUseCase @Inject constructor(private val groupRepository: GroupRepository) {
    suspend operator fun invoke(groupId: String, toUserId: String, amount: Double): Result<String> =
        groupRepository.recordSettlement(groupId, toUserId, amount)
}
