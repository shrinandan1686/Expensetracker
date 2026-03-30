package com.trackit.expense.overlay

/**
 * Predefined expense categories shown in the overlay popup dropdown.
 *
 * Ordered by everyday frequency. Each entry carries a display name and
 * a relevant emoji for quick visual identification (avoids loading vector
 * assets inside a WindowManager-hosted ComposeView).
 *
 * Maps loosely to [com.trackit.expense.domain.model.Category.defaults] but is
 * intentionally kept as a self-contained enum for the overlay layer so that
 * the popup has zero dependency on the DB at display time.
 */
enum class ExpenseCategory(
    val displayName: String,
    val emoji: String
) {
    FOOD("Food & Drinks",      "🍔"),
    TRANSPORT("Transport",     "🚗"),
    SHOPPING("Shopping",       "🛍️"),
    GROCERIES("Groceries",     "🛒"),
    BILLS("Bills & Utilities", "📄"),
    HEALTHCARE("Healthcare",   "🏥"),
    ENTERTAINMENT("Entertainment", "🎬"),
    EDUCATION("Education",     "📚"),
    TRAVEL("Travel",           "✈️"),
    INVESTMENTS("Investments", "📈"),
    OTHERS("Others",           "•••");

    /** Full label shown in the dropdown: emoji + space + displayName */
    val label: String get() = "$emoji $displayName"

    companion object {
        /** Returns the best-guess category for a merchant name or UPI VPA. */
        fun inferFrom(merchant: String?): ExpenseCategory {
            if (merchant == null) return OTHERS
            val m = merchant.lowercase()
            return when {
                m.containsAny("swiggy", "zomato", "dominos", "pizza", "food", "cafe", "restaurant", "hotel") -> FOOD
                m.containsAny("ola", "uber", "rapido", "redbus", "irctc", "metro", "bus", "auto") -> TRANSPORT
                m.containsAny("amazon", "flipkart", "myntra", "ajio", "nykaa", "shop", "store", "mart") -> SHOPPING
                m.containsAny("bigbasket", "dmart", "grofer", "more", "reliance", "grocery", "vegetable") -> GROCERIES
                m.containsAny("electricity", "water", "gas", "broadband", "airtel", "jio", "bsnl", "bill", "recharge") -> BILLS
                m.containsAny("pharma", "medical", "hospital", "clinic", "doctor", "health", "apollo", "med") -> HEALTHCARE
                m.containsAny("netflix", "prime", "spotify", "hotstar", "disney", "cinema", "pvr", "inox", "game") -> ENTERTAINMENT
                m.containsAny("udemy", "coursera", "byju", "school", "college", "fee", "book", "study") -> EDUCATION
                m.containsAny("flight", "airline", "indigo", "air", "hotel", "oyo", "makemytrip", "travel") -> TRAVEL
                m.containsAny("zerodha", "groww", "mutual", "sip", "invest", "nps", "insurance") -> INVESTMENTS
                else -> OTHERS
            }
        }

        private fun String.containsAny(vararg keywords: String): Boolean =
            keywords.any { this.contains(it) }
    }
}
