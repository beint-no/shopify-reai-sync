package com.respiroc.shopifyreaisync.service

import com.respiroc.shopifyreaisync.model.ReaiOrderSyncRecord
import com.respiroc.shopifyreaisync.repository.ReaiOrderSyncRecordRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class ReaiOrderSyncRecordService(
    private val reaiOrderSyncRecordRepository: ReaiOrderSyncRecordRepository
) {
    @Transactional(readOnly = true)
    fun findByShopifyOrderNumberAndTenantId(orderNumber: String, tenantId: Long): ReaiOrderSyncRecord? {
        return reaiOrderSyncRecordRepository.findByShopifyOrderNumberAndTenantId(orderNumber, tenantId)
    }

    @Transactional
    fun saveSyncResult(
        orderNumber: String,
        tenantId: Long,
        reaiOrderId: Long?,
        reaiInvoiceId: Long?,
        reaiInvoiceNumber: String?
    ): ReaiOrderSyncRecord {
        val existingRecord = reaiOrderSyncRecordRepository
            .findByShopifyOrderNumberAndTenantId(orderNumber, tenantId)
        val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
        return if (existingRecord == null) {
            val record = ReaiOrderSyncRecord(
                shopifyOrderNumber = orderNumber,
                tenantId = tenantId,
                reaiOrderId = reaiOrderId,
                reaiInvoiceId = reaiInvoiceId,
                reaiInvoiceNumber = reaiInvoiceNumber,
                syncedAt = timestamp
            )
            reaiOrderSyncRecordRepository.save(record)
        } else {
            existingRecord.reaiOrderId = reaiOrderId
            existingRecord.reaiInvoiceId = reaiInvoiceId
            existingRecord.reaiInvoiceNumber = reaiInvoiceNumber
            existingRecord.syncedAt = timestamp
            reaiOrderSyncRecordRepository.save(existingRecord)
        }
    }
}