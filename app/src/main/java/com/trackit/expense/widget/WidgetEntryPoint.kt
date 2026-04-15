package com.trackit.expense.widget

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt [EntryPoint] that lets the Glance widget — which is not a standard Hilt component
 * and cannot receive constructor injection — reach singletons from the application graph.
 *
 * Usage:
 * ```kotlin
 * val ep = EntryPointAccessors.fromApplication<WidgetEntryPoint>(context)
 * val data = ep.widgetDataRepository().getWidgetData()
 * ```
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetDataRepository(): WidgetDataRepository
}
