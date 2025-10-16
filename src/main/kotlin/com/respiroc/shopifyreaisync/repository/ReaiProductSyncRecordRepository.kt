package com.respiroc.shopifyreaisync.repository

import com.respiroc.shopifyreaisync.model.ReaiProductSyncRecord
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ReaiProductSyncRecordRepository : JpaRepository<ReaiProductSyncRecord, Int> {
    @Query("SELECT r FROM ReaiProductSyncRecord r WHERE r.shopifyProductGid = :shopifyProductGid AND r.tenantId = :tenantId")
    fun findByShopifyProductGidAndTenantId(shopifyProductGid: String, tenantId: Long): ReaiProductSyncRecord?
}
