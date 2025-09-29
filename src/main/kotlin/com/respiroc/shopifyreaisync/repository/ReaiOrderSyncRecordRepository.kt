package com.respiroc.shopifyreaisync.repository

import com.respiroc.shopifyreaisync.model.ReaiOrderSyncRecord
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ReaiOrderSyncRecordRepository : JpaRepository<ReaiOrderSyncRecord, Int> {
    @Query("SELECT r FROM ReaiOrderSyncRecord r WHERE r.shopifyOrderNumber = :shopifyOrderNumber AND r.tenantId = :tenantId")
    fun findByShopifyOrderNumberAndTenantId(shopifyOrderNumber: String, tenantId: Long): ReaiOrderSyncRecord?
}