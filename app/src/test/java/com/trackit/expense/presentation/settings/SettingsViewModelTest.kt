package com.trackit.expense.presentation.settings

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.trackit.expense.data.local.dao.AccountDao
import com.trackit.expense.data.local.dao.CategoryDao
import com.trackit.expense.data.local.entity.AccountEntity
import com.trackit.expense.data.local.entity.AccountType
import com.trackit.expense.data.local.entity.CategoryEntity
import com.trackit.expense.domain.repository.SettingsRepository
import com.trackit.expense.domain.repository.ThemeMode
import com.trackit.expense.domain.usecase.BulkSmsImportUseCase
import com.trackit.expense.domain.usecase.ExportDataUseCase
import com.trackit.expense.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val accountDao         = mockk<AccountDao>()
    private val categoryDao        = mockk<CategoryDao>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val exportDataUseCase  = mockk<ExportDataUseCase>()
    private val bulkSmsImportUseCase = mockk<BulkSmsImportUseCase>()

    private fun buildVm() = SettingsViewModel(
        accountDao           = accountDao,
        categoryDao          = categoryDao,
        settingsRepository   = settingsRepository,
        exportDataUseCase    = exportDataUseCase,
        bulkSmsImportUseCase = bulkSmsImportUseCase
    )

    @Before fun setUp() {
        every { accountDao.getAllAccounts()  } returns flowOf(emptyList())
        every { categoryDao.getAllCategories() } returns flowOf(emptyList())
        every { settingsRepository.budgetAlertsEnabled      } returns flowOf(true)
        every { settingsRepository.weeklySummaryEnabled     } returns flowOf(true)
        every { settingsRepository.unloggedRemindersEnabled } returns flowOf(true)
        every { settingsRepository.reminderIntervalHours    } returns flowOf(4)
        every { settingsRepository.themeMode                } returns flowOf(ThemeMode.SYSTEM)
        every { settingsRepository.dynamicColorEnabled      } returns flowOf(true)
    }

    // ── ImportState machine ──────────────────────────────────────────────────

    @Test fun `importState starts as Idle`() = runTest {
        val vm = buildVm()
        assertThat(vm.importState.value).isInstanceOf(ImportState.Idle::class.java)
    }

    @Test fun `startImport transitions Idle → Loading → Done on success`() = runTest {
        val fromMillis = 1_700_000_000_000L
        coEvery {
            bulkSmsImportUseCase(fromMillis, any())
        } coAnswers {
            // Simulate progress callback being called once
            val onProgress = secondArg<suspend (Int, Int) -> Unit>()
            onProgress(5, 10)
            Result.success(BulkSmsImportUseCase.ImportResult(imported = 8, skipped = 2, total = 10))
        }

        val vm = buildVm()

        vm.importState.test {
            assertThat(awaitItem()).isInstanceOf(ImportState.Idle::class.java)

            vm.startImport(fromMillis)

            // Loading(0, 0) emitted synchronously before coroutine runs
            val loading0 = awaitItem()
            assertThat(loading0).isInstanceOf(ImportState.Loading::class.java)
            loading0 as ImportState.Loading
            assertThat(loading0.read).isEqualTo(0)
            assertThat(loading0.total).isEqualTo(0)

            // Loading(5, 10) from progress callback
            val loading5 = awaitItem()
            assertThat(loading5).isInstanceOf(ImportState.Loading::class.java)
            loading5 as ImportState.Loading
            assertThat(loading5.read).isEqualTo(5)
            assertThat(loading5.total).isEqualTo(10)

            // Done
            val done = awaitItem()
            assertThat(done).isInstanceOf(ImportState.Done::class.java)
            done as ImportState.Done
            assertThat(done.imported).isEqualTo(8)
            assertThat(done.skipped).isEqualTo(2)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `startImport transitions to Error on failure`() = runTest {
        coEvery {
            bulkSmsImportUseCase(any(), any())
        } returns Result.failure(RuntimeException("SMS permission denied"))

        val vm = buildVm()

        vm.importState.test {
            awaitItem() // Idle
            vm.startImport(1_700_000_000_000L)
            awaitItem() // Loading(0,0)
            val error = awaitItem()
            assertThat(error).isInstanceOf(ImportState.Error::class.java)
            error as ImportState.Error
            assertThat(error.message).contains("SMS permission denied")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `startImport error message falls back when exception message is null`() = runTest {
        coEvery {
            bulkSmsImportUseCase(any(), any())
        } returns Result.failure(RuntimeException())  // null message

        val vm = buildVm()
        vm.importState.test {
            awaitItem()
            vm.startImport(0L)
            awaitItem() // Loading
            val error = awaitItem()
            error as ImportState.Error
            assertThat(error.message).isNotEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `clearImportState resets to Idle`() = runTest {
        coEvery {
            bulkSmsImportUseCase(any(), any())
        } returns Result.success(BulkSmsImportUseCase.ImportResult(1, 0, 1))

        val vm = buildVm()
        vm.startImport(0L)

        assertThat(vm.importState.value).isInstanceOf(ImportState.Done::class.java)

        vm.clearImportState()

        assertThat(vm.importState.value).isInstanceOf(ImportState.Idle::class.java)
    }

    @Test fun `Done state carries correct imported and skipped counts`() = runTest {
        coEvery {
            bulkSmsImportUseCase(any(), any())
        } returns Result.success(BulkSmsImportUseCase.ImportResult(imported = 15, skipped = 3, total = 18))

        val vm = buildVm()
        vm.startImport(0L)

        val done = vm.importState.value as ImportState.Done
        assertThat(done.imported).isEqualTo(15)
        assertThat(done.skipped).isEqualTo(3)
    }

    // ── Export ───────────────────────────────────────────────────────────────

    @Test fun `exportData success sets shareUri`() = runTest {
        val uri = mockk<android.net.Uri>()
        coEvery { exportDataUseCase(any()) } returns Result.success(uri)

        val vm = buildVm()
        vm.exportData()

        assertThat(vm.shareUri.value).isEqualTo(uri)
    }

    @Test fun `exportData failure sets exportMessage`() = runTest {
        coEvery { exportDataUseCase(any()) } returns Result.failure(RuntimeException("disk full"))

        val vm = buildVm()
        vm.exportData()

        assertThat(vm.exportMessage.value).contains("Export failed")
        assertThat(vm.exportMessage.value).contains("disk full")
    }

    @Test fun `clearShareUri nullifies shareUri`() = runTest {
        val uri = mockk<android.net.Uri>()
        coEvery { exportDataUseCase(any()) } returns Result.success(uri)
        val vm = buildVm()
        vm.exportData()

        vm.clearShareUri()

        assertThat(vm.shareUri.value).isNull()
    }

    @Test fun `clearExportMessage nullifies exportMessage`() = runTest {
        coEvery { exportDataUseCase(any()) } returns Result.failure(RuntimeException("err"))
        val vm = buildVm()
        vm.exportData()

        vm.clearExportMessage()

        assertThat(vm.exportMessage.value).isNull()
    }

    // ── Account CRUD ─────────────────────────────────────────────────────────

    @Test fun `addAccount inserts correct AccountEntity`() = runTest {
        coJustRun { accountDao.insertAccount(any()) }

        val vm = buildVm()
        vm.addAccount(
            name     = "HDFC Debit",
            last4    = "1234",
            type     = AccountType.DEBIT,
            colorHex = "#FF0000"
        )

        coVerify {
            accountDao.insertAccount(withArg { entity ->
                assertThat(entity.name).isEqualTo("HDFC Debit")
                assertThat(entity.last4).isEqualTo("1234")
                assertThat(entity.type).isEqualTo(AccountType.DEBIT)
                assertThat(entity.colorHex).isEqualTo("#FF0000")
            })
        }
    }

    @Test fun `deleteAccount delegates to accountDao`() = runTest {
        val account = AccountEntity(name = "SBI", last4 = "5678", type = AccountType.DEBIT, colorHex = "#000")
        coJustRun { accountDao.deleteAccount(any()) }
        val vm = buildVm()

        vm.deleteAccount(account)

        coVerify { accountDao.deleteAccount(account) }
    }

    // ── Category CRUD ────────────────────────────────────────────────────────

    @Test fun `addCategory inserts CategoryEntity with isCustom true`() = runTest {
        coJustRun { categoryDao.insertCategory(any()) }

        val vm = buildVm()
        vm.addCategory(name = "Pet Care", emoji = "🐶")

        coVerify {
            categoryDao.insertCategory(withArg { entity ->
                assertThat(entity.name).isEqualTo("Pet Care")
                assertThat(entity.emoji).isEqualTo("🐶")
                assertThat(entity.isCustom).isTrue()
            })
        }
    }

    @Test fun `toggleCategory flips enabled status`() = runTest {
        coJustRun { categoryDao.updateEnabledStatus(any(), any()) }

        val enabledCategory = CategoryEntity(
            id = "cat-1", name = "Food", emoji = "🍔",
            isCustom = false, sortOrder = 1, isEnabled = true
        )
        val vm = buildVm()

        vm.toggleCategory(enabledCategory)

        coVerify { categoryDao.updateEnabledStatus("cat-1", false) }
    }

    @Test fun `toggleCategory on disabled category enables it`() = runTest {
        coJustRun { categoryDao.updateEnabledStatus(any(), any()) }

        val disabledCategory = CategoryEntity(
            id = "cat-2", name = "Travel", emoji = "✈️",
            isCustom = false, sortOrder = 5, isEnabled = false
        )
        val vm = buildVm()

        vm.toggleCategory(disabledCategory)

        coVerify { categoryDao.updateEnabledStatus("cat-2", true) }
    }

    // ── Notification settings ────────────────────────────────────────────────

    @Test fun `setBudgetAlertsEnabled delegates to repository`() = runTest {
        coJustRun { settingsRepository.setBudgetAlertsEnabled(any()) }

        val vm = buildVm()
        vm.setBudgetAlertsEnabled(false)

        coVerify { settingsRepository.setBudgetAlertsEnabled(false) }
    }

    @Test fun `setWeeklySummaryEnabled delegates to repository`() = runTest {
        coJustRun { settingsRepository.setWeeklySummaryEnabled(any()) }

        val vm = buildVm()
        vm.setWeeklySummaryEnabled(false)

        coVerify { settingsRepository.setWeeklySummaryEnabled(false) }
    }

    @Test fun `setReminderInterval delegates to repository`() = runTest {
        coJustRun { settingsRepository.setReminderIntervalHours(any()) }

        val vm = buildVm()
        vm.setReminderInterval(8)

        coVerify { settingsRepository.setReminderIntervalHours(8) }
    }

    // ── Appearance ───────────────────────────────────────────────────────────

    @Test fun `setThemeMode delegates to repository`() = runTest {
        coJustRun { settingsRepository.setThemeMode(any()) }

        val vm = buildVm()
        vm.setThemeMode(ThemeMode.DARK)

        coVerify { settingsRepository.setThemeMode(ThemeMode.DARK) }
    }

    @Test fun `setDynamicColorEnabled delegates to repository`() = runTest {
        coJustRun { settingsRepository.setDynamicColorEnabled(any()) }

        val vm = buildVm()
        vm.setDynamicColorEnabled(false)

        coVerify { settingsRepository.setDynamicColorEnabled(false) }
    }
}
