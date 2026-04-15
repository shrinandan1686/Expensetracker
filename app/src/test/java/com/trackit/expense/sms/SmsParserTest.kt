package com.trackit.expense.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SmsParser].
 *
 * Covers:
 * A. Five canonical Indian bank SMS formats (original suite)
 * B. Amount regex edge cases: lakh format, INR decimal, RS uppercase, spaces
 * C. Duplicate-detection window helpers
 * D. Full parse() integration tests
 */
class SmsParserTest {

    // ─────────────────────────────────────────────────────────────────────────────
    // TEST FIXTURES
    // ─────────────────────────────────────────────────────────────────────────────

    private val SMS_1 = "INR 450.00 debited from A/c XX1234 to UPI VPA swiggy@icici"
    private val SMS_2 = "Rs.1,200 spent on HDFC Credit Card at Amazon"
    private val SMS_3 = "₹89 paid to Zomato via PhonePe UPI"
    private val SMS_4 = "Your A/c XX5678 debited Rs 500 on 30-03-26"
    private val SMS_5 = "UPI txn: ₹200 to merchant@paytm from SBI"

    // ─────────────────────────────────────────────────────────────────────────────
    // A. AMOUNT PARSING — original suite
    // ─────────────────────────────────────────────────────────────────────────────

    @Test fun `parseAmount - INR prefix with decimal - SMS_1`() =
        assertEquals(450.00, SmsParser.parseAmount(SMS_1)!!, 0.001)

    @Test fun `parseAmount - Rs dot prefix with comma separator - SMS_2`() =
        assertEquals(1200.00, SmsParser.parseAmount(SMS_2)!!, 0.001)

    @Test fun `parseAmount - rupee symbol no decimal - SMS_3`() =
        assertEquals(89.00, SmsParser.parseAmount(SMS_3)!!, 0.001)

    @Test fun `parseAmount - Rs space prefix no decimal - SMS_4`() =
        assertEquals(500.00, SmsParser.parseAmount(SMS_4)!!, 0.001)

    @Test fun `parseAmount - rupee symbol in UPI txn SMS - SMS_5`() =
        assertEquals(200.00, SmsParser.parseAmount(SMS_5)!!, 0.001)

    @Test fun `parseAmount - returns null for SMS with no amount`() =
        assertNull(SmsParser.parseAmount("Your OTP is 123456. Do not share."))

    // ─────────────────────────────────────────────────────────────────────────────
    // B. AMOUNT PARSING — edge cases
    // ─────────────────────────────────────────────────────────────────────────────

    /** ₹1,00,000 — Indian lakh format with two commas. */
    @Test fun `parseAmount - Indian lakh format ₹1,00,000`() {
        val result = SmsParser.parseAmount("₹1,00,000 debited from A/c")
        assertEquals(100_000.00, result!!, 0.001)
    }

    /** INR 1,00,000.00 — INR prefix + lakh + decimal. */
    @Test fun `parseAmount - INR lakh format 1,00,000 dot 00`() {
        val result = SmsParser.parseAmount("INR 1,00,000.00 debited from A/c")
        assertEquals(100_000.00, result!!, 0.001)
    }

    /** INR 1000.00 — basic decimal. */
    @Test fun `parseAmount - INR 1000 dot 00`() {
        val result = SmsParser.parseAmount("INR 1000.00 debited")
        assertEquals(1000.00, result!!, 0.001)
    }

    /** INR 1,000.00 — INR prefix with comma-separator. */
    @Test fun `parseAmount - INR 1,000 dot 00`() {
        val result = SmsParser.parseAmount("INR 1,000.00 debited")
        assertEquals(1000.00, result!!, 0.001)
    }

    /** RS 500 — fully uppercase (no dots). CASE_INSENSITIVE handles this. */
    @Test fun `parseAmount - RS uppercase no dot`() {
        val result = SmsParser.parseAmount("RS 500 debited from A/c XX1111")
        assertEquals(500.00, result!!, 0.001)
    }

