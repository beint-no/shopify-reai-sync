package com.respiroc.shopifyreaisync.dto.store

data class ShopifyDisconnectRequest(
    val tenantId: Long,
    val shopDomain: String
)
