package com.pharadev.receiptscanner.data.repository.mappers

import com.pharadev.receiptscanner.data.database.entities.Category
import com.pharadev.receiptscanner.data.database.entities.Receipt
import com.pharadev.receiptscanner.data.database.entities.ReceiptWithCategory
import com.pharadev.receiptscanner.domain.model.CategoryDomain
import com.pharadev.receiptscanner.domain.model.ReceiptDomain

// Category mappers
fun Category.toDomain(): CategoryDomain {
    return CategoryDomain(
        id = id,
        name = name,
        color = color,
        icon = icon,
        isDefault = isDefault
    )
}

fun CategoryDomain.toEntity(): Category {
    return Category(
        id = id,
        name = name,
        color = color,
        icon = icon,
        isDefault = isDefault
    )
}

// Receipt mappers
fun ReceiptWithCategory.toDomain(): ReceiptDomain {
    return ReceiptDomain(
        id = receipt.id,
        imagePath = receipt.imagePath,
        vendor = receipt.vendor,
        amount = receipt.amount,
        date = receipt.date,
        category = category.toDomain(),
        notes = receipt.notes,
        createdAt = receipt.createdAt,
        modifiedAt = receipt.modifiedAt
    )
}

fun ReceiptDomain.toEntity(): Receipt {
    return Receipt(
        id = id,
        imagePath = imagePath,
        vendor = vendor,
        amount = amount,
        date = date,
        categoryId = category.id,
        notes = notes,
        createdAt = createdAt,
        modifiedAt = modifiedAt
    )
}