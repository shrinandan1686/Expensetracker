package com.trackit.expense.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.trackit.expense.data.local.dao.SplitDao
import com.trackit.expense.data.local.entity.SplitEntity
import com.trackit.expense.data.remote.api.TrackItApiService
import com.trackit.expense.data.remote.dto.CreateSplitRequestDto
import com.trackit.expense.data.remote.dto.SplitDto
import com.trackit.expense.data.remote.dto.SplitParticipantDto
import com.trackit.expense.domain.model.Split
import com.trackit.expense.domain.model.SplitParticipant
import com.trackit.expense.domain.repository.SplitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SplitRepositoryImpl @Inject constructor(
    private val apiService: TrackItApiService,
    private val splitDao: SplitDao,
    private val gson: Gson
) : SplitRepository {

    override fun getSplits(groupId: String): Flow<List<Split>> =
        splitDao.getSplitsByGroup(groupId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun addSplit(
        groupId: String,
        description: String,
        totalAmount: Double,
        paidBy: String,
        participants: List<SplitParticipant>
    ): Result<Split> = runCatching {
        val request = CreateSplitRequestDto(
            description = description,
            totalAmount = totalAmount,
            paidBy = paidBy,
            participants = participants.map { SplitParticipantDto(it.userId, it.amount, it.isPaid) }
        )
        val response = apiService.addSplit(groupId, request)
        if (!response.isSuccessful) error("Failed to add split: ${response.code()}")
        val dto = response.body() ?: error("Empty response")
        val split = dto.toDomain()
        splitDao.upsertSplit(split.toEntity())
        split
    }

    override suspend fun refreshSplits(groupId: String): Result<Unit> = runCatching {
        val response = apiService.getSplits(groupId)
        if (!response.isSuccessful) error("Failed to fetch splits: ${response.code()}")
        val splits = response.body() ?: emptyList()
        splitDao.upsertSplits(splits.map { it.toDomain().toEntity() })
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private fun SplitDto.toDomain() = Split(
        id = id ?: "",
        groupId = groupId ?: "",
        description = description ?: "",
        totalAmount = totalAmount ?: 0.0,
        paidBy = paidBy ?: "",
        participants = participants?.map { SplitParticipant(it.userId ?: "", it.amount ?: 0.0, it.isPaid ?: false) } ?: emptyList(),
        expenseId = expenseId,
        createdAt = createdAt ?: 0L
    )

    private fun Split.toEntity() = SplitEntity(
        id = id,
        groupId = groupId,
        description = description,
        totalAmount = totalAmount,
        paidBy = paidBy,
        participantsJson = gson.toJson(participants),
        expenseId = expenseId,
        createdAt = createdAt
    )

    private fun SplitEntity.toDomain(): Split {
        val participantType = object : TypeToken<List<SplitParticipant>>() {}.type
        val participants: List<SplitParticipant> = runCatching<List<SplitParticipant>> { gson.fromJson(participantsJson, participantType) ?: emptyList() }.getOrDefault(emptyList())
        return Split(id = id, groupId = groupId, description = description, totalAmount = totalAmount, paidBy = paidBy, participants = participants, expenseId = expenseId, createdAt = createdAt)
    }
}
