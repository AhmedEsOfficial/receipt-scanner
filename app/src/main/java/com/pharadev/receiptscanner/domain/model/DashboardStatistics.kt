package com.pharadev.receiptscanner.domain.model

import java.math.BigDecimal

data class DashboardStatistics(
    val totalAmount: BigDecimal,
    val receiptCount: Int,
    val topCategory: CategoryDomain?,
    val recentReceipts: List<ReceiptDomain>
)