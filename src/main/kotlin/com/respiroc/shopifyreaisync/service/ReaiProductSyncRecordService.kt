package com.respiroc.shopifyreaisync.service

import com.respiroc.shopifyreaisync.model.ReaiProductSyncRecord
import com.respiroc.shopifyreaisync.repository.ReaiProductSyncRecordRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class ReaiProductSyncRecordService(
    private val reaiProductSyncRecordRepository: ReaiProductSyncRecordRepository
) {
    @Transactional(readOnly = true)
    fun findByShopifyProductGidAndTenantId(shopifyProductGid: String, tenantId: Long): ReaiProductSyncRecord? {
        return reaiProductSyncRecordRepository.findByShopifyProductGidAndTenantId(shopifyProductGid, tenantId)
    }

    @Transactional
    fun saveSyncResult(
        shopDomain: String,
        shopifyProductGid: String,
        tenantId: Long,
        reaiProductId: Long,
        productTitle: String?
    ): ReaiProductSyncRecord {
        val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
        val existingRecord = reaiProductSyncRecordRepository.findByShopifyProductGidAndTenantId(shopifyProductGid, tenantId)
        return if (existingRecord == null) {
            val record = ReaiProductSyncRecord(
                shopDomain = shopDomain,
                shopifyProductGid = shopifyProductGid,
                tenantId = tenantId,
                reaiProductId = reaiProductId,
                productTitle = productTitle,
                syncedAt = timestamp
            )
            reaiProductSyncRecordRepository.save(record)
        } else {
            existingRecord.reaiProductId = reaiProductId
            existingRecord.productTitle = productTitle
            existingRecord.syncedAt = timestamp
            reaiProductSyncRecordRepository.save(existingRecord)
        }
    }
}
