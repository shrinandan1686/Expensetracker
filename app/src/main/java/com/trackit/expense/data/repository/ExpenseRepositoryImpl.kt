package com.trackit.expense.data.repository

import com.trackit.expense.data.local.dao.ExpenseDao
import com.trackit.expense.data.local.entity.ExpenseEntity
import com.trackit.expense.domain.model.Expense
import com.trackit.expense.domain.repository.ExpenseRepository
import com.trackit.expense.worker.SyncWorker
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first implementation of [ExpenseRepository].
 *
 * ## Offline-first guarantee
 * Every write operation persists data to Room **synchronously** (within the calling
 * coroutine) before returning. Network sync happens asynchronously via [SyncWorker],
 * which WorkManager schedules on the next available network window.
 *
 * The UI always observes [Flow] from Room, so the screen updates instantly after
 * a local write — no spinner, no waiting for HTTP.
 *
 * ## ID generation
 * [Expense.id] is a UUID pre-set at the domain model layer (default parameter).
 * The repository does not generate IDs — it trusts the caller's value.
 *
 * @param expenseDao   Room DAO — single source of truth.
 * @param workManager  WorkManager — used to enqueue background sync.
 */
@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val workManager: WorkManager
) : ExpenseRepository {

    // ─────────────────────────────────────────────────────────────────────────
    // Reads — all reactive Flow from Room
    // ─────────────────────────────────────────────────────────────────────────

    override fun getAll(): Flow<List<Expense>> =
        expenseDao.getAll().map { it.map(ExpenseEntity::toDomain) }

    override fun getUnlogged(): Flow<List<Expense>> =
        expenseDao.getUnlogged().map { it.map(ExpenseEntity::toDomain) }

    override fun getByMonth(month: String): Flow<List<Expense>> =
        expenseDao.getByMonth(month).map { it.map(ExpenseEntity::toDomain) }

    override fun getByCategory(category: String): Flow<List<Expense>> =
        expenseDao.getByCategory(category).map { it.map(ExpenseEntity::toDomain) }

    override fun getTotalByMonth(month: String): Flow<Double> =
        expenseDao.getTotalByMonth(month)

    override fun getById(id: String): Flow<Expense?> =
        expenseDao.getById(id).map { it?.toDomain() }

    // ─────────────────────────────────────────────────────────────────────────
    // Writes — Room first, then enqueue sync
    // ─────────────────────────────────────────────────────────────────────────

    override suspend fun save(expense: Expense): Result<String> = runCatching {
        expenseDao.insert(expense.toEntity())
        enqueueSyncWork()
        expense.id
    }

    override suspend fun update(expense: Expense): Result<Unit> = runCatching {
        expenseDao.update(expense.toEntity())
        enqueueSyncWork()
    }

    override suspend fun delete(id: String): Result<Unit> = runCatching {
        expenseDao.deleteById(id)
        // NB: deletions are not currently propagated to the server
    }

    override suspend fun markLogged(
        id: String,
        loggedAt: Long
    ): Result<Unit> = runCatching {
        expenseDao.markLogged(id, loggedAt)
        enqueueSyncWork()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sync
    // ─────────────────────────────────────────────────────────────────────────

    override fun enqueueSyncWork() {
        SyncWorker.enqueue(workManager)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Entity ↔ Domain mappers (extension functions)
    // ─────────────────────────────────────────────────────────────────────────
}

// Declared as top-level extension functions so they can be reused by SyncWorker
// without needing an instance of the repository.

fun ExpenseEntity.toDomain(): Expense = Expense(
    id            = id,
    amount        = amount,
    merchant      = merchant,
    category      = category,
    account       = account,
    notes         = notes,
    rawSms        = rawSms,
    isLogged      = isLogged,
    isSynced      = isSynced,
    transactionAt = transactionAt,
    loggedAt      = loggedAt,
    createdAt     = createdAt
)

fun Expense.toEntity(): ExpenseEntity = ExpenseEntity(
    id            = id,
    amount        = amount,
    merchant      = merchant,
    category      = category,
    account       = account,
    notes         = notes,
    rawSms        = rawSms,
    isLogged      = isLogged,
    isSynced      = isSynced,
    transactionAt = transactionAt,
    loggedAt      = loggedAt,
    createdAt     = createdAt
)
