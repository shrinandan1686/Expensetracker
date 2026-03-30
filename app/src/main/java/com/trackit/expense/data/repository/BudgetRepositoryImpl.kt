package com.trackit.expense.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.trackit.expense.data.local.dao.BudgetDao
import com.trackit.expense.data.local.entity.BudgetEntity
import com.trackit.expense.domain.model.Budget
import com.trackit.expense.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first implementation of [BudgetRepository].
 *
 * ## JSON serialisation
 * [Budget.categoryBudgets] is a [Map<String, Double>] at the domain layer and a JSON
 * String in [BudgetEntity.categoryBudgets]. Gson handles the conversion in the mapper
 * functions below. No Room [TypeConverter] is required, keeping the entity class clean.
 *
 * @param budgetDao  Room DAO for budget persistence.
 * @param gson       Shared Gson instance injected by Hilt (from [NetworkModule]).
 */
@Singleton
class BudgetRepositoryImpl @Inject constructor(
    private val budgetDao: BudgetDao,
    private val gson: Gson
) : BudgetRepository {

    private val mapType = object : TypeToken<Map<String, Double>>() {}.type

    // ─────────────────────────────────────────────────────────────────────────
    // Reads
    // ─────────────────────────────────────────────────────────────────────────

    override fun getAll(): Flow<List<Budget>> =
        budgetDao.getAll().map { it.map { entity -> entity.toDomain() } }

    override fun getByMonth(month: String): Flow<Budget?> =
        budgetDao.getByMonth(month).map { it?.toDomain() }

    // ─────────────────────────────────────────────────────────────────────────
    // Writes
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun save(budget: Budget): Result<Unit> = runCatching {
        budgetDao.upsert(budget.toEntity())
    }

    override suspend fun delete(id: String): Result<Unit> = runCatching {
        budgetDao.deleteById(id)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mappers
    // ─────────────────────────────────────────────────────────────────────────

    private fun BudgetEntity.toDomain(): Budget = Budget(
        id              = id,
        month           = month,
        overall         = overall,
        categoryBudgets = runCatching<Map<String, Double>> {
            gson.fromJson(categoryBudgets, mapType)
        }.getOrDefault(emptyMap())
    )

    private fun Budget.toEntity(): BudgetEntity = BudgetEntity(
        id              = id,
        month           = month,
        overall         = overall,
        categoryBudgets = gson.toJson(categoryBudgets)
    )
}
