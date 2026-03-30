package com.trackit.expense.domain.model

/**
 * Domain model representing an expense category.
 *
 * Decoupled from [CategoryEntity] (Room) and any remote DTOs.
 *
 * @property id Local Room primary key. 0 means not yet persisted.
 * @property name Display name shown in the UI (e.g. "Food & Drinks").
 * @property iconName Material icon identifier for Compose rendering.
 * @property colorHex Hex color string for UI theming (e.g. "#FF5733").
 * @property isDefault System-provided category that cannot be deleted.
 * @property createdAt Unix epoch milliseconds of record creation.
 */
data class Category(
    val id: Long = 0,
    val name: String = "",
    val iconName: String = "category",
    val colorHex: String = "#6200EE",
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** Pre-defined default categories seeded on first launch. */
        val defaults: List<Category> = listOf(
            Category(name = "Food & Drinks",   iconName = "restaurant",    colorHex = "#FF6B6B", isDefault = true),
            Category(name = "Transport",        iconName = "directions_car", colorHex = "#4ECDC4", isDefault = true),
            Category(name = "Shopping",         iconName = "shopping_bag",  colorHex = "#45B7D1", isDefault = true),
            Category(name = "Bills & Utilities",iconName = "receipt_long",  colorHex = "#96CEB4", isDefault = true),
            Category(name = "Entertainment",    iconName = "movie",         colorHex = "#FFEAA7", isDefault = true),
            Category(name = "Health",           iconName = "health_and_safety", colorHex = "#DDA0DD", isDefault = true),
            Category(name = "Education",        iconName = "school",        colorHex = "#98D8C8", isDefault = true),
            Category(name = "Other",            iconName = "more_horiz",    colorHex = "#B0B0B0", isDefault = true),
        )
    }
}
