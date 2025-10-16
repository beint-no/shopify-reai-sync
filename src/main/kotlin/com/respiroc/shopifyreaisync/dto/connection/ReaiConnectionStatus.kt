package com.respiroc.shopifyreaisync.dto.connection

import java.time.OffsetDateTime

data class ReaiConnectionStatus(
    val connected: Boolean,
    val expiresAt: OffsetDateTime?,
    val tenantId: Long?,
    val autoSync: Boolean
)