    /** Rs 500 — mixed case without dot. */
    @Test fun `parseAmount - Rs no dot`() {
        val result = SmsParser.parseAmount("Rs 500 debited from A/c XX1111")
        assertEquals(500.00, result!!, 0.001)
    }

    /** rs 350.50 — fully lowercase prefix. */
    @Test fun `parseAmount - rs lowercase prefix`() {
        val result = SmsParser.parseAmount("rs 350.50 debited")
        assertEquals(350.50, result!!, 0.001)
    }

    /** Rs. 500 — dot followed by space before the amount. */
    @Test fun `parseAmount - Rs dot space before amount`() {
        val result = SmsParser.parseAmount("Rs. 500 sent to merchant via UPI")
        assertEquals(500.00, result!!, 0.001)
    }

    /** Rs.500 — dot with NO space. */
    @Test fun `parseAmount - Rs dot no space`() {
        val result = SmsParser.parseAmount("Rs.500 paid to Swiggy")
        assertEquals(500.00, result!!, 0.001)
    }

    /** RS. 1,000.00 — uppercase RS with dot and space. */
    @Test fun `parseAmount - RS dot space 1,000 dot 00`() {
        val result = SmsParser.parseAmount("RS. 1,000.00 debited")
        assertEquals(1000.00, result!!, 0.001)
    }

    /** inr 250 — fully lowercase INR. */
    @Test fun `parseAmount - inr lowercase prefix`() {
        val result = SmsParser.parseAmount("inr 250 paid to Zomato")
        assertEquals(250.00, result!!, 0.001)
    }

    /** ₹99.50 — sub-rupee paise check. */
    @Test fun `parseAmount - paise decimal`() {
        val result = SmsParser.parseAmount("₹99.50 paid to Swiggy via UPI")
        assertEquals(99.50, result!!, 0.001)
    }

    /** Amount at start of string with no leading context. */
    @Test fun `parseAmount - amount at start of string`() {
        val result = SmsParser.parseAmount("₹1,200 debited.")
        assertEquals(1200.00, result!!, 0.001)
    }

