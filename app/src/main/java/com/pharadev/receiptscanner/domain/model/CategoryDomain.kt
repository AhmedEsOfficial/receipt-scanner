package com.pharadev.receiptscanner.domain.model

data class CategoryDomain(
    val id: Long,
    val name: String,
    val color: String,
    val icon: String,
    val isDefault: Boolean = true
)