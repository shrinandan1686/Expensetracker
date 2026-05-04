package com.trackit.expense.domain.usecase

import com.trackit.expense.domain.model.Split
import com.trackit.expense.domain.model.SplitParticipant
import com.trackit.expense.domain.repository.SplitRepository
import javax.inject.Inject

class AddSplitUseCase @Inject constructor(private val splitRepository: SplitRepository) {
    suspend operator fun invoke(
        groupId: String,
        description: String,
        totalAmount: Double,
        paidBy: String,
        participants: List<SplitParticipant>
    ): Result<Split> = splitRepository.addSplit(groupId, description, totalAmount, paidBy, participants)
}
