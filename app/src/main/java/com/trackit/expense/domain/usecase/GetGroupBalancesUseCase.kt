package com.trackit.expense.domain.usecase

import com.trackit.expense.domain.model.Balance
import com.trackit.expense.domain.repository.GroupRepository
import javax.inject.Inject

class GetGroupBalancesUseCase @Inject constructor(private val groupRepository: GroupRepository) {
    suspend operator fun invoke(groupId: String): Result<List<Balance>> =
        groupRepository.getBalances(groupId)
}
