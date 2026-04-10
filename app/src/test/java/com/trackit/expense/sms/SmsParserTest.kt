package com.trackit.expense.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SmsParser].
 *
 * Each test group covers one public parser function and one of the five canonical
 * Indian bank SMS formats used as requirements:
 *
 * - SMS_1: "INR 450.00 debited from A/c XX1234 to UPI VPA swiggy@icici"
 * - SMS_2: "Rs.1,200 spent on HDFC Credit Card at Amazon"
 * - SMS_3: "₹89 paid to Zomato via PhonePe UPI"
 * - SMS_4: "Your A/c XX5678 debited Rs 500 on 30-03-26"
 * - SMS_5: "UPI txn: ₹200 to merchant@paytm from SBI"
 *
 * Tests are grouped by parser function, not by SMS, so each regex can be
 * verified independently.
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
    // AMOUNT PARSING
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `parseAmount - INR prefix with decimal - SMS_1`() {
        val result = SmsParser.parseAmount(SMS_1)
        assertNotNull("Amount must not be null", result)
        assertEquals(450.00, result!!, 0.001)
    }

    @Test
    fun `parseAmount - Rs dot prefix with comma separator - SMS_2`() {
        val result = SmsParser.parseAmount(SMS_2)
        assertNotNull("Amount must not be null", result)
        assertEquals(1200.00, result!!, 0.001)
    }

    @Test
    fun `parseAmount - rupee symbol no decimal - SMS_3`() {
        val result = SmsParser.parseAmount(SMS_3)
        assertNotNull("Amount must not be null", result)
        assertEquals(89.00, result!!, 0.001)
    }

    @Test
    fun `parseAmount - Rs space prefix no decimal - SMS_4`() {
        val result = SmsParser.parseAmount(SMS_4)
        assertNotNull("Amount must not be null", result)
        assertEquals(500.00, result!!, 0.001)
    }

    @Test
    fun `parseAmount - rupee symbol in UPI txn SMS - SMS_5`() {
        val result = SmsParser.parseAmount(SMS_5)
        assertNotNull("Amount must not be null", result)
        assertEquals(200.00, result!!, 0.001)
    }

    @Test
    fun `parseAmount - returns null for SMS with no amount`() {
        val result = SmsParser.parseAmount("Your OTP is 123456. Do not share.")
        assertNull("OTP SMS should return null amount", result)
    }

    @Test
    fun `parseAmount - handles multiple commas in large amount`() {
        val result = SmsParser.parseAmount("INR 1,00,000.00 debited from A/c")
        assertEquals(100000.00, result!!, 0.001)
    }

    @Test
    fun `parseAmount - lowercase rs prefix`() {
        val result = SmsParser.parseAmount("rs 350.50 debited")
        assertEquals(350.50, result!!, 0.001)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // MERCHANT PARSING
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `parseMerchant - VPA handle from explicit VPA keyword - SMS_1`() {
        val result = SmsParser.parseMerchant(SMS_1)
        assertEquals("swiggy@icici", result)
    }

    @Test
    fun `parseMerchant - at-merchant at end of string - SMS_2`() {
        val result = SmsParser.parseMerchant(SMS_2)
        assertEquals("Amazon", result)
    }

    @Test
    fun `parseMerchant - to-merchant before via PhonePe - SMS_3`() {
        val result = SmsParser.parseMerchant(SMS_3)
        assertEquals("Zomato", result)
    }

    @Test
    fun `parseMerchant - returns null when only account debit with no merchant - SMS_4`() {
        // SMS_4 has no VPA, no "to <merchant>", no "at <merchant>"
        val result = SmsParser.parseMerchant(SMS_4)
        assertNull("No merchant expected in account-debit SMS", result)
    }

    @Test
    fun `parseMerchant - VPA handle with domain in to-merchant position - SMS_5`() {
        val result = SmsParser.parseMerchant(SMS_5)
        assertEquals("merchant@paytm", result)
    }

    @Test
    fun `parseMerchant - VPA has priority over to-merchant`() {
        val sms = "INR 100 debited to Zomato. UPI VPA zomato@icici"
        val result = SmsParser.parseMerchant(sms)
        assertEquals("zomato@icici", result)
    }

    @Test
    fun `parseMerchant - does not match gateway keywords as merchant`() {
        // "to UPI" should NOT match — "UPI" is in the exclusion list
        val sms = "₹50 debited to UPI from A/c XX1111"
        val result = SmsParser.parseMerchant(sms)
        assertNull("'UPI' alone is not a merchant", result)
    }

    @Test
    fun `parseMerchant - does not match bank name as merchant`() {
        val sms = "₹500 debited from A/c XX9999 to SBI"
        val result = SmsParser.parseMerchant(sms)
        assertNull("'SBI' alone is not a merchant", result)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // ACCOUNT LAST-4 PARSING
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `parseAccountLast4 - A-c XX prefix four digits - SMS_1`() {
        val result = SmsParser.parseAccountLast4(SMS_1)
        assertEquals("1234", result)
    }

    @Test
    fun `parseAccountLast4 - returns null when no account reference - SMS_2`() {
        val result = SmsParser.parseAccountLast4(SMS_2)
        assertNull("Credit card purchase SMS_2 has no A/c field", result)
    }

    @Test
    fun `parseAccountLast4 - returns null when no account reference - SMS_3`() {
        val result = SmsParser.parseAccountLast4(SMS_3)
        assertNull("SMS_3 has no A/c field", result)
    }

    @Test
    fun `parseAccountLast4 - A-c XX prefix four digits - SMS_4`() {
        val result = SmsParser.parseAccountLast4(SMS_4)
        assertEquals("5678", result)
    }

    @Test
    fun `parseAccountLast4 - returns null when account not present - SMS_5`() {
        val result = SmsParser.parseAccountLast4(SMS_5)
        assertNull("SMS_5 has no A/c field", result)
    }

    @Test
    fun `parseAccountLast4 - Card prefix variant`() {
        val sms = "₹2500 spent on Card XX4321 at BigBazaar"
        val result = SmsParser.parseAccountLast4(sms)
        assertEquals("4321", result)
    }

    @Test
    fun `parseAccountLast4 - lowercase a-c variant`() {
        val sms = "Rs 100 debited from a/c xx3456 to UPI"
        val result = SmsParser.parseAccountLast4(sms)
        assertEquals("3456", result)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // PAYMENT MODE PARSING
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `parsePaymentMode - detects UPI in INR format SMS - SMS_1`() {
        val result = SmsParser.parsePaymentMode(SMS_1)
        assertEquals("UPI", result)
    }

    @Test
    fun `parsePaymentMode - detects Credit Card - SMS_2`() {
        val result = SmsParser.parsePaymentMode(SMS_2)
        assertEquals("Credit Card", result)
    }

    @Test
    fun `parsePaymentMode - detects PhonePe before UPI - SMS_3`() {
        // Both "PhonePe" and "UPI" appear; PhonePe is listed first in the pattern
        val result = SmsParser.parsePaymentMode(SMS_3)
        assertEquals("PhonePe", result)
    }

    @Test
    fun `parsePaymentMode - returns null when no payment mode keyword - SMS_4`() {
        val result = SmsParser.parsePaymentMode(SMS_4)
        assertNull("SMS_4 contains no payment mode keyword", result)
    }

    @Test
    fun `parsePaymentMode - detects UPI from UPI txn SMS - SMS_5`() {
        val result = SmsParser.parsePaymentMode(SMS_5)
        assertEquals("UPI", result)
    }

    @Test
    fun `parsePaymentMode - detects GPay`() {
        val result = SmsParser.parsePaymentMode("₹300 paid via GPay UPI")
        assertEquals("GPay", result)
    }

    @Test
    fun `parsePaymentMode - detects NEFT`() {
        val result = SmsParser.parsePaymentMode("Rs 5000 transferred via NEFT from A/c XX1111")
        assertEquals("NEFT", result)
    }

    @Test
    fun `parsePaymentMode - detects Debit Card`() {
        val result = SmsParser.parsePaymentMode("₹1500 spent using Debit Card at Flipkart")
        assertEquals("Debit Card", result)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // TRANSACTION TYPE PARSING
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `parseTransactionType - debited - SMS_1`() {
        assertEquals("debited", SmsParser.parseTransactionType(SMS_1))
    }

    @Test
    fun `parseTransactionType - spent - SMS_2`() {
        assertEquals("spent", SmsParser.parseTransactionType(SMS_2))
    }

    @Test
    fun `parseTransactionType - paid - SMS_3`() {
        assertEquals("paid", SmsParser.parseTransactionType(SMS_3))
    }

    @Test
    fun `parseTransactionType - debited - SMS_4`() {
        assertEquals("debited", SmsParser.parseTransactionType(SMS_4))
    }

    @Test
    fun `parseTransactionType - txn keyword - SMS_5`() {
        // SMS_5 has "txn" keyword
        assertEquals("txn", SmsParser.parseTransactionType(SMS_5))
    }

    @Test
    fun `parseTransactionType - purchased keyword`() {
        assertEquals("purchased", SmsParser.parseTransactionType("₹999 purchased on FlipkartPay"))
    }

    @Test
    fun `parseTransactionType - sent keyword`() {
        assertEquals("sent", SmsParser.parseTransactionType("Rs. 100.00 sent to SHRINANDAN via PhonePe"))
    }

    @Test
    fun `parseTransactionType - transferred keyword`() {
        assertEquals("transferred", SmsParser.parseTransactionType("Amt transferred: INR 500 to A/c XX1234"))
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // FULL parse() — END-TO-END TESTS
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `parse - SMS_1 full result is correct`() {
        val result = SmsParser.parse(SMS_1)
        assertNotNull(result)
        result!!
        assertEquals(450.00, result.amount, 0.001)
        assertEquals("swiggy@icici", result.merchant)
        assertEquals("1234", result.accountLast4)
        assertEquals("UPI", result.paymentMode)
        assertEquals("debited", result.transactionType)
        assertEquals(SMS_1, result.rawSms)
        assertTrue("isUpi should be true", result.isUpi)
    }

    @Test
    fun `parse - SMS_2 full result is correct`() {
        val result = SmsParser.parse(SMS_2)
        assertNotNull(result)
        result!!
        assertEquals(1200.00, result.amount, 0.001)
        assertEquals("Amazon", result.merchant)
        assertNull(result.accountLast4)
        assertEquals("Credit Card", result.paymentMode)
        assertEquals("spent", result.transactionType)
    }

    @Test
    fun `parse - SMS_3 full result is correct`() {
        val result = SmsParser.parse(SMS_3)
        assertNotNull(result)
        result!!
        assertEquals(89.00, result.amount, 0.001)
        assertEquals("Zomato", result.merchant)
        assertNull(result.accountLast4)
        assertEquals("PhonePe", result.paymentMode)
        assertEquals("paid", result.transactionType)
        assertTrue("isUpi should be true for PhonePe", result.isUpi)
    }

    @Test
    fun `parse - SMS_4 full result is correct`() {
        val result = SmsParser.parse(SMS_4)
        assertNotNull(result)
        result!!
        assertEquals(500.00, result.amount, 0.001)
        assertNull(result.merchant)
        assertEquals("5678", result.accountLast4)
        assertEquals("debited", result.transactionType)
    }

    @Test
    fun `parse - SMS_5 full result is correct`() {
        val result = SmsParser.parse(SMS_5)
        assertNotNull(result)
        result!!
        assertEquals(200.00, result.amount, 0.001)
        assertEquals("merchant@paytm", result.merchant)
        assertNull(result.accountLast4)
        assertEquals("UPI", result.paymentMode)
        assertEquals("txn", result.transactionType)
        // SMS_5 has no debit keyword but is parsed due to UPI context
        assertTrue("isUpi should be true", result.isUpi)
    }

    @Test
    fun `parse - 'sent to' format is correct`() {
        val sms = "Rs. 100.00 sent to SHRINANDAN via PhonePe. Ref No: 123456"
        val result = SmsParser.parse(sms)
        assertNotNull(result)
        assertEquals(100.0, result!!.amount, 0.001)
        assertEquals("SHRINANDAN", result.merchant)
        assertEquals("PhonePe", result.paymentMode)
        assertEquals("sent", result.transactionType)
    }

    @Test
    fun `parse - 'transferred to' format is correct`() {
        val sms = "INR 500.00 transferred to John Doe from A/c XX9876"
        val result = SmsParser.parse(sms)
        assertNotNull(result)
        assertEquals(500.0, result!!.amount, 0.001)
        assertEquals("John Doe", result.merchant)
        assertEquals("9876", result.accountLast4)
        assertEquals("transferred", result.transactionType)
    }

    @Test
    fun `parse - User HDFC test case`() {
        val sms = """
            Sent Rs.1.00
            From HDFC Bank A/C *7724
            To Shri Nandan
            On 10/04/26
            Ref 646603865440
            Not You?
            Call 18002586161/SMS BLOCK UPI to 7308080808
        """.trimIndent()
        val result = SmsParser.parse(sms)
        assertNotNull("Should not be null", result)
        assertEquals(1.0, result!!.amount, 0.001)
        assertEquals("Shri Nandan", result.merchant)
        assertEquals("7724", result.accountLast4)
    }

    @Test
    fun `parse - returns null for OTP SMS`() {
        val otpSms = "Your OTP is 482910. Valid for 5 minutes. Do not share."
        assertNull("OTP SMS must return null", SmsParser.parse(otpSms))
    }

    @Test
    fun `parse - returns null for promotional SMS`() {
        val promoSms = "Get 50% off on your next order! Use code SAVE50."
        assertNull("Promotional SMS must return null", SmsParser.parse(promoSms))
    }

    @Test
    fun `parse - amountInPaise is correct`() {
        val result = SmsParser.parse(SMS_1)!!
        assertEquals(45000L, result.amountInPaise)
    }

    @Test
    fun `parse - amountInPaise handles sub-rupee decimal correctly`() {
        val result = SmsParser.parse("₹99.50 paid to Swiggy via UPI")!!
        assertEquals(9950L, result.amountInPaise)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // SENDER FILTER TESTS
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `KNOWN_SENDERS - contains all required bank IDs`() {
        val required = listOf(
            "HDFCBK", "HDFCSMS", "SBISMS", "SBIUPI", "ICICIB", 
            "AXISBK", "KOTAKB", "PAYTMB", "PHONEP", "GOOGLE"
        )
        required.forEach { sender ->
            assertTrue("$sender must be in KNOWN_SENDERS", sender in SmsReceiver.KNOWN_SENDERS)
        }
    }

    @Test
    fun `KNOWN_SENDERS - does not contain random strings`() {
        assertTrue("RANDOMID must not be in KNOWN_SENDERS", "RANDOMID" !in SmsReceiver.KNOWN_SENDERS)
    }
}
