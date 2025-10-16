package com.respiroc.shopifyreaisync.dto.order

import java.time.OffsetDateTime

data class ReaiSyncStatus(
    val reaiOrderId: Long?,
    val reaiInvoiceId: Long?,
    val reaiInvoiceNumber: String?,
    val syncedAt: OffsetDateTime,
    val alreadySynced: Boolean
)

data class ReaiSyncResult(
    val orderDetails: ShopifyOrderDetails,
    val status: ReaiSyncStatus
)
