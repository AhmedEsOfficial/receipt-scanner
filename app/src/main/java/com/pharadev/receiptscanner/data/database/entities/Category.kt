package com.pharadev.receiptscanner.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val id: Long,
    val name: String,
    val color: String,
    val icon: String,
    val isDefault: Boolean = true
)