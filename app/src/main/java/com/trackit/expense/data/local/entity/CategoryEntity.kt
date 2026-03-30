package com.trackit.expense.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing an expense category in the local database.
 *
 * Maps to the "categories" table.
 *
 * Fields to be populated:
 * - [id]: Auto-generated primary key
 * - [name]: Display name of the category (e.g. "Food", "Transport")
 * - [iconName]: Material icon name string for Compose rendering
 * - [colorHex]: Hex color string (e.g. "#FF5733") for UI theming
 * - [isDefault]: Whether this is a system default category
 * - [createdAt]: Record creation timestamp
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String = "",

    @ColumnInfo(name = "icon_name")
    val iconName: String = "category",

    @ColumnInfo(name = "color_hex")
    val colorHex: String = "#6200EE",

    @ColumnInfo(name = "is_default")
    val isDefault: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
