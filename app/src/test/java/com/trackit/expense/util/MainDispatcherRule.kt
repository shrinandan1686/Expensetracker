package com.trackit.expense.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit4 [TestWatcher] that replaces [Dispatchers.Main] with a [TestDispatcher]
 * for the duration of each test, then resets it afterwards.
 *
 * Usage:
 * ```kotlin
 * @get:Rule val mainDispatcherRule = MainDispatcherRule()
 * ```
 *
 * Using [UnconfinedTestDispatcher] (default) means coroutines started in `viewModelScope`
 * are executed eagerly — no need to call `advanceUntilIdle()` for simple sequential tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
