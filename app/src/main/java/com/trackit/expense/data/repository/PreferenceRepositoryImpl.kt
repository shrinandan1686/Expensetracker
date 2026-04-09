package com.trackit.expense.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.trackit.expense.domain.repository.PreferenceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * SharedPreferences-based implementation of [PreferenceRepository].
 */
class PreferenceRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PreferenceRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "trackit_prefs",
        Context.MODE_PRIVATE
    )

    override fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    override fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    override fun hasThresholdBeenTriggered(month: String, percentage: Int): Boolean {
        val triggered = prefs.getString(KEY_THRESHOLDS_PREFIX + month, "") ?: ""
        return triggered.split("-").contains(percentage.toString())
    }

    override fun markThresholdTriggered(month: String, percentage: Int) {
        val key = KEY_THRESHOLDS_PREFIX + month
        val triggered = prefs.getString(key, "") ?: ""
        val newList = if (triggered.isEmpty()) {
            percentage.toString()
        } else {
            triggered + "-" + percentage.toString()
        }
        prefs.edit().putString(key, newList).apply()
    }

    companion object {
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_THRESHOLDS_PREFIX = "budget_thresholds_"
    }
}
