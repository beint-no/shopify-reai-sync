package com.respiroc.shopifyreaisync.dto.product

data class ProductSearchRequest(
    val shopDomain: String,
    val productName: String,
    val tenantId: Long
)

data class ProductSyncRequest(
    val shopDomain: String,
    val productGid: String,
    val tenantId: Long
)
