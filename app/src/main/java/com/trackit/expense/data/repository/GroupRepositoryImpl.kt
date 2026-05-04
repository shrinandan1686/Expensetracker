package com.trackit.expense.data.repository

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.trackit.expense.data.local.dao.GroupDao
import com.trackit.expense.data.local.entity.GroupEntity
import com.trackit.expense.data.remote.api.TrackItApiService
import com.trackit.expense.data.remote.dto.CreateSettlementRequestDto
import com.trackit.expense.data.remote.dto.GroupDto
import com.trackit.expense.data.remote.dto.GroupMemberDto
import com.trackit.expense.domain.model.Balance
import com.trackit.expense.domain.model.Group
import com.trackit.expense.domain.model.GroupMember
import com.trackit.expense.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepositoryImpl @Inject constructor(
    private val apiService: TrackItApiService,
    private val groupDao: GroupDao,
    private val gson: Gson
) : GroupRepository {

    override fun getGroups(): Flow<List<Group>> =
        groupDao.getAllGroups().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getGroupById(groupId: String): Result<Group> = runCatching {
        val cached = groupDao.getGroupById(groupId)
        if (cached != null) return Result.success(cached.toDomain())
        val response = apiService.getGroup(groupId)
        if (!response.isSuccessful) error("Failed to fetch group: ${response.code()}")
        val dto = response.body() ?: error("Empty response")
        val group = dto.toDomain()
        groupDao.upsertGroup(group.toEntity())
        group
    }

    override suspend fun createGroup(name: String, emoji: String?): Result<Group> = runCatching {
        val body = buildMap<String, String> {
            put("name", name)
            emoji?.let { put("emoji", it) }
        }
        Log.d(TAG, "createGroup: name=$name")
        val response = apiService.createGroup(body)
        Log.d(TAG, "createGroup response: code=${response.code()}")
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()
            Log.e(TAG, "createGroup error body: $errorBody")
            error("Server error ${response.code()}: $errorBody")
        }
        val dto = response.body() ?: error("Empty response body")
        Log.d(TAG, "createGroup dto.id=${dto.id}")
        val group = dto.toDomain()
        groupDao.upsertGroup(group.toEntity())
        group
    }

    override suspend fun addMember(
        groupId: String,
        memberId: String,
        memberName: String,
        memberPhone: String,
        memberUpiId: String?
    ): Result<Unit> = runCatching {
        val body = buildMap<String, String> {
            put("memberId", memberId)
            put("memberName", memberName)
            put("memberPhone", memberPhone)
            memberUpiId?.let { put("memberUpiId", it) }
        }
        val response = apiService.addGroupMember(groupId, body)
        if (!response.isSuccessful) error("Failed to add member: ${response.code()}")
    }

    override suspend fun getBalances(groupId: String): Result<List<Balance>> = runCatching {
        val response = apiService.getGroupBalances(groupId)
        if (!response.isSuccessful) error("Failed to fetch balances: ${response.code()}")
        val dto = response.body() ?: return Result.success(emptyList())
        dto.balances?.map { Balance(fromUserId = it.from ?: "", toUserId = it.to ?: "", amount = it.amount ?: 0.0) }
            ?: emptyList()
    }

    override suspend fun recordSettlement(groupId: String, toUserId: String, amount: Double): Result<String> = runCatching {
        val response = apiService.recordSettlement(groupId, CreateSettlementRequestDto(toUserId, amount))
        if (!response.isSuccessful) error("Failed to record settlement: ${response.code()}")
        response.body()?.id ?: ""
    }

    override suspend fun confirmSettlement(settlementId: String): Result<Unit> = runCatching {
        val response = apiService.confirmSettlement(settlementId)
        if (!response.isSuccessful) error("Failed to confirm settlement: ${response.code()}")
    }

    override suspend fun refreshGroups(): Result<Unit> = runCatching {
        val response = apiService.getGroups()
        if (!response.isSuccessful) error("Failed to fetch groups: ${response.code()}")
        val groups = response.body() ?: emptyList()
        groupDao.upsertGroups(groups.map { it.toDomain().toEntity() })
    }

    companion object {
        private const val TAG = "GroupRepo"
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private fun GroupDto.toDomain() = Group(
        id = id ?: "",
        name = name ?: "",
        emoji = emoji,
        createdBy = createdBy ?: "",
        members = members?.map { it.toDomain() } ?: emptyList(),
        createdAt = createdAt ?: 0L
    )

    private fun GroupMemberDto.toDomain() = GroupMember(
        userId = userId ?: "",
        name = name ?: "",
        phone = phone ?: "",
        upiId = upiId
    )

    private fun Group.toEntity() = GroupEntity(
        id = id,
        name = name,
        emoji = emoji,
        createdBy = createdBy,
        membersJson = gson.toJson(members),
        createdAt = createdAt
    )

    private fun GroupEntity.toDomain(): Group {
        val memberType = object : TypeToken<List<GroupMember>>() {}.type
        val members: List<GroupMember> = runCatching<List<GroupMember>> { gson.fromJson(membersJson, memberType) ?: emptyList() }.getOrDefault(emptyList())
        return Group(id = id, name = name, emoji = emoji, createdBy = createdBy, members = members, createdAt = createdAt)
    }
}