    /** Ensure OTP number (6 digits) without a currency prefix is NOT matched. */
    @Test fun `parseAmount - returns null for bare 6-digit OTP number`() {
        assertNull(SmsParser.parseAmount("Your OTP is 482910. Valid for 5 minutes."))
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // MERCHANT PARSING (original + extras)
    // ─────────────────────────────────────────────────────────────────────────────

    @Test fun `parseMerchant - VPA from VPA keyword - SMS_1`() =
        assertEquals("swiggy@icici", SmsParser.parseMerchant(SMS_1))

    @Test fun `parseMerchant - at-merchant - SMS_2`() =
        assertEquals("Amazon", SmsParser.parseMerchant(SMS_2))

    @Test fun `parseMerchant - to-merchant before via - SMS_3`() =
        assertEquals("Zomato", SmsParser.parseMerchant(SMS_3))

    @Test fun `parseMerchant - returns null for pure account-debit - SMS_4`() =
        assertNull(SmsParser.parseMerchant(SMS_4))

    @Test fun `parseMerchant - VPA in to-position - SMS_5`() =
        assertEquals("merchant@paytm", SmsParser.parseMerchant(SMS_5))

    @Test fun `parseMerchant - VPA has priority over to-merchant`() {
        val sms = "INR 100 debited to Zomato. UPI VPA zomato@icici"
        assertEquals("zomato@icici", SmsParser.parseMerchant(sms))
    }

    @Test fun `parseMerchant - does not match UPI gateway keyword`() =
        assertNull(SmsParser.parseMerchant("₹50 debited to UPI from A/c XX1111"))

    @Test fun `parseMerchant - does not match bare bank name`() =
        assertNull(SmsParser.parseMerchant("₹500 debited from A/c XX9999 to SBI"))

    // ─────────────────────────────────────────────────────────────────────────────
    // ACCOUNT LAST-4 PARSING
    // ─────────────────────────────────────────────────────────────────────────────

    @Test fun `parseAccountLast4 - A/c XX prefix - SMS_1`() =
        assertEquals("1234", SmsParser.parseAccountLast4(SMS_1))

    @Test fun `parseAccountLast4 - returns null when absent - SMS_2`() =
        assertNull(SmsParser.parseAccountLast4(SMS_2))

    @Test fun `parseAccountLast4 - A/c XX prefix - SMS_4`() =
        assertEquals("5678", SmsParser.parseAccountLast4(SMS_4))

    @Test fun `parseAccountLast4 - Card XX prefix`() =
        assertEquals("4321", SmsParser.parseAccountLast4("₹2500 spent on Card XX4321 at BigBazaar"))

    @Test fun `parseAccountLast4 - lowercase a/c variant`() =
        assertEquals("3456", SmsParser.parseAccountLast4("Rs 100 debited from a/c xx3456 to UPI"))

    // ─────────────────────────────────────────────────────────────────────────────
    // PAYMENT MODE PARSING
    // ─────────────────────────────────────────────────────────────────────────────

    @Test fun `parsePaymentMode - UPI - SMS_1`() = assertEquals("UPI", SmsParser.parsePaymentMode(SMS_1))
    @Test fun `parsePaymentMode - Credit Card - SMS_2`() = assertEquals("Credit Card", SmsParser.parsePaymentMode(SMS_2))
    @Test fun `parsePaymentMode - PhonePe before UPI - SMS_3`() = assertEquals("PhonePe", SmsParser.parsePaymentMode(SMS_3))
    @Test fun `parsePaymentMode - null when absent - SMS_4`() = assertNull(SmsParser.parsePaymentMode(SMS_4))
    @Test fun `parsePaymentMode - UPI txn - SMS_5`() = assertEquals("UPI", SmsParser.parsePaymentMode(SMS_5))
    @Test fun `parsePaymentMode - GPay`() = assertEquals("GPay", SmsParser.parsePaymentMode("₹300 paid via GPay UPI"))
    @Test fun `parsePaymentMode - NEFT`() = assertEquals("NEFT", SmsParser.parsePaymentMode("Rs 5000 transferred via NEFT from A/c XX1111"))
    @Test fun `parsePaymentMode - Debit Card`() = assertEquals("Debit Card", SmsParser.parsePaymentMode("₹1500 spent using Debit Card at Flipkart"))

    // ─────────────────────────────────────────────────────────────────────────────
    // TRANSACTION TYPE PARSING
    // ─────────────────────────────────────────────────────────────────────────────

    @Test fun `parseTransactionType - debited - SMS_1`() = assertEquals("debited", SmsParser.parseTransactionType(SMS_1))
    @Test fun `parseTransactionType - spent - SMS_2`()   = assertEquals("spent",   SmsParser.parseTransactionType(SMS_2))
    @Test fun `parseTransactionType - paid - SMS_3`()    = assertEquals("paid",    SmsParser.parseTransactionType(SMS_3))
    @Test fun `parseTransactionType - debited - SMS_4`() = assertEquals("debited", SmsParser.parseTransactionType(SMS_4))
    @Test fun `parseTransactionType - txn - SMS_5`()     = assertEquals("txn",     SmsParser.parseTransactionType(SMS_5))
    @Test fun `parseTransactionType - purchased`()       = assertEquals("purchased", SmsParser.parseTransactionType("₹999 purchased on FlipkartPay"))
    @Test fun `parseTransactionType - sent`()            = assertEquals("sent",    SmsParser.parseTransactionType("Rs. 100.00 sent to SHRINANDAN via PhonePe"))
    @Test fun `parseTransactionType - transferred`()     = assertEquals("transferred", SmsParser.parseTransactionType("Amt transferred: INR 500 to A/c XX1234"))

    // ─────────────────────────────────────────────────────────────────────────────
    // C. DUPLICATE-DETECTION WINDOW HELPERS
    //
    // These tests verify the pure business logic of the duplicate check window
    // (amount tolerance ±₹5, time window ±60 s) independently of any DB call.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test fun `duplicate window - amount within tolerance is candidate`() {
        val parsedAmount = 500.0
        val candidateAmount = 503.0  // within ±5
        assertTrue(candidateAmount in (parsedAmount - AMOUNT_TOLERANCE_INR)..(parsedAmount + AMOUNT_TOLERANCE_INR))
    }

