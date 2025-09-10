package com.pharadev.receiptscanner.domain.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class ReceiptDomain(
    val id: Long = 0,
    val imagePath: String,
    val vendor: String,
    val amount: BigDecimal,
    val date: LocalDate,
    val category: CategoryDomain,
    val notes: String? = null,
    val createdAt: LocalDateTime,
    val modifiedAt: LocalDateTime
)