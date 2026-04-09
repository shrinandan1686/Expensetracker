package com.trackit.expense.domain.repository

/**
 * Interface for managing global application preferences and state.
 * Currently tracks onboarding status.
 */
interface PreferenceRepository {
    /** Returns true if the user has successfully finished the onboarding flow. */
    fun isOnboardingCompleted(): Boolean

    /** Sets the onboarding completion status. */
    fun setOnboardingCompleted(completed: Boolean)

    /** Returns true if a budget alert for [percentage] has been sent for [month]. */
    fun hasThresholdBeenTriggered(month: String, percentage: Int): Boolean

    /** Marks a budget alert [percentage] as sent for [month]. */
    fun markThresholdTriggered(month: String, percentage: Int)
}
