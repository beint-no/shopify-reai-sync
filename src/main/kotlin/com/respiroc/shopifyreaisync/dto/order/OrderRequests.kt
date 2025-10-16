package com.respiroc.shopifyreaisync.dto.order

data class OrderSearchRequest(
    val shopDomain: String,
    val orderNumber: String,
    val tenantId: Long
)

data class OrderSyncRequest(
    val shopDomain: String,
    val orderNumber: String,
    val tenantId: Long
)