    @Test fun `duplicate window - amount exactly on upper tolerance boundary is candidate`() {
        val parsedAmount = 500.0
        val candidateAmount = 505.0  // exactly +5
        assertTrue(candidateAmount in (parsedAmount - AMOUNT_TOLERANCE_INR)..(parsedAmount + AMOUNT_TOLERANCE_INR))
    }

    @Test fun `duplicate window - amount outside tolerance is NOT candidate`() {
        val parsedAmount = 500.0
        val candidateAmount = 506.0  // +6, exceeds ±5
        assertTrue(candidateAmount !in (parsedAmount - AMOUNT_TOLERANCE_INR)..(parsedAmount + AMOUNT_TOLERANCE_INR))
    }

    @Test fun `duplicate window - timestamp within 60s is candidate`() {
        val t1 = 1_000_000L
        val t2 = t1 + 59_000L  // 59 s later — within window
        assertTrue(t2 in (t1 - TIME_WINDOW_MS)..(t1 + TIME_WINDOW_MS))
    }

    @Test fun `duplicate window - timestamp exactly on 60s boundary is candidate`() {
        val t1 = 1_000_000L
        val t2 = t1 + TIME_WINDOW_MS  // exactly 60 s
        assertTrue(t2 in (t1 - TIME_WINDOW_MS)..(t1 + TIME_WINDOW_MS))
    }

    @Test fun `duplicate window - timestamp beyond 60s is NOT candidate`() {
        val t1 = 1_000_000L
        val t2 = t1 + 61_000L  // 61 s — outside window
        assertTrue(t2 !in (t1 - TIME_WINDOW_MS)..(t1 + TIME_WINDOW_MS))
    }

    @Test fun `duplicate window - negative time delta (earlier SMS) is candidate`() {
        val t1 = 1_000_000L
        val t2 = t1 - 30_000L  // 30 s earlier — inside window
        assertTrue(t2 in (t1 - TIME_WINDOW_MS)..(t1 + TIME_WINDOW_MS))
    }

    @Test fun `duplicate window - AMOUNT_TOLERANCE_INR is 5 rupees`() =
        assertEquals(5.0, AMOUNT_TOLERANCE_INR, 0.0)

    @Test fun `duplicate window - TIME_WINDOW_MS is 60 seconds`() =
        assertEquals(60_000L, TIME_WINDOW_MS)

    // ─────────────────────────────────────────────────────────────────────────────
    // D. FULL parse() — END-TO-END
    // ─────────────────────────────────────────────────────────────────────────────

    @Test fun `parse - SMS_1 full result`() {
        val r = SmsParser.parse(SMS_1)!!
        assertEquals(450.00, r.amount, 0.001)
        assertEquals("swiggy@icici", r.merchant)
        assertEquals("1234", r.accountLast4)
        assertEquals("UPI", r.paymentMode)
        assertEquals("debited", r.transactionType)
        assertEquals(SMS_1, r.rawSms)
        assertTrue(r.isUpi)
    }

    @Test fun `parse - SMS_2 full result`() {
        val r = SmsParser.parse(SMS_2)!!
        assertEquals(1200.00, r.amount, 0.001)
        assertEquals("Amazon", r.merchant)
        assertNull(r.accountLast4)
        assertEquals("Credit Card", r.paymentMode)
        assertEquals("spent", r.transactionType)
    }

    @Test fun `parse - SMS_3 full result`() {
        val r = SmsParser.parse(SMS_3)!!
        assertEquals(89.00, r.amount, 0.001)
        assertEquals("Zomato", r.merchant)
        assertNull(r.accountLast4)
        assertEquals("PhonePe", r.paymentMode)
        assertEquals("paid", r.transactionType)
        assertTrue(r.isUpi)
    }

    @Test fun `parse - SMS_4 full result`() {
        val r = SmsParser.parse(SMS_4)!!
        assertEquals(500.00, r.amount, 0.001)
        assertNull(r.merchant)
        assertEquals("5678", r.accountLast4)
        assertEquals("debited", r.transactionType)
    }

