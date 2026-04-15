package com.trackit.expense.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.trackit.expense.data.local.dao.ExpenseDao
import com.trackit.expense.util.SmsInboxReader
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [BulkSmsImportUseCase].
 *
 * All Android dependencies (ExpenseDao, SmsInboxReader) are mocked so these
 * run entirely on the JVM — no emulator needed.
 */
class BulkSmsImportUseCaseTest {

    private val smsInboxReader   = mockk<SmsInboxReader>()
    private val expenseDao       = mockk<ExpenseDao>()
    private val addExpenseUseCase = mockk<AddExpenseUseCase>()

    private lateinit var useCase: BulkSmsImportUseCase

    // Fixed timestamp so SMS parsing is deterministic
    private val T = 1_700_000_000_000L

    @Before fun setUp() {
        useCase = BulkSmsImportUseCase(smsInboxReader, expenseDao, addExpenseUseCase)
    }

    // ── Empty inbox ──────────────────────────────────────────────────────────

    @Test fun `empty inbox returns zero imported and zero skipped`() = runTest {
        coEvery { smsInboxReader.readSince(any()) } returns emptyList()

        val result = useCase(T).getOrThrow()

        assertThat(result.imported).isEqualTo(0)
        assertThat(result.skipped).isEqualTo(0)
        assertThat(result.total).isEqualTo(0)
    }

    // ── Non-parseable SMS ────────────────────────────────────────────────────

    @Test fun `non-parseable SMS is counted as skipped`() = runTest {
        coEvery { smsInboxReader.readSince(any()) } returns listOf(
            SmsInboxReader.RawSms("Your OTP is 123456. Do not share.", T, "IN-HDFCBK")
        )

        val result = useCase(T).getOrThrow()

        assertThat(result.imported).isEqualTo(0)
        assertThat(result.skipped).isEqualTo(1)
        assertThat(result.total).isEqualTo(1)
        coVerify(exactly = 0) { addExpenseUseCase(any()) }
    }

    // ── Duplicate detection ──────────────────────────────────────────────────

    @Test fun `SMS already in DB is skipped as duplicate`() = runTest {
        coEvery { smsInboxReader.readSince(any()) } returns listOf(
            SmsInboxReader.RawSms("INR 450.00 debited from A/c XX1234 to UPI VPA swiggy@icici", T, "IN-HDFCBK")
        )
        coEvery { expenseDao.existsByAmountAndTime(450.0, T - 30_000L, T + 30_000L) } returns true

        val result = useCase(T).getOrThrow()

        assertThat(result.imported).isEqualTo(0)
        assertThat(result.skipped).isEqualTo(1)
        coVerify(exactly = 0) { addExpenseUseCase(any()) }
    }

    // ── New valid SMS ────────────────────────────────────────────────────────

    @Test fun `new valid SMS is saved and counted as imported`() = runTest {
        coEvery { smsInboxReader.readSince(any()) } returns listOf(
            SmsInboxReader.RawSms("INR 450.00 debited from A/c XX1234 to UPI VPA swiggy@icici", T, "IN-HDFCBK")
        )
        coEvery { expenseDao.existsByAmountAndTime(any(), any(), any()) } returns false
        coEvery { addExpenseUseCase(any()) } returns Result.success("some-id")

        val result = useCase(T).getOrThrow()

        assertThat(result.imported).isEqualTo(1)
        assertThat(result.skipped).isEqualTo(0)
        assertThat(result.total).isEqualTo(1)
    }

    @Test fun `saved expense has isLogged set to false`() = runTest {
        coEvery { smsInboxReader.readSince(any()) } returns listOf(
            SmsInboxReader.RawSms("₹89 paid to Zomato via PhonePe UPI", T, "IN-PHONEP")
        )
        coEvery { expenseDao.existsByAmountAndTime(any(), any(), any()) } returns false
        val savedExpenseSlot = slot<com.trackit.expense.domain.model.Expense>()
        coEvery { addExpenseUseCase(capture(savedExpenseSlot)) } returns Result.success("id-1")

        useCase(T)

        assertThat(savedExpenseSlot.captured.isLogged).isFalse()
        assertThat(savedExpenseSlot.captured.isSynced).isFalse()
    }

