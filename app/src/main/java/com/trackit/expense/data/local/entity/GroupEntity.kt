package com.trackit.expense.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "groups",
    indices = [Index("created_by")]
)
data class GroupEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "emoji")
    val emoji: String?,

    @ColumnInfo(name = "created_by")
    val createdBy: String,

    // JSON-serialised List<GroupMember> — Room can't store nested lists natively
    @ColumnInfo(name = "members_json")
    val membersJson: String = "[]",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