    @Test fun `parse - SMS_5 full result`() {
        val r = SmsParser.parse(SMS_5)!!
        assertEquals(200.00, r.amount, 0.001)
        assertEquals("merchant@paytm", r.merchant)
        assertNull(r.accountLast4)
        assertEquals("UPI", r.paymentMode)
        assertEquals("txn", r.transactionType)
        assertTrue(r.isUpi)
    }

    @Test fun `parse - lakh amount end-to-end`() {
        val sms = "INR 1,00,000.00 debited from A/c XX1234 to UPI VPA test@sbi"
        val r   = SmsParser.parse(sms)!!
        assertEquals(100_000.00, r.amount, 0.001)
    }

    @Test fun `parse - RS uppercase end-to-end`() {
        val sms = "RS 999 debited from your account via UPI"
        val r   = SmsParser.parse(sms)!!
        assertEquals(999.00, r.amount, 0.001)
    }

    @Test fun `parse - Rs dot space end-to-end`() {
        val sms = "Rs. 500 sent to SHRINANDAN via PhonePe. Ref No: 123456"
        val r   = SmsParser.parse(sms)!!
        assertEquals(500.00, r.amount, 0.001)
        assertEquals("SHRINANDAN", r.merchant)
        assertEquals("PhonePe", r.paymentMode)
    }

    @Test fun `parse - sent to format`() {
        val sms = "Rs. 100.00 sent to SHRINANDAN via PhonePe. Ref No: 123456"
        val r   = SmsParser.parse(sms)!!
        assertEquals(100.0, r.amount, 0.001)
        assertEquals("SHRINANDAN", r.merchant)
        assertEquals("PhonePe", r.paymentMode)
        assertEquals("sent", r.transactionType)
    }

    @Test fun `parse - transferred to format`() {
        val sms = "INR 500.00 transferred to John Doe from A/c XX9876"
        val r   = SmsParser.parse(sms)!!
        assertEquals(500.0, r.amount, 0.001)
        assertEquals("John Doe", r.merchant)
        assertEquals("9876", r.accountLast4)
        assertEquals("transferred", r.transactionType)
    }

    @Test fun `parse - HDFC multi-line format`() {
        val sms = """
            Sent Rs.1.00
            From HDFC Bank A/C *7724
            To Shri Nandan
            On 10/04/26
            Ref 646603865440
        """.trimIndent()
        val r = SmsParser.parse(sms)!!
        assertEquals(1.0, r.amount, 0.001)
        assertEquals("Shri Nandan", r.merchant)
        assertEquals("7724", r.accountLast4)
    }

    @Test fun `parse - returns null for OTP SMS`() =
        assertNull(SmsParser.parse("Your OTP is 482910. Valid for 5 minutes. Do not share."))

    @Test fun `parse - returns null for promotional SMS`() =
        assertNull(SmsParser.parse("Get 50% off on your next order! Use code SAVE50."))

    @Test fun `parse - amountInPaise is correct`() =
        assertEquals(45000L, SmsParser.parse(SMS_1)!!.amountInPaise)

    @Test fun `parse - amountInPaise handles sub-rupee decimal`() =
        assertEquals(9950L, SmsParser.parse("₹99.50 paid to Swiggy via UPI")!!.amountInPaise)

    // ─────────────────────────────────────────────────────────────────────────────
    // KNOWN_SENDERS filter
    // ─────────────────────────────────────────────────────────────────────────────

    @Test fun `KNOWN_SENDERS contains all required bank IDs`() {
        listOf("HDFCBK", "HDFCSMS", "SBISMS", "SBIUPI", "ICICIB",
               "AXISBK", "KOTAKB", "PAYTMB", "PHONEP", "GOOGLE").forEach { id ->
            assertTrue("$id must be in KNOWN_SENDERS", id in SmsReceiver.KNOWN_SENDERS)
        }
    }

    @Test fun `KNOWN_SENDERS does not contain random strings`() =
        assertTrue("RANDOMID" !in SmsReceiver.KNOWN_SENDERS)
}
