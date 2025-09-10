package com.pharadev.receiptscanner.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(
    tableName = "receipts",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["date"]),
        Index(value = ["vendor"]),
        Index(value = ["createdAt"])
    ]
)
data class Receipt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imagePath: String,
    val vendor: String,
    val amount: BigDecimal,
    val date: LocalDate,
    val categoryId: Long,
    val notes: String? = null,
    val createdAt: LocalDateTime,
    val modifiedAt: LocalDateTime
)