    @Test fun `saved expense category is inferred from merchant`() = runTest {
        coEvery { smsInboxReader.readSince(any()) } returns listOf(
            SmsInboxReader.RawSms("₹89 paid to Zomato via PhonePe UPI", T, "IN-PHONEP")
        )
        coEvery { expenseDao.existsByAmountAndTime(any(), any(), any()) } returns false
        val savedExpenseSlot = slot<com.trackit.expense.domain.model.Expense>()
        coEvery { addExpenseUseCase(capture(savedExpenseSlot)) } returns Result.success("id-1")

        useCase(T)

        assertThat(savedExpenseSlot.captured.category).isEqualTo("Food & Drinks")
    }

    @Test fun `saved expense preserves raw SMS body`() = runTest {
        val rawBody = "INR 450.00 debited from A/c XX1234 to UPI VPA swiggy@icici"
        coEvery { smsInboxReader.readSince(any()) } returns listOf(
            SmsInboxReader.RawSms(rawBody, T, "IN-HDFCBK")
        )
        coEvery { expenseDao.existsByAmountAndTime(any(), any(), any()) } returns false
        val slot = slot<com.trackit.expense.domain.model.Expense>()
        coEvery { addExpenseUseCase(capture(slot)) } returns Result.success("id-1")

        useCase(T)

        assertThat(slot.captured.rawSms).isEqualTo(rawBody)
    }

    // ── Mixed results ────────────────────────────────────────────────────────

    @Test fun `mixed inbox counts imported and skipped correctly`() = runTest {
        coEvery { smsInboxReader.readSince(any()) } returns listOf(
            SmsInboxReader.RawSms("INR 450.00 debited from A/c XX1234 to UPI VPA swiggy@icici", T, "IN-HDFCBK"),
            SmsInboxReader.RawSms("Your OTP is 123456", T + 1000, "IN-HDFCBK"),
            SmsInboxReader.RawSms("₹200 paid to merchant@paytm from SBI", T + 2000, "IN-SBIUPI"),
        )
        coEvery { expenseDao.existsByAmountAndTime(450.0, any(), any()) } returns false
        coEvery { expenseDao.existsByAmountAndTime(200.0, any(), any()) } returns true // duplicate
        coEvery { addExpenseUseCase(any()) } returns Result.success("id-1")

        val result = useCase(T - 1000L).getOrThrow()

        assertThat(result.total).isEqualTo(3)
        assertThat(result.imported).isEqualTo(1)
        assertThat(result.skipped).isEqualTo(2)
    }

    // ── addExpenseUseCase failure ────────────────────────────────────────────

    @Test fun `addExpenseUseCase failure increments skipped not imported`() = runTest {
        coEvery { smsInboxReader.readSince(any()) } returns listOf(
            SmsInboxReader.RawSms("INR 100.00 debited from A/c XX9999 to UPI VPA test@sbi", T, "IN-SBIUPI")
        )
        coEvery { expenseDao.existsByAmountAndTime(any(), any(), any()) } returns false
        coEvery { addExpenseUseCase(any()) } returns Result.failure(RuntimeException("DB full"))

        val result = useCase(T).getOrThrow()

        assertThat(result.imported).isEqualTo(0)
        assertThat(result.skipped).isEqualTo(1)
    }

    // ── Progress callback ────────────────────────────────────────────────────

    @Test fun `progress callback is invoked for every message`() = runTest {
        val messages = listOf(
            SmsInboxReader.RawSms("INR 100.00 debited to UPI VPA a@sbi", T,      "IN-SBIUPI"),
            SmsInboxReader.RawSms("INR 200.00 debited to UPI VPA b@sbi", T+1000, "IN-SBIUPI"),
            SmsInboxReader.RawSms("INR 300.00 debited to UPI VPA c@sbi", T+2000, "IN-SBIUPI"),
        )
        coEvery { smsInboxReader.readSince(any()) } returns messages
        coEvery { expenseDao.existsByAmountAndTime(any(), any(), any()) } returns false
        coEvery { addExpenseUseCase(any()) } returns Result.success("id")

        val progressCalls = mutableListOf<Pair<Int, Int>>()
        useCase(T - 1000L) { read, total -> progressCalls.add(read to total) }

        assertThat(progressCalls).hasSize(3)
        assertThat(progressCalls[0]).isEqualTo(1 to 3)
        assertThat(progressCalls[1]).isEqualTo(2 to 3)
        assertThat(progressCalls[2]).isEqualTo(3 to 3)
    }
}
