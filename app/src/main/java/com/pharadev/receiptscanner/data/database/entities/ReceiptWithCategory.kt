package com.pharadev.receiptscanner.data.database.entities

import androidx.room.Embedded
import androidx.room.Relation

data class ReceiptWithCategory(
    @Embedded val receipt: Receipt,
    @Relation(
        parentColumn = "categoryId",
        entityColumn = "id"
    )
    val category: Category
)