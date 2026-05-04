package com.trackit.expense.domain.usecase

import com.trackit.expense.domain.model.Group
import com.trackit.expense.domain.repository.GroupRepository
import javax.inject.Inject

class CreateGroupUseCase @Inject constructor(private val groupRepository: GroupRepository) {
    suspend operator fun invoke(name: String, emoji: String?): Result<Group> =
        groupRepository.createGroup(name, emoji)
}